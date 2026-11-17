package com.karankaushik.lobby.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karankaushik.lobby.BrowserController
import com.karankaushik.lobby.download.HlsDownloadManager
import com.karankaushik.lobby.model.DownloadFailureKind
import com.karankaushik.lobby.model.DownloadItem
import com.karankaushik.lobby.model.DownloadQuality
import com.karankaushik.lobby.model.DownloadState
import com.karankaushik.lobby.model.MediaSource
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SourcesSheet(controller: BrowserController, onOpen: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var allowsDownload by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text("Sources", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Built in", color = Color.White.copy(alpha = 0.6f))
        controller.sources.filter { it.isBuiltIn }.forEach { source ->
            SourceRow(source, source.id == controller.selectedSource.id, onOpen = {
                controller.open(source)
                onOpen()
            }, onToggle = { controller.setAllowsDownload(it, source) })
        }
        Text(
            "The download switch stays off until you turn it on for that site.",
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        val custom = controller.sources.filter { !it.isBuiltIn }
        if (custom.isNotEmpty()) {
            Text("Added by you", color = Color.White.copy(alpha = 0.6f))
            custom.forEach { source ->
                SourceRow(
                    source,
                    source.id == controller.selectedSource.id,
                    onOpen = {
                        controller.open(source)
                        onOpen()
                    },
                    onToggle = { controller.setAllowsDownload(it, source) },
                    onRemove = { controller.remove(source) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (!adding) {
            Button(
                onClick = {
                    name = ""
                    address = ""
                    allowsDownload = false
                    addError = null
                    adding = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add source")
            }
        } else {
            Text("Add a source", color = Color.White.copy(alpha = 0.6f))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name (optional)") },
                singleLine = true
            )
            OutlinedTextField(
                value = address,
                onValueChange = {
                    address = it
                    addError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("https://example.com") },
                singleLine = true
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Allow offline downloads", color = Color.White, modifier = Modifier.weight(1f))
                Switch(checked = allowsDownload, onCheckedChange = { allowsDownload = it })
            }
            addError?.let { Text(it, color = Color(0xFFFF8A80), modifier = Modifier.padding(top = 4.dp)) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                TextButton(
                    onClick = { adding = false },
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
                Button(
                    onClick = {
                        val message = controller.addSource(name, address, allowsDownload)
                        if (message == null) onOpen() else addError = message
                    },
                    enabled = address.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add")
                }
            }
            Text(
                "Use the site’s main address. Lobby keeps you on that site. Offline save is off unless this switch is on.",
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SourceRow(
    source: MediaSource,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .combinedClickable(onClick = onOpen, onLongClick = { onToggle(!source.allowsDownload) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SourceLogo(source, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (source.allowsDownload) {
                    Text(
                        "Offline",
                        color = LobbyAccent,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(LobbyAccent.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(source.hostLabel, color = Color.White.copy(alpha = 0.6f))
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Current source", tint = LobbyAccent)
        }
        Switch(
            checked = source.allowsDownload,
            onCheckedChange = onToggle
        )
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove ${source.name}", tint = Color(0xFFFF8A80))
            }
        }
    }
}

@Composable
fun ProtectionSheet(controller: BrowserController) {
    var confirmClear by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text("Protection", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Locked to ${controller.selectedSource.hostLabel}", color = LobbyAccent, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        listOf(
            "New windows and pop-ups stay closed",
            "The page cannot leave ${controller.selectedSource.hostLabel}",
            "Installers and file downloads are blocked",
            "Camera, microphone, and location stay off",
            "Known popup and ad hosts stay blocked",
            "Offline save runs only when this site’s switch is on"
        ).forEach { line ->
            Text("✓  $line", color = Color.White, modifier = Modifier.padding(vertical = 4.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CountCard(controller.popupBlockCount, "popups stopped", Modifier.weight(1f))
            CountCard(controller.downloadBlockCount, "downloads stopped", Modifier.weight(1f))
        }
        Text(
            "Don't enter passwords or payment details in this viewer.",
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 16.dp)
        )
        TextButton(onClick = { confirmClear = true }, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)) {
            Text("Clear browsing data", color = Color(0xFFFF8A80))
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear browsing data?") },
            text = { Text("Cookies, saved pages, and site data will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    controller.clearData()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CountCard(value: Int, label: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(12.dp)
    ) {
        Text(value.toString(), color = Color.White, fontWeight = FontWeight.SemiBold)
        Text(label, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
fun DownloadsSheet(
    controller: BrowserController,
    downloads: HlsDownloadManager,
    onPlay: (String) -> Unit,
    onRetryFromPage: () -> Unit
) {
    LaunchedEffect(Unit) { downloads.verifyFilesOnDisk() }
    val completed = downloads.items.count { it.state == DownloadState.COMPLETED }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text("Downloads", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (!controller.selectedSource.allowsDownload) {
            Text(
                "Offline downloads are off for ${controller.selectedSource.name}. Turn the switch on in Sources to allow them for this site.",
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Offline storage used", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("$completed saved video${if (completed == 1) "" else "s"}", color = Color.White.copy(alpha = 0.6f))
            }
            Text(formatBytes(downloads.totalStorageBytes), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text("Max quality", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)
        ) {
            DownloadQuality.entries.forEach { quality ->
                FilterChip(
                    selected = downloads.preferredQuality == quality,
                    onClick = { downloads.setQuality(quality) },
                    label = { Text(quality.shortLabel) }
                )
            }
        }
        Text("Current page", color = Color.White.copy(alpha = 0.6f))
        val candidate = controller.hlsCandidate
        if (controller.selectedSource.allowsDownload && candidate != null) {
            Text(candidate.pageTitle.ifBlank { controller.pageTitle }, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(candidateLabel(candidate.role.name, candidate.streamUrl), color = Color.White.copy(alpha = 0.6f))
            Button(
                onClick = { controller.downloadCurrent(downloads) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) { Text("Download for offline") }
        } else {
            Text(
                if (controller.selectedSource.allowsDownload) {
                    "Start playing a video on ${controller.selectedSource.name} to detect its HLS stream."
                } else {
                    "This site does not allow offline downloads."
                },
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        Text("Saved & active", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp))
        if (downloads.items.isEmpty()) {
            Text("No offline downloads yet", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 20.dp))
        } else {
            downloads.items.forEach { item ->
                DownloadRow(
                    item = item,
                    sourceName = controller.sources.firstOrNull { it.id == item.sourceId }?.name ?: item.sourceId,
                    onPlay = { onPlay(item.id) },
                    onPause = { downloads.pause(item) },
                    onResume = { downloads.resume(item) },
                    onRetry = { downloads.retry(item) },
                    onRetryFromPage = {
                        controller.reopenPageForRetry(item.sourceId, item.pageUrl)
                        onRetryFromPage()
                    },
                    onDelete = { downloads.cancelAndDelete(item) }
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    sourceName: String,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRetryFromPage: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(enabled = item.state == DownloadState.COMPLETED) { onPlay() }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        sourceName,
                        stateSummary(item),
                        if (item.bytes > 0) formatBytes(item.bytes) else null
                    ).joinToString(" · "),
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            when (item.state) {
                DownloadState.DOWNLOADING, DownloadState.QUEUED -> IconButton(onClick = onPause) {
                    Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = Color.White)
                }
                DownloadState.PAUSED -> IconButton(onClick = onResume) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Resume", tint = LobbyAccent)
                }
                DownloadState.COMPLETED -> IconButton(onClick = onPlay) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = LobbyAccent)
                }
                DownloadState.FAILED, DownloadState.NEEDS_REDOWNLOAD -> Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF8A80),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFFF8A80))
            }
        }
        if (item.state == DownloadState.DOWNLOADING || item.state == DownloadState.PAUSED || item.state == DownloadState.QUEUED) {
            LinearProgressIndicator(
                progress = { item.progress.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                color = if (item.state == DownloadState.PAUSED) Color(0xFFFFB74D) else LobbyAccent,
                trackColor = Color.White.copy(alpha = 0.08f)
            )
        }
        item.errorMessage?.let {
            Text(it, color = if (item.state == DownloadState.NEEDS_REDOWNLOAD) Color(0xFFFFB74D) else Color(0xFFFF8A80))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.state == DownloadState.NEEDS_REDOWNLOAD || item.failureKind?.supportsDirectRetry == true) {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
            if (item.pageUrl != null && (item.state == DownloadState.NEEDS_REDOWNLOAD || item.failureKind?.supportsRetryFromPage == true)) {
                TextButton(onClick = onRetryFromPage) { Text("Retry from page") }
            }
        }
    }
}

private fun stateSummary(item: DownloadItem): String = when (item.state) {
    DownloadState.QUEUED -> "Queued"
    DownloadState.DOWNLOADING -> "Downloading ${(item.progress * 100).toInt()}%"
    DownloadState.PAUSED -> "Paused (${(item.progress * 100).toInt()}%)"
    DownloadState.COMPLETED -> "Ready offline"
    DownloadState.NEEDS_REDOWNLOAD -> "Needs re-download"
    DownloadState.FAILED -> when (item.failureKind) {
        DownloadFailureKind.LIVE_STREAM -> "Can’t download (Live)"
        DownloadFailureKind.DRM_PROTECTED -> "DRM protected"
        DownloadFailureKind.EXPIRED_URL -> "Expired link"
        DownloadFailureKind.FORBIDDEN_403 -> "Rejected (403)"
        DownloadFailureKind.NO_DISK_SPACE -> "No disk space"
        DownloadFailureKind.NETWORK_LOST -> "Network lost"
        DownloadFailureKind.NO_CANDIDATE -> "No candidate"
        DownloadFailureKind.OTHER, null -> "Failed"
    }
}

private fun candidateLabel(role: String, url: String): String {
    val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
    return when (role) {
        "MASTER" -> "Master HLS playlist ($host)"
        "VARIANT" -> "Variant HLS playlist ($host)"
        else -> "HLS stream ($host)"
    }
}

fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.coerceAtLeast(0).toDouble()
    var index = 0
    while (size >= 1024 && index < units.lastIndex) {
        size /= 1024
        index++
    }
    return if (index == 0) "${bytes.coerceAtLeast(0)} B" else String.format(Locale.US, "%.1f %s", size, units[index])
}
