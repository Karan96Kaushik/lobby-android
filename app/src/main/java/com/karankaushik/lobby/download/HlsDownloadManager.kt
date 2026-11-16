package com.karankaushik.lobby.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.karankaushik.lobby.MainActivity
import com.karankaushik.lobby.data.DownloadStore
import com.karankaushik.lobby.model.DownloadFailureKind
import com.karankaushik.lobby.model.DownloadItem
import com.karankaushik.lobby.model.DownloadQuality
import com.karankaushik.lobby.model.DownloadState
import com.karankaushik.lobby.model.HlsCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.webkit.CookieManager

@OptIn(UnstableApi::class)
class HlsDownloadManager(private val context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("lobby", Context.MODE_PRIVATE)
    private val store = DownloadStore(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val downloaders = ConcurrentHashMap<String, HlsDownloader>()
    private val paused = ConcurrentHashMap.newKeySet<String>()
    private val removed = ConcurrentHashMap.newKeySet<String>()
    private val _banners = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val banners = _banners.asSharedFlow()
    private var cache: SimpleCache? = null
    private var service: DownloadService? = null

    var items by mutableStateOf(store.load())
        private set
    var preferredQuality by mutableStateOf(
        DownloadQuality.fromStorage(prefs.getString(QUALITY_KEY, null))
    )
        private set

    val totalStorageBytes: Long
        get() = items.filter { it.state == DownloadState.COMPLETED }.sumOf { it.bytes.coerceAtLeast(0) }

    val activeDownloadCount: Int
        get() = items.count { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED }

    init {
        createChannel()
        scope.launch { verifyFilesOnDisk() }
    }

    fun setQuality(quality: DownloadQuality) {
        preferredQuality = quality
        prefs.edit().putString(QUALITY_KEY, quality.storageKey).apply()
    }

    fun attach(service: DownloadService) {
        this.service = service
        service.showForeground(foregroundNotification())
    }

    fun detach(service: DownloadService) {
        if (this.service === service) this.service = null
    }

    fun startDownload(
        candidate: HlsCandidate?,
        fallbackSourceId: String,
        fallbackPageUrl: String?,
        fallbackTitle: String,
        userAgent: String,
        allowsDownload: Boolean
    ) {
        if (!allowsDownload) return
        if (candidate == null) {
            insertFailed(
                title = fallbackTitle.ifBlank { "Unavailable Stream" },
                sourceId = fallbackSourceId,
                pageUrl = fallbackPageUrl,
                message = "No downloadable HLS stream was found on this page. Start playback first and try again.",
                kind = DownloadFailureKind.NO_CANDIDATE
            )
            postBanner("No stream found on this page")
            return
        }
        val existing = items.firstOrNull {
            it.originalUrl == candidate.streamUrl &&
                (it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED || it.state == DownloadState.PAUSED)
        }
        if (existing != null) {
            postBanner("Already downloading “${existing.title}”")
            return
        }
        val title = candidate.pageTitle.trim().ifBlank { fallbackTitle.ifBlank { "Saved Video" } }
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            title = title,
            sourceId = candidate.sourceId,
            originalUrl = candidate.streamUrl,
            pageUrl = candidate.pageUrl ?: fallbackPageUrl,
            mediaUrl = null,
            pageOrigin = candidate.pageOrigin,
            userAgent = userAgent,
            state = DownloadState.QUEUED,
            progress = 0.0,
            bytes = 0,
            createdAt = System.currentTimeMillis(),
            errorMessage = null,
            failureKind = null
        )
        items = listOf(item) + items
        persist()
        postBanner("Preparing download…")
        launch(item)
    }

    fun retry(item: DownloadItem) {
        if (item.originalUrl.isBlank()) {
            update(item.id) {
                it.copy(
                    state = DownloadState.FAILED,
                    failureKind = DownloadFailureKind.NO_CANDIDATE,
                    errorMessage = "No stream URL is stored for this item. Open the page to capture it again."
                )
            }
            return
        }
        paused.remove(item.id)
        removed.remove(item.id)
        downloaders.remove(item.id)?.cancel()
        jobs.remove(item.id)?.cancel()
        val refreshed = item.copy(
            state = DownloadState.QUEUED,
            progress = 0.0,
            bytes = 0,
            mediaUrl = null,
            errorMessage = null,
            failureKind = null
        )
        update(item.id) { refreshed }
        launch(refreshed)
    }

    fun pause(item: DownloadItem) {
        paused.add(item.id)
        downloaders[item.id]?.cancel()
        jobs[item.id]?.cancel()
        update(item.id) { it.copy(state = DownloadState.PAUSED) }
    }

    fun resume(item: DownloadItem) {
        paused.remove(item.id)
        val current = items.firstOrNull { it.id == item.id } ?: return
        launch(current.copy(state = DownloadState.QUEUED, errorMessage = null, failureKind = null))
    }

    fun cancelAndDelete(item: DownloadItem) {
        removed.add(item.id)
        paused.remove(item.id)
        downloaders.remove(item.id)?.cancel()
        jobs.remove(item.id)?.cancel()
        items = items.filter { it.id != item.id }
        persist()
        scope.launch(Dispatchers.IO) { removeCached(item.id) }
        refreshService()
    }

    fun hasMedia(id: String): Boolean = runCatching {
        cache().keys.any { it.startsWith("$id|") }
    }.getOrDefault(false)

    suspend fun verifyFilesOnDisk() {
        val snapshot = items
        val reconciled = withContext(Dispatchers.IO) {
            snapshot.map { item ->
                if (jobs[item.id]?.isActive == true) return@map item
                when (item.state) {
                    DownloadState.DOWNLOADING, DownloadState.QUEUED -> item.copy(
                        state = DownloadState.FAILED,
                        failureKind = DownloadFailureKind.NETWORK_LOST,
                        errorMessage = "Download was interrupted while the app was closed."
                    )
                    DownloadState.COMPLETED -> if (hasMedia(item.id)) item else item.copy(
                        state = DownloadState.NEEDS_REDOWNLOAD,
                        errorMessage = "Offline file is missing. Re-download to play offline."
                    )
                    DownloadState.NEEDS_REDOWNLOAD -> if (hasMedia(item.id)) {
                        item.copy(state = DownloadState.COMPLETED, errorMessage = null)
                    } else {
                        item
                    }
                    else -> item
                }
            }
        }
        if (reconciled != items) {
            items = reconciled
            persist()
        }
    }

    fun createPlayer(context: Context, item: DownloadItem): ExoPlayer {
        val mediaUrl = item.mediaUrl ?: item.originalUrl
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory(item)))
            .build()
        player.setMediaItem(MediaItem.fromUri(mediaUrl))
        player.prepare()
        player.playWhenReady = true
        return player
    }

    fun foregroundNotification(): Notification {
        val active = items.firstOrNull { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED }
        val title = active?.title ?: "Lobby"
        val percent = ((active?.progress ?: 0.0) * 100).toInt().coerceIn(0, 100)
        val text = if (active == null) "Saving video" else "Downloading $percent%"
        val launch = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(com.karankaushik.lobby.R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(launch)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent, active != null && active.progress <= 0.0)
            .build()
    }

    private fun launch(item: DownloadItem) {
        if (jobs[item.id]?.isActive == true) return
        update(item.id) { it.copy(state = DownloadState.QUEUED, errorMessage = null, failureKind = null) }
        val job = scope.launch {
            jobs[item.id] = coroutineContext[kotlinx.coroutines.Job] ?: return@launch
            try {
                val ready = withContext(Dispatchers.IO) { prepare(item) }
                if (removed.contains(item.id) || paused.contains(item.id)) return@launch
                when (ready) {
                    is Prepare.Failed -> {
                        fail(item.id, ready.kind, ready.message)
                        postBanner(ready.message)
                        return@launch
                    }
                    is Prepare.Ready -> update(item.id) {
                        it.copy(state = DownloadState.DOWNLOADING, mediaUrl = ready.mediaUrl, errorMessage = null, failureKind = null)
                    }
                }
                val current = items.firstOrNull { it.id == item.id } ?: return@launch
                val downloader = HlsDownloader(MediaItem.fromUri(current.mediaUrl ?: return@launch), dataSourceFactory(current))
                downloaders[item.id] = downloader
                postBanner("Downloading “${current.title}”")
                refreshService()
                withContext(Dispatchers.IO) {
                    downloader.download { _, bytesDownloaded, percentDownloaded ->
                        val fraction = if (percentDownloaded >= 0f) {
                            (percentDownloaded / 100.0).coerceIn(0.0, 1.0)
                        } else {
                            0.0
                        }
                        scope.launch { onProgress(item.id, bytesDownloaded, fraction) }
                    }
                }
                if (removed.contains(item.id) || paused.contains(item.id)) return@launch
                update(item.id) {
                    val size = it.bytes.coerceAtLeast(0)
                    it.copy(state = DownloadState.COMPLETED, progress = 1.0, bytes = size, errorMessage = null, failureKind = null)
                }
                postBanner("Saved “${current.title}”")
            } catch (_: CancellationException) {
                // Deleted or the coroutine was cancelled.
            } catch (_: InterruptedException) {
                // Pause cancels the downloader.
            } catch (error: Exception) {
                if (removed.contains(item.id) || paused.contains(item.id)) return@launch
                val mapped = classify(error)
                fail(item.id, mapped.first, mapped.second)
                postBanner(mapped.second)
            } finally {
                downloaders.remove(item.id)
                val current = coroutineContext[kotlinx.coroutines.Job]
                if (current != null && jobs[item.id] == current) jobs.remove(item.id)
                refreshService()
            }
        }
        jobs[item.id] = job
    }

    private fun onProgress(id: String, bytes: Long, fraction: Double) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = items[index]
        if (current.state == DownloadState.PAUSED || current.state == DownloadState.COMPLETED) return
        val nextBytes = maxOf(current.bytes, bytes)
        val changed = kotlin.math.abs(fraction - current.progress) >= 0.02 || fraction >= 0.99 || nextBytes != current.bytes
        items = items.toMutableList().also {
            it[index] = current.copy(state = DownloadState.DOWNLOADING, progress = fraction, bytes = nextBytes)
        }
        if (changed) {
            persist()
            refreshService()
        }
    }

    private fun prepare(item: DownloadItem): Prepare {
        val free = availableBytes()
        if (free != null && free < 60L * 1024 * 1024) {
            return Prepare.Failed(
                DownloadFailureKind.NO_DISK_SPACE,
                "Not enough free storage on this device to save this video."
            )
        }
        return validate(item, item.originalUrl, 0)
    }

    private fun validate(item: DownloadItem, url: String, depth: Int): Prepare {
        val headers = headersFor(item, url)
        val fetched = try {
            fetch(url, headers)
        } catch (error: Exception) {
            val mapped = classify(error)
            return Prepare.Failed(mapped.first, mapped.second)
        }
        when (fetched.code) {
            in 200..299 -> Unit
            403 -> return Prepare.Failed(
                DownloadFailureKind.FORBIDDEN_403,
                "Access denied (HTTP 403). The stream server rejected the session headers or cookies."
            )
            401, 410 -> return Prepare.Failed(
                DownloadFailureKind.EXPIRED_URL,
                "This signed stream link has expired (HTTP ${fetched.code}). Retry from the video page."
            )
            404 -> return Prepare.Failed(
                DownloadFailureKind.EXPIRED_URL,
                "Stream playlist was not found (HTTP 404). Reload the page and retry."
            )
            else -> return Prepare.Failed(DownloadFailureKind.OTHER, "Stream server returned HTTP ${fetched.code}.")
        }
        when (val encryption = PlaylistInspector.encryptionStatus(fetched.body, fetched.url)) {
            is EncryptionStatus.Rejected -> return Prepare.Failed(DownloadFailureKind.DRM_PROTECTED, encryption.message)
            is EncryptionStatus.Aes128 -> {
                val keyFailure = verifyKeys(encryption.keyUrls, headers)
                if (keyFailure != null) return keyFailure
            }
            EncryptionStatus.Clear -> Unit
        }
        return when (val scan = PlaylistInspector.scan(fetched.body, fetched.url, preferredQuality.maxHeight)) {
            is PlaylistScan.Reject -> Prepare.Failed(scan.kind, scan.message)
            is PlaylistScan.Media -> Prepare.Ready(scan.url)
            is PlaylistScan.Master -> {
                if (depth >= 2) {
                    Prepare.Failed(DownloadFailureKind.OTHER, "Nested master playlists exceeded maximum depth.")
                } else {
                    validate(item, scan.variantUrl, depth + 1)
                }
            }
        }
    }

    private fun verifyKeys(urls: List<String>, headers: Map<String, String>): Prepare.Failed? {
        for (keyUrl in urls) {
            try {
                val connection = open(keyUrl, headers)
                val code = connection.responseCode
                val length = if (code in 200..299) readLimited(connection.inputStream, 64).size else 0
                connection.disconnect()
                if (code !in 200..299 || length == 0) {
                    return Prepare.Failed(
                        DownloadFailureKind.DRM_PROTECTED,
                        "Stream encryption key request failed (HTTP $code). This stream cannot be downloaded."
                    )
                }
            } catch (error: Exception) {
                return Prepare.Failed(
                    DownloadFailureKind.DRM_PROTECTED,
                    "Stream encryption key could not be fetched (${error.message ?: "network error"})."
                )
            }
        }
        return null
    }

    private fun fetch(url: String, headers: Map<String, String>): RemotePlaylist {
        val connection = open(url, headers)
        val code = connection.responseCode
        val finalUrl = connection.url?.toString() ?: url
        if (code !in 200..299) {
            connection.disconnect()
            return RemotePlaylist(finalUrl, "", code)
        }
        val bytes = readLimited(connection.inputStream, 2 * 1024 * 1024)
        connection.disconnect()
        val body = bytes.toString(Charsets.UTF_8).let { text ->
            if (text.contains("#EXTM3U")) text else bytes.toString(Charsets.ISO_8859_1)
        }
        return RemotePlaylist(finalUrl, body, code)
    }

    private fun open(url: String, headers: Map<String, String>): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
        }
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        return connection
    }

    private fun headersFor(item: DownloadItem, url: String): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        val agent = item.userAgent.ifBlank { "Lobby" }
        headers["User-Agent"] = agent
        item.pageOrigin?.let { headers["Referer"] = it }
        val cookie = cookieHeader(listOfNotNull(item.pageUrl, item.originalUrl, url))
        if (cookie.isNotBlank()) headers["Cookie"] = cookie
        return headers
    }

    private fun cookieHeader(urls: List<String>): String {
        val pairs = linkedMapOf<String, String>()
        val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return ""
        for (url in urls.distinct()) {
            val raw = runCatching { manager.getCookie(url) }.getOrNull() ?: continue
            raw.split(';').forEach { part ->
                val trimmed = part.trim()
                val eq = trimmed.indexOf('=')
                if (eq > 0) pairs[trimmed.substring(0, eq)] = trimmed
            }
        }
        return pairs.values.joinToString("; ")
    }

    private fun dataSourceFactory(item: DownloadItem): CacheDataSource.Factory {
        val upstream = DefaultHttpDataSource.Factory()
            .setUserAgent(item.userAgent.ifBlank { "Lobby" })
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(headersFor(item, item.mediaUrl ?: item.originalUrl))
        return CacheDataSource.Factory()
            .setCache(cache())
            .setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory(cacheKeyFactory(item.id))
    }

    private fun cacheKeyFactory(itemId: String): CacheKeyFactory = CacheKeyFactory { spec ->
        val identity = spec.key ?: spec.uri.toString()
        "$itemId|$identity"
    }

    @Synchronized
    private fun cache(): SimpleCache {
        cache?.let { return it }
        val directory = File(app.filesDir, "hls-cache").apply { mkdirs() }
        return SimpleCache(directory, NoOpCacheEvictor(), StandaloneDatabaseProvider(app)).also { cache = it }
    }

    private fun removeCached(id: String) {
        val prefix = "$id|"
        val keys = runCatching { cache().keys.filter { it.startsWith(prefix) } }.getOrDefault(emptyList())
        keys.forEach { key -> runCatching { cache().removeResource(key) } }
    }

    private fun availableBytes(): Long? = runCatching {
        val stats = StatFs(app.filesDir.absolutePath)
        stats.availableBytes
    }.getOrNull()

    private fun insertFailed(
        title: String,
        sourceId: String,
        pageUrl: String?,
        message: String,
        kind: DownloadFailureKind
    ) {
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            title = title,
            sourceId = sourceId,
            originalUrl = "",
            pageUrl = pageUrl,
            mediaUrl = null,
            pageOrigin = null,
            userAgent = "",
            state = DownloadState.FAILED,
            progress = 0.0,
            bytes = 0,
            createdAt = System.currentTimeMillis(),
            errorMessage = message,
            failureKind = kind
        )
        items = listOf(item) + items
        persist()
    }

    private fun fail(id: String, kind: DownloadFailureKind, message: String) {
        update(id) { it.copy(state = DownloadState.FAILED, failureKind = kind, errorMessage = message) }
    }

    private fun update(id: String, block: (DownloadItem) -> DownloadItem) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        items = items.toMutableList().also { it[index] = block(it[index]) }
        persist()
    }

    private fun persist() {
        store.save(items)
    }

    private fun postBanner(text: String) {
        _banners.tryEmit(text)
    }

    private fun refreshService() {
        val active = items.any { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED }
        if (!active) {
            app.stopService(Intent(app, DownloadService::class.java))
            return
        }
        val running = service
        if (running != null) {
            running.showForeground(foregroundNotification())
        } else {
            runCatching {
                ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
            }
        }
    }

    private fun classify(error: Exception): Pair<DownloadFailureKind, String> {
        val text = error.message?.lowercase().orEmpty()
        if (text.contains("no space") || text.contains("enospc") || text.contains("disk full")) {
            return DownloadFailureKind.NO_DISK_SPACE to "Download stopped because the device ran out of storage space."
        }
        if (text.contains("403") || text.contains("forbidden")) {
            return DownloadFailureKind.FORBIDDEN_403 to "Access denied (HTTP 403). The server rejected the session headers or cookies."
        }
        if (text.contains("401") || text.contains("410") || text.contains("expired")) {
            return DownloadFailureKind.EXPIRED_URL to "Signed stream URL expired. Open the page again and retry."
        }
        if (text.contains("drm") || text.contains("widevine") || text.contains("fairplay")) {
            return DownloadFailureKind.DRM_PROTECTED to "This stream uses DRM protection or an inaccessible encryption key and cannot be downloaded."
        }
        if (error is java.net.UnknownHostException ||
            error is java.net.SocketTimeoutException ||
            error is java.net.ConnectException ||
            error is java.io.IOException && (text.contains("network") || text.contains("host") || text.contains("timed out"))
        ) {
            return DownloadFailureKind.NETWORK_LOST to "Network connection was lost during the download. Check your connection and retry."
        }
        return DownloadFailureKind.OTHER to (error.message?.ifBlank { null } ?: "Download failed.")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = app.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Offline video downloads"
        manager.createNotificationChannel(channel)
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        input.use { stream ->
            while (total < limit) {
                val read = stream.read(buffer, 0, minOf(buffer.size, limit - total))
                if (read < 0) break
                output.write(buffer, 0, read)
                total += read
            }
        }
        return output.toByteArray()
    }

    private data class RemotePlaylist(val url: String, val body: String, val code: Int)

    private sealed interface Prepare {
        data class Ready(val mediaUrl: String) : Prepare
        data class Failed(val kind: DownloadFailureKind, val message: String) : Prepare
    }

    companion object {
        private const val QUALITY_KEY = "downloads.preferredQuality.v1"
        const val CHANNEL_ID = "lobby.downloads"
        const val NOTIFICATION_ID = 7101
    }
}

class DownloadService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? com.karankaushik.lobby.LobbyApp
        app?.downloads?.attach(this)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        (application as? com.karankaushik.lobby.LobbyApp)?.downloads?.detach(this)
        super.onDestroy()
    }

    fun showForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(HlsDownloadManager.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(HlsDownloadManager.NOTIFICATION_ID, notification)
        }
    }
}
