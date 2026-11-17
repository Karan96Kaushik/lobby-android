package com.karankaushik.lobby.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karankaushik.lobby.BrowserController
import com.karankaushik.lobby.model.MediaSource
import com.karankaushik.lobby.remote.RemoteHub
import kotlinx.coroutines.delay

private enum class HomeFocus { CAROUSEL, SETTINGS }

private val cardWidth = 320.dp
private val cardHeight = 300.dp
private val cardGap = 28.dp

@Composable
fun FireTvHome(controller: BrowserController, remote: RemoteHub, onWatch: () -> Unit) {
    var showRemote by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    val homeFocus = remember { mutableStateOf(HomeFocus.CAROUSEL) }
    var pairedWith by remember { mutableStateOf(remote.hostPeer) }
    var success by remember { mutableStateOf<String?>(null) }
    val onWatchState = rememberUpdatedState(onWatch)

    LaunchedEffect(remote.hostPeer) {
        val peer = remote.hostPeer
        if (peer != null && pairedWith == null) {
            if (showRemote) showRemote = false
            success = "$peer connected"
        }
        pairedWith = peer
    }
    LaunchedEffect(success) {
        if (success == null) return@LaunchedEffect
        delay(5000)
        success = null
    }
    LaunchedEffect(Unit) {
        if (!remote.hosting) remote.startHosting()
    }
    BackHandler(enabled = showRemote || settingsOpen) {
        if (showRemote) showRemote = false else settingsOpen = false
    }

    if (showRemote) {
        FireTvRemotePage(hub = remote, onClose = { showRemote = false })
        return
    }

    if (settingsOpen) {
        FireTvSettings(
            controller = controller,
            remote = remote,
            success = success,
            onRemote = { showRemote = true },
            onDone = {
                homeFocus.value = HomeFocus.SETTINGS
                settingsOpen = false
            }
        )
        return
    }

    DisposableEffect(Unit) {
        val handler: (KeyEvent) -> Boolean = handler@{ event ->
            val handled = event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                event.keyCode == KeyEvent.KEYCODE_ENTER ||
                event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                event.keyCode == KeyEvent.KEYCODE_BUTTON_A
            if (!handled) return@handler false
            if (event.action != KeyEvent.ACTION_DOWN) return@handler true
            val repeat = event.repeatCount > 0
            val focus = homeFocus.value
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (focus == HomeFocus.CAROUSEL) stepSource(controller, -1)
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (focus == HomeFocus.CAROUSEL) stepSource(controller, 1)
                }
                KeyEvent.KEYCODE_DPAD_UP -> if (!repeat && focus == HomeFocus.SETTINGS) {
                    homeFocus.value = HomeFocus.CAROUSEL
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> if (!repeat && focus == HomeFocus.CAROUSEL) {
                    homeFocus.value = HomeFocus.SETTINGS
                }
                else -> if (!repeat) {
                    when (focus) {
                        HomeFocus.CAROUSEL -> onWatchState.value()
                        HomeFocus.SETTINGS -> settingsOpen = true
                    }
                }
            }
            true
        }
        FireTvKeySink.handler = handler
        onDispose {
            if (FireTvKeySink.handler === handler) FireTvKeySink.handler = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(LobbyBackground)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(vertical = 32.dp)
        ) {
            Column(Modifier.padding(horizontal = 64.dp)) {
                Text("Lobby", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Left or right to choose. Select to watch.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 18.sp
                )
                success?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Text(message, color = LobbyAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                SourceCarousel(
                    sources = controller.sources,
                    selectedId = controller.selectedSource.id,
                    active = homeFocus.value == HomeFocus.CAROUSEL
                )
            }
        }
        SettingsIconButton(
            focused = homeFocus.value == HomeFocus.SETTINGS,
            onClick = { settingsOpen = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 36.dp, bottom = 28.dp)
        )
    }
}

@Composable
private fun SourceCarousel(
    sources: List<MediaSource>,
    selectedId: String,
    active: Boolean
) {
    val index = sources.indexOfFirst { it.id == selectedId }.let { if (it < 0) 0 else it }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { settled = true }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(cardHeight + 24.dp)
                .clipToBounds()
        ) {
            val target = (maxWidth - cardWidth) / 2 - (cardWidth + cardGap) * index
            val offset by animateDpAsState(
                targetValue = target,
                animationSpec = if (settled) tween(280) else snap(),
                label = "carouselOffset"
            )
            Row(
                Modifier
                    .wrapContentWidth(unbounded = true, align = Alignment.Start)
                    .offset(x = offset),
                horizontalArrangement = Arrangement.spacedBy(cardGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sources.forEach { source ->
                    CarouselCard(
                        source = source,
                        selected = source.id == selectedId,
                        active = active && source.id == selectedId
                    )
                }
            }
            EdgeFade(Modifier.align(Alignment.CenterStart), start = true)
            EdgeFade(Modifier.align(Alignment.CenterEnd), start = false)
            if (sources.size > 1) {
                Text(
                    "‹",
                    color = LobbyAccent,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
                )
                Text(
                    "›",
                    color = LobbyAccent,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (sources.size <= 16) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                sources.forEach { source ->
                    val selected = source.id == selectedId
                    Box(
                        Modifier
                            .size(if (selected) 10.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) LobbyAccent else Color.White.copy(alpha = 0.28f))
                    )
                }
            }
        } else {
            Text(
                "${index + 1} of ${sources.size}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun EdgeFade(modifier: Modifier, start: Boolean) {
    Box(
        modifier
            .width(88.dp)
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    if (start) {
                        listOf(LobbyBackground, Color.Transparent)
                    } else {
                        listOf(Color.Transparent, LobbyBackground)
                    }
                )
            )
    )
}

@Composable
private fun CarouselCard(source: MediaSource, selected: Boolean, active: Boolean) {
    val scale by animateFloatAsState(if (selected) 1f else 0.84f, label = "cardScale")
    val alpha by animateFloatAsState(if (selected) 1f else 0.45f, label = "cardAlpha")
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .width(cardWidth)
            .height(cardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(shape)
            .border(
                width = if (active) 4.dp else if (selected) 2.dp else 1.dp,
                color = if (selected) LobbyAccent else Color.White.copy(alpha = 0.16f),
                shape = shape
            )
            .background(if (selected) LobbyAccent.copy(alpha = 0.14f) else LobbySurface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SourceLogo(source, 96.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            source.name,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            source.hostLabel,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (active) {
            Spacer(Modifier.height(12.dp))
            Text("Select", color = LobbyAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun SettingsIconButton(
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .size(48.dp)
            .graphicsLayer {
                val scale = if (focused) 1.08f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) LobbyAccent else Color.White.copy(alpha = 0.22f),
                shape = shape
            )
            .background(LobbySurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Settings,
            contentDescription = "Settings",
            tint = if (focused) LobbyAccent else Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun FireTvSettings(
    controller: BrowserController,
    remote: RemoteHub,
    success: String?,
    onRemote: () -> Unit,
    onDone: () -> Unit
) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var allowsDownload by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    val downloadsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { downloadsFocus.requestFocus() }
    }
    val custom = controller.sources.filter { !it.isBuiltIn }

    Box(Modifier.fillMaxSize().background(LobbyBackground), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 880.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 64.dp, vertical = 36.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Settings",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(containerColor = LobbySurface, contentColor = Color.White)
                ) {
                    Text("Done")
                }
            }
            success?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(message, color = LobbyAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(28.dp))
            Text("Offline downloads", color = LobbyAccent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))
            SettingsSwitchRow(
                label = "Allow offline downloads for ${controller.selectedSource.name}",
                checked = controller.selectedSource.allowsDownload,
                onCheckedChange = { controller.setAllowsDownload(it, controller.selectedSource) },
                modifier = Modifier.focusRequester(downloadsFocus)
            )
            Spacer(Modifier.height(28.dp))
            Text("Sources", color = LobbyAccent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))
            if (!adding) {
                Button(
                    onClick = {
                        name = ""
                        address = ""
                        allowsDownload = false
                        addError = null
                        adding = true
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LobbySurface, contentColor = Color.White)
                ) {
                    Text("Add source")
                }
            } else {
                Text("Add a source", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    colors = tvFieldColors()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        addError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("https://example.com") },
                    singleLine = true,
                    colors = tvFieldColors()
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Allow offline downloads", color = Color.White, modifier = Modifier.weight(1f), fontSize = 18.sp)
                    Switch(
                        checked = allowsDownload,
                        onCheckedChange = { allowsDownload = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = LobbyAccent, checkedThumbColor = LobbyBackground)
                    )
                }
                addError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Color(0xFFFF8A80))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { adding = false },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LobbySurface, contentColor = Color.White)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val message = controller.addSource(name, address, allowsDownload)
                            if (message == null) {
                                name = ""
                                address = ""
                                allowsDownload = false
                                addError = null
                                adding = false
                            } else {
                                addError = message
                            }
                        },
                        enabled = address.isNotBlank(),
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LobbyAccent, contentColor = LobbyBackground)
                    ) {
                        Text("Add")
                    }
                }
            }
            if (custom.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Added by you", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                custom.forEach { source ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(source.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text(source.hostLabel, color = Color.White.copy(alpha = 0.6f))
                        }
                        Button(
                            onClick = { controller.remove(source) },
                            modifier = Modifier.height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LobbySurface,
                                contentColor = Color(0xFFFF8A80)
                            )
                        ) {
                            Text("Remove")
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("Remote", color = LobbyAccent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text(remoteStatus(remote), color = Color.White, fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))
            Text("Pairing PIN", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
            Text(
                remote.pin.ifBlank { "····" },
                color = LobbyAccent,
                fontSize = 64.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 12.sp
            )
            Text(
                when {
                    remote.hostPeer != null -> "Paired with ${remote.hostPeer}. Unpair to allow a different phone."
                    remote.fault != null -> remote.fault.orEmpty()
                    else -> "Enter this PIN in Lobby on your phone."
                },
                color = if (remote.fault != null && remote.hostPeer == null) Color(0xFFFF8A80) else Color.White,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRemote,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LobbySurface, contentColor = Color.White)
            ) {
                Text("Set up remote", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) LobbyAccent else Color.White.copy(alpha = 0.15f),
                shape = shape
            )
            .background(LobbySurface)
            .clickable(interactionSource = interaction, indication = null) { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.focusProperties { canFocus = false },
            colors = SwitchDefaults.colors(checkedTrackColor = LobbyAccent, checkedThumbColor = LobbyBackground)
        )
    }
}

private fun stepSource(controller: BrowserController, delta: Int) {
    val sources = controller.sources
    if (sources.isEmpty()) return
    val current = sources.indexOfFirst { it.id == controller.selectedSource.id }.let { if (it < 0) 0 else it }
    controller.chooseSource(sources[(current + delta).mod(sources.size)])
}

private fun remoteStatus(remote: RemoteHub): String = when {
    remote.hostPeer != null -> "${remote.hostPeer} is paired with this TV."
    remote.link is com.karankaushik.lobby.remote.RemoteLink.Connected -> "This TV is controlling another Lobby."
    else -> "The PIN above is ready for a phone on the same Wi-Fi."
}

@Composable
private fun tvFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = LobbyAccent,
    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
    cursorColor = LobbyAccent,
    focusedLabelColor = LobbyAccent,
    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
)
