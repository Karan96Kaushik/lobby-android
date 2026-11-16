package com.karankaushik.lobby.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.karankaushik.lobby.BrowserController
import com.karankaushik.lobby.download.HlsDownloadManager
import com.karankaushik.lobby.web.applyBrowserCommand
import com.karankaushik.lobby.web.createLobbyWebView

private enum class LobbySheet { SOURCES, PROTECTION, DOWNLOADS }

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun BrowserScreen(controller: BrowserController, downloads: HlsDownloadManager) {
    val context = LocalContext.current
    var chromeVisible by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf<LobbySheet?>(null) }
    var playingId by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val webHost = remember { mutableStateOf<SwipeRefreshLayout?>(null) }

    LaunchedEffect(downloads) {
        downloads.banners.collect { controller.showNotice(it) }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.persistSession()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(controller) {
        controller.commands.collect { command ->
            val host = webHost.value ?: return@collect
            applyBrowserCommand(host, command, controller)
        }
    }

    val playing = playingId?.let { id -> downloads.items.firstOrNull { it.id == id } }
    BackHandler(enabled = playing != null || sheet != null || controller.canGoBack) {
        when {
            playing != null -> playingId = null
            sheet != null -> sheet = null
            controller.canGoBack -> controller.goBack()
        }
    }

    Box(Modifier.fillMaxSize().background(LobbyBackground)) {
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Toolbar(
                    controller = controller,
                    downloadsActive = downloads.activeDownloadCount > 0,
                    onSources = { sheet = LobbySheet.SOURCES },
                    onProtection = { sheet = LobbySheet.PROTECTION },
                    onDownloads = { sheet = LobbySheet.DOWNLOADS },
                    onSave = {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        controller.downloadCurrent(downloads)
                    },
                    onHide = { chromeVisible = false }
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        val swipe = createLobbyWebView(viewContext, controller)
                        val webView = swipe.getChildAt(0) as WebView
                        webView.loadUrl(controller.initialPageUrl())
                        webHost.value = swipe
                        swipe
                    },
                    onRelease = { swipe ->
                        val webView = swipe.getChildAt(0) as? WebView
                        webView?.removeJavascriptInterface("LobbyBridge")
                        webView?.destroy()
                        webHost.value = null
                    }
                )
                controller.errorMessage?.let { message ->
                    ErrorOverlay(message, onHome = controller::goHome, onRetry = controller::reload)
                }
                if (!chromeVisible) {
                    TextButton(
                        onClick = { chromeVisible = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Monogram(controller.selectedSource.name, 22.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(controller.selectedSource.name, color = Color.White, maxLines = 1)
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Show controls", tint = Color.White)
                        }
                    }
                }
                controller.notice?.let { text ->
                    Text(
                        text,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(LobbySurface)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        if (playing != null) {
            OfflinePlayer(itemId = playing.id, downloads = downloads, onClose = { playingId = null })
        }
    }

    if (sheet != null) {
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = sheetState,
            containerColor = LobbyBackground
        ) {
            when (sheet) {
                LobbySheet.SOURCES -> SourcesSheet(
                    controller = controller,
                    onOpen = { sheet = null }
                )
                LobbySheet.PROTECTION -> ProtectionSheet(controller)
                LobbySheet.DOWNLOADS -> DownloadsSheet(
                    controller = controller,
                    downloads = downloads,
                    onPlay = { playingId = it },
                    onRetryFromPage = { sheet = null }
                )
                null -> Unit
            }
        }
    }
}

@Composable
private fun Toolbar(
    controller: BrowserController,
    downloadsActive: Boolean,
    onSources: () -> Unit,
    onProtection: () -> Unit,
    onDownloads: () -> Unit,
    onSave: () -> Unit,
    onHide: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(LobbySurface.copy(alpha = 0.94f))
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onSources) {
                Monogram(controller.selectedSource.name, 28.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    controller.selectedSource.name,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
            ToolIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", controller.canGoBack, controller::goBack)
            ToolIcon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", controller.canGoForward, controller::goForward)
            if (controller.isLoading) {
                ToolIcon(Icons.Filled.Close, "Stop", true, controller::stop)
            } else {
                ToolIcon(Icons.Filled.Refresh, "Reload", true, controller::reload)
            }
            ToolIcon(Icons.Filled.Home, "Home", true, controller::goHome)
            if (controller.selectedSource.allowsDownload) {
                if (controller.hlsCandidate != null) {
                    TextButton(onClick = onSave) {
                        Text("Save", color = LobbyBackground, fontWeight = FontWeight.Bold, modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(LobbyAccent)
                            .padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
                Box {
                    ToolIcon(Icons.Filled.Download, "Downloads", true, onDownloads)
                    if (downloadsActive) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(LobbyAccent)
                        )
                    }
                }
            }
            ToolIcon(Icons.Filled.Shield, "Protection", true, onProtection, LobbyAccent)
            ToolIcon(Icons.Filled.KeyboardArrowUp, "Hide controls", true, onHide)
        }
        if (controller.isLoading) {
            LinearProgressIndicator(
                progress = { controller.progress.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = LobbyAccent,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
private fun ToolIcon(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = label, tint = if (enabled) tint else Color.White.copy(alpha = 0.28f))
    }
}

@Composable
private fun ErrorOverlay(message: String, onHome: () -> Unit, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(LobbyBackground.copy(alpha = 0.94f)).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.WifiOff, contentDescription = null, tint = LobbyAccent, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onHome) { Text("Home") }
            TextButton(onClick = onRetry) { Text("Try Again", color = LobbyAccent) }
        }
    }
}

@Composable
fun Monogram(name: String, size: androidx.compose.ui.unit.Dp) {
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        Modifier.size(size).clip(CircleShape).background(LobbyAccent),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, color = LobbyBackground, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun OfflinePlayer(itemId: String, downloads: HlsDownloadManager, onClose: () -> Unit) {
    val context = LocalContext.current
    val item = downloads.items.firstOrNull { it.id == itemId }
    val player = remember(itemId) {
        val current = downloads.items.firstOrNull { it.id == itemId } ?: return@remember null
        runCatching { downloads.createPlayer(context, current) }.getOrNull()
    }
    DisposableEffect(player) {
        onDispose { player?.release() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (player != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PlayerView(viewContext).apply { this.player = player }
                }
            )
        } else {
            Text(
                item?.errorMessage ?: "This offline video needs to be downloaded again.",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                textAlign = TextAlign.Center
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding()) {
            Icon(Icons.Filled.Close, contentDescription = "Close player", tint = Color.White)
        }
    }
}
