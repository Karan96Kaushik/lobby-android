package com.karankaushik.lobby

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.karankaushik.lobby.data.SessionStore
import com.karankaushik.lobby.data.SourceLibrary
import com.karankaushik.lobby.download.HlsDownloadManager
import com.karankaushik.lobby.model.HlsCandidate
import com.karankaushik.lobby.model.MediaSource
import com.karankaushik.lobby.model.PlaylistRole
import com.karankaushik.lobby.policy.SitePolicy
import com.karankaushik.lobby.remote.RemoteUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

sealed interface BrowserCommand {
    data object Back : BrowserCommand
    data object Forward : BrowserCommand
    data object Stop : BrowserCommand
    data object Reload : BrowserCommand
    data class Load(val url: String) : BrowserCommand
    data object ClearData : BrowserCommand
}

class BrowserController(app: Application) : AndroidViewModel(app) {
    private val library = SourceLibrary(app.getSharedPreferences("lobby", Application.MODE_PRIVATE))
    private val sessions = SessionStore(app)
    private val main = Handler(Looper.getMainLooper())
    private val _commands = MutableSharedFlow<BrowserCommand>(extraBufferCapacity = 32)
    val commands = _commands.asSharedFlow()

    var sources by mutableStateOf(initialSources())
        private set
    var selectedSource by mutableStateOf(initialSelected())
        private set
    var displayedSourceId by mutableStateOf(selectedSource.id)
        private set
    var pageTitle by mutableStateOf(sessions.savedTitle(selectedSource.id) ?: selectedSource.name)
        private set
    var pageUrl by mutableStateOf<String?>(null)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0.0)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var popupBlockCount by mutableStateOf(0)
        private set
    var downloadBlockCount by mutableStateOf(0)
        private set
    var hlsCandidate by mutableStateOf<HlsCandidate?>(null)
        private set
    var userAgent: String = ""

    private var remoteHosts by mutableStateOf<List<String>>(emptyList())
    private var loaded = false
    private var noticeGeneration = 0
    private var refreshState: (() -> Unit)? = null

    private fun initialSources(): List<MediaSource> = library.load().first

    private fun initialSelected(): MediaSource = library.load().second

    fun bindRefresh(listener: () -> Unit) {
        refreshState = listener
    }

    fun initialPageUrl(): String {
        val saved = sessions.read(selectedSource.id, selectedSource.allowedHosts)
        displayedSourceId = selectedSource.id
        if (saved != null) {
            if (saved.title.isNotBlank()) pageTitle = saved.title
            pageUrl = saved.url
            publish()
            return saved.url
        }
        pageTitle = selectedSource.name
        publish()
        return selectedSource.home
    }

    fun goBack() {
        hlsCandidate = null
        publish()
        emit(BrowserCommand.Back)
    }

    fun goForward() {
        hlsCandidate = null
        publish()
        emit(BrowserCommand.Forward)
    }

    fun stop() = emit(BrowserCommand.Stop)

    fun reload() {
        hlsCandidate = null
        errorMessage = null
        publish()
        emit(BrowserCommand.Reload)
    }

    fun goHome() {
        hlsCandidate = null
        errorMessage = null
        publish()
        emit(BrowserCommand.Load(selectedSource.home))
    }

    fun navigationHosts(): List<String> {
        if (remoteHosts.isEmpty()) return selectedSource.allowedHosts
        return selectedSource.allowedHosts + remoteHosts
    }

    fun openRemoteUrl(raw: String): String? {
        val url = RemoteUrl.normalize(raw) ?: return "Enter a web address."
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return "Enter a web address."
        val match = sources.firstOrNull { SitePolicy.isFirstPartyHost(host, it.allowedHosts) }
        hlsCandidate = null
        errorMessage = null
        if (match != null) {
            remoteHosts = emptyList()
            if (match.id != selectedSource.id) {
                persistSession()
                selectedSource = match
                displayedSourceId = match.id
                library.saveSelectedId(match.id)
                pageTitle = sessions.savedTitle(match.id) ?: match.name
            }
        } else {
            remoteHosts = listOf(SourceLibrary.canonicalHost(host))
        }
        pageUrl = url
        publish()
        emit(BrowserCommand.Load(url))
        return null
    }

    fun open(source: MediaSource) {
        val hadRemote = remoteHosts.isNotEmpty()
        remoteHosts = emptyList()
        if (source.id == selectedSource.id && loaded && !hadRemote) return
        persistSession()
        hlsCandidate = null
        selectedSource = source
        displayedSourceId = source.id
        library.saveSelectedId(source.id)
        pageTitle = sessions.savedTitle(source.id) ?: source.name
        errorMessage = null
        val saved = sessions.read(source.id, source.allowedHosts)
        pageUrl = saved?.url
        publish()
        emit(BrowserCommand.Load(saved?.url ?: source.home))
    }

    fun addSource(name: String, address: String, allowsDownload: Boolean = false): String? {
        val parsed = library.parseCustom(name, address, allowsDownload, sources)
        val source = parsed.first ?: return parsed.second
        sources = sources + source
        library.saveCustom(sources)
        loaded = true
        open(source.copy())
        publish()
        return null
    }

    fun setAllowsDownload(enabled: Boolean, source: MediaSource) {
        val updated = sources.map {
            if (it.id == source.id) it.copy(allowsDownload = enabled) else it
        }
        sources = updated
        if (source.isBuiltIn) {
            library.saveDownloadOverride(source.id, enabled)
        } else {
            library.saveCustom(updated)
        }
        if (selectedSource.id == source.id) {
            selectedSource = updated.first { it.id == source.id }
            hlsCandidate = null
            if (enabled) reload()
        }
        publish()
    }

    fun remove(source: MediaSource) {
        if (source.isBuiltIn) return
        sources = sources.filter { it.id != source.id }
        library.saveCustom(sources)
        sessions.remove(source.id)
        if (selectedSource.id == source.id) {
            loaded = false
            sources.firstOrNull()?.let { open(it) }
        }
        publish()
    }

    fun downloadCurrent(downloads: HlsDownloadManager) {
        if (!selectedSource.allowsDownload) return
        var candidate = hlsCandidate
        if (candidate != null) {
            val trimmed = pageTitle.trim()
            if (trimmed.isNotEmpty()) candidate = candidate.copy(pageTitle = trimmed)
            if (candidate.pageUrl == null) candidate = candidate.copy(pageUrl = pageUrl)
        }
        downloads.startDownload(
            candidate = candidate,
            fallbackSourceId = selectedSource.id,
            fallbackPageUrl = pageUrl,
            fallbackTitle = pageTitle,
            userAgent = userAgent,
            allowsDownload = true
        )
    }

    fun reopenPageForRetry(itemSourceId: String, page: String?) {
        val source = sources.firstOrNull { it.id == itemSourceId }
        if (source != null && source.id != selectedSource.id) {
            persistSession()
            selectedSource = source
            displayedSourceId = source.id
            library.saveSelectedId(source.id)
            pageTitle = sessions.savedTitle(source.id) ?: source.name
        }
        hlsCandidate = null
        errorMessage = null
        val hosts = selectedSource.allowedHosts
        if (page != null && SitePolicy.isFirstPartyPage(page, hosts)) {
            emit(BrowserCommand.Load(page))
            showNotice("Play the video to capture a fresh stream link")
        } else {
            emit(BrowserCommand.Load(selectedSource.home))
        }
        publish()
    }

    fun clearData() {
        hlsCandidate = null
        sessions.removeAll()
        pageTitle = selectedSource.name
        pageUrl = null
        errorMessage = null
        emit(BrowserCommand.ClearData)
        showNotice("Browsing data cleared")
        publish()
    }

    fun allowedHostsJson(): String = JSONArray(selectedSource.allowedHosts).toString()

    fun downloadsEnabled(): Boolean = selectedSource.allowsDownload

    fun onPopupBlocked(announce: Boolean) {
        main.post {
            popupBlockCount += 1
            if (announce) showNotice("Popup blocked")
            publish()
        }
    }

    fun onDownloadBlocked() {
        main.post {
            downloadBlockCount += 1
            showNotice("Download blocked")
            publish()
        }
    }

    fun onHlsMessage(payload: String) {
        if (!selectedSource.allowsDownload) return
        main.post { handleHls(payload) }
    }

    fun onPageStarted(url: String?) {
        loaded = true
        isLoading = true
        errorMessage = null
        hlsCandidate = null
        if (url != null) rememberLocation(url)
        publish()
    }

    fun onPageFinished(url: String?, title: String?) {
        isLoading = false
        progress = 1.0
        errorMessage = null
        if (!title.isNullOrBlank()) updateTitle(title, url)
        if (url != null) rememberLocation(url)
        publish()
    }

    fun onProgress(value: Int) {
        progress = value / 100.0
        isLoading = value in 1..99
        publish()
    }

    fun onHistory(back: Boolean, forward: Boolean, url: String?) {
        canGoBack = back
        canGoForward = forward
        if (url != null) rememberLocation(url)
        publish()
    }

    fun onTitle(title: String?, url: String?) {
        if (!title.isNullOrBlank()) updateTitle(title, url)
        publish()
    }

    fun onError(message: String) {
        isLoading = false
        if (pageUrl == null) errorMessage = message
        publish()
    }

    fun persistSession() {
        val url = pageUrl ?: return
        val source = sources.firstOrNull { it.id == displayedSourceId } ?: return
        sessions.save(source.id, url, pageTitle, source.allowedHosts)
    }

    fun showNotice(text: String) {
        noticeGeneration += 1
        val generation = noticeGeneration
        notice = text
        publish()
        viewModelScope.launch {
            delay(2200)
            if (noticeGeneration == generation) {
                notice = null
                publish()
            }
        }
    }

    private fun handleHls(payload: String) {
        if (!selectedSource.allowsDownload) return
        val body = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val rawUrl = body.optString("url")
        if (rawUrl.isBlank()) return
        val streamUrl = rawUrl
        val scheme = runCatching { java.net.URI(streamUrl).scheme?.lowercase() }.getOrNull()
        if (scheme != "https" && scheme != "http") return
        val reported = when (body.optString("kind")) {
            "master" -> PlaylistRole.MASTER
            "variant" -> PlaylistRole.VARIANT
            else -> PlaylistRole.UNKNOWN
        }
        val role = if (reported != PlaylistRole.UNKNOWN) reported else PlaylistRole.classify(streamUrl)
        val existing = hlsCandidate
        if (existing != null && existing.role == PlaylistRole.MASTER && role == PlaylistRole.VARIANT) return
        val messageTitle = body.optString("title").trim()
        val resolvedTitle = pageTitle.trim().ifEmpty {
            if (body.optBoolean("isMainFrame") && messageTitle.isNotEmpty()) messageTitle else selectedSource.name
        }
        val candidatePage = pageUrl ?: body.optString("pageURL").ifBlank { null }
        val candidate = HlsCandidate(
            streamUrl = streamUrl,
            pageUrl = candidatePage,
            pageTitle = resolvedTitle.ifBlank { selectedSource.name },
            sourceId = selectedSource.id,
            role = role
        )
        if (hlsCandidate?.streamUrl != candidate.streamUrl || hlsCandidate?.pageTitle != candidate.pageTitle) {
            hlsCandidate = candidate
            publish()
        }
    }

    private fun updateTitle(title: String, url: String?) {
        if (url != null && !SitePolicy.isFirstPartyPage(url, navigationHosts())) return
        pageTitle = title.trim().ifEmpty { selectedSource.name }
        if (hlsCandidate != null && title.isNotBlank()) {
            hlsCandidate = hlsCandidate?.copy(pageTitle = title.trim())
        }
    }

    private fun rememberLocation(url: String) {
        val http = url.startsWith("https://") || url.startsWith("http://")
        if (!http) return
        pageUrl = url
        val source = sources.firstOrNull { it.id == displayedSourceId } ?: return
        if (!SitePolicy.isKeepable(url, source.allowedHosts)) return
        sessions.save(source.id, url, pageTitle, source.allowedHosts)
    }

    private fun emit(command: BrowserCommand) {
        _commands.tryEmit(command)
    }

    private fun publish() {
        refreshState?.invoke()
    }

    override fun onCleared() {
        persistSession()
    }
}
