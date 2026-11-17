package com.karankaushik.lobby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karankaushik.lobby.BrowserController
import com.karankaushik.lobby.remote.DiscoveredPeer
import com.karankaushik.lobby.remote.RemoteHub
import com.karankaushik.lobby.remote.RemoteLink
import com.karankaushik.lobby.remote.RemoteUrl

private enum class RemoteMode { CONTROL, SHARE }

@Composable
fun RemoteScreen(controller: BrowserController, hub: RemoteHub, onClose: () -> Unit) {
    var mode by remember { mutableStateOf(if (hub.hosting) RemoteMode.SHARE else RemoteMode.CONTROL) }
    val connected = hub.link is RemoteLink.Connected
    val controlFocus = remember { FocusRequester() }
    val shareFocus = remember { FocusRequester() }
    val hostingFocus = remember { FocusRequester() }
    val padFocus = remember { FocusRequester() }
    DisposableEffect(Unit) {
        onDispose { hub.stopScanning() }
    }
    LaunchedEffect(mode, connected) {
        if (mode == RemoteMode.CONTROL && !connected) hub.startScanning() else hub.stopScanning()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(LobbyBackground),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Remote", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Same Wi-Fi. Both copies of Lobby stay open.",
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 13.sp
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close remote", tint = Color.White)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (connected) {
                ControlPad(controller, hub, padFocus)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("Control", mode == RemoteMode.CONTROL, Modifier.focusRequester(controlFocus)) {
                        mode = RemoteMode.CONTROL
                    }
                    ModeChip("Allow control", mode == RemoteMode.SHARE) { mode = RemoteMode.SHARE }
                }
                Spacer(Modifier.height(20.dp))
                if (mode == RemoteMode.SHARE) {
                    SharePane(hub, if (hub.hosting) hostingFocus else shareFocus)
                } else if (hub.link is RemoteLink.Paired) {
                    StandbyPane(hub.link as RemoteLink.Paired, hub)
                } else {
                    FindPane(hub)
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    TextButton(onClick = onClick, interactionSource = interaction, modifier = modifier.focusRing(focused)) {
        Text(
            label,
            color = if (selected) LobbyBackground else Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (selected) LobbyAccent else LobbySurface)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SharePane(hub: RemoteHub, primaryFocus: FocusRequester) {
    if (!hub.hosting) {
        Text(
            "Show a PIN. Other phones enter it to pair. Several can stay paired, and only one controls at a time.",
            color = Color.White.copy(alpha = 0.8f)
        )
        hub.fault?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(message, color = Color(0xFFFF8A80))
        }
        Spacer(Modifier.height(20.dp))
        val allowInteraction = remember { MutableInteractionSource() }
        val allowFocused by allowInteraction.collectIsFocusedAsState()
        Button(
            onClick = { hub.startHosting() },
            interactionSource = allowInteraction,
            modifier = Modifier.fillMaxWidth().height(56.dp).focusRequester(primaryFocus).focusRing(allowFocused),
            colors = ButtonDefaults.buttonColors(containerColor = LobbyAccent, contentColor = LobbyBackground)
        ) {
            Text("Allow control", fontWeight = FontWeight.Bold)
        }
        return
    }
    Text(hub.deviceName, color = Color.White.copy(alpha = 0.62f))
    Spacer(Modifier.height(8.dp))
    Text(
        hub.pin,
        color = LobbyAccent,
        fontSize = 56.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        letterSpacing = 10.sp,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        pairingSummary(hub),
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    if (hub.pairedDevices.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        hub.pairedDevices.forEach { device ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(device.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (device.controlling) "Controlling" else "Paired",
                        color = if (device.controlling) LobbyAccent else Color.White.copy(alpha = 0.62f),
                        fontSize = 13.sp
                    )
                }
                if (device.controlling) {
                    TextButton(onClick = { hub.releaseControl() }) { Text("Release", color = LobbyAccent) }
                }
                TextButton(onClick = { hub.unpairDevice(device.id) }) { Text("Remove", color = Color(0xFFFF8A80)) }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val newPinInteraction = remember { MutableInteractionSource() }
        val newPinFocused by newPinInteraction.collectIsFocusedAsState()
        Button(
            onClick = { hub.regeneratePin() },
            interactionSource = newPinInteraction,
            modifier = Modifier.weight(1f).height(56.dp).focusRequester(primaryFocus).focusRing(newPinFocused),
            colors = ButtonDefaults.buttonColors(containerColor = LobbySurface, contentColor = Color.White)
        ) {
            Text("New PIN")
        }
        Button(
            onClick = { hub.stopHosting() },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = LobbySurface, contentColor = Color.White)
        ) {
            Text("Stop")
        }
    }
}

@Composable
private fun FindPane(hub: RemoteHub) {
    var selected by remember { mutableStateOf<DiscoveredPeer?>(null) }
    var pinEntry by remember { mutableStateOf("") }
    val peers = hub.peers
    LaunchedEffect(peers) {
        val current = selected ?: return@LaunchedEffect
        if (peers.none { it.address == current.address && it.port == current.port }) selected = null
    }
    when (val state = hub.link) {
        is RemoteLink.Connecting -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = LobbyAccent, modifier = Modifier.height(22.dp).width(22.dp))
                Spacer(Modifier.width(12.dp))
                Text("Pairing with ${state.name}…", color = Color.White)
            }
            return
        }
        is RemoteLink.Failed -> {
            Text(state.message, color = Color(0xFFFF8A80))
            TextButton(onClick = { hub.dismissError() }) { Text("Try again", color = LobbyAccent) }
            Spacer(Modifier.height(8.dp))
        }
        else -> Unit
    }
    hub.fault?.let { Text(it, color = Color(0xFFFF8A80)) }
    if (peers.isEmpty()) {
        Text(
            if (hub.scanning) "Looking for Lobby on this network…" else "Search stopped.",
            color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "On the other device, open Remote and tap Allow control.",
            color = Color.White.copy(alpha = 0.62f)
        )
    }
    peers.forEach { peer ->
        val isSelected = selected?.address == peer.address && selected?.port == peer.port
        val interaction = remember(peer.address.hostAddress, peer.port) { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) LobbyAccent.copy(alpha = 0.16f) else LobbySurface)
                .focusRing(focused)
                .clickable(interactionSource = interaction, indication = null) {
                    selected = peer
                    pinEntry = ""
                }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(peer.name, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(peer.address.hostAddress ?: "", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
    }
    val peer = selected
    if (peer != null) {
        Spacer(Modifier.height(16.dp))
        Text("PIN for ${peer.name}", color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pinEntry,
            onValueChange = { updated ->
                pinEntry = updated.filter { it.isDigit() }.take(4)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            colors = fieldColors()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { hub.connect(peer, pinEntry, fresh = true) },
            enabled = pinEntry.length == 4,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LobbyAccent, contentColor = LobbyBackground)
        ) {
            Text("Pair", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StandbyPane(state: RemoteLink.Paired, hub: RemoteHub) {
    Text("Paired with ${state.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    Spacer(Modifier.height(8.dp))
    Text(
        if (state.controller != null) "${state.controller} is controlling." else "No one is controlling.",
        color = Color.White.copy(alpha = 0.8f)
    )
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = { hub.takeControl() },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LobbyAccent, contentColor = LobbyBackground)
    ) {
        Text(if (state.controller == null) "Control" else "Take control", fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = { hub.unpair() }, modifier = Modifier.fillMaxWidth()) {
        Text("Unpair", color = Color(0xFFFF8A80))
    }
}

@Composable
private fun ControlPad(controller: BrowserController, hub: RemoteHub, padFocus: FocusRequester) {
    val connected = hub.link as? RemoteLink.Connected
    var address by remember { mutableStateOf("") }
    var addressError by remember { mutableStateOf<String?>(null) }
    var enteringAddress by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(connected?.name ?: "Lobby", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("Connected", color = LobbyAccent, fontSize = 13.sp)
        }
        RemoteButton(
            Icons.Filled.PowerSettingsNew,
            "Release control",
            size = 52.dp,
            contentColor = Color(0xFFFF8A80)
        ) { hub.disconnect() }
    }
    TextButton(onClick = { hub.unpair() }) { Text("Unpair", color = Color(0xFFFF8A80)) }
    Spacer(Modifier.height(20.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(36.dp))
            .background(LobbySurface.copy(alpha = 0.72f))
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RemoteButton(
            Icons.Filled.KeyboardArrowUp,
            "Up",
            size = 68.dp,
            repeat = true
        ) { hub.sendKey("up") }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteButton(Icons.Filled.KeyboardArrowLeft, "Left", size = 68.dp, repeat = true) { hub.sendKey("left") }
            RemoteButton(
                icon = null,
                description = "OK",
                accent = true,
                size = 84.dp,
                modifier = Modifier.focusRequester(padFocus)
            ) { hub.sendKey("select") }
            RemoteButton(Icons.Filled.KeyboardArrowRight, "Right", size = 68.dp, repeat = true) { hub.sendKey("right") }
        }
        RemoteButton(Icons.Filled.KeyboardArrowDown, "Down", size = 68.dp, repeat = true) { hub.sendKey("down") }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteButton(Icons.Filled.Replay10, "Back 10 seconds") { hub.sendKey("seekback") }
            RemoteButton(Icons.Filled.PlayArrow, "Play", accent = true, size = 76.dp) { hub.sendKey("play") }
            RemoteButton(Icons.Filled.Forward10, "Forward 10 seconds") { hub.sendKey("seekfwd") }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            RemoteButton(Icons.Filled.Replay5, "Back 5 minutes") { hub.sendKey("seekback5") }
            RemoteButton(Icons.Filled.VolumeOff, "Mute") { hub.sendKey("mute") }
            RemoteButton(Icons.Filled.Forward5, "Forward 5 minutes") { hub.sendKey("seekfwd5") }
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RemoteButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", size = 52.dp) { hub.sendKey("back") }
            RemoteButton(Icons.Filled.Home, "Home", size = 52.dp) { hub.sendKey("home") }
            RemoteButton(
                if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                if (fullscreen) "Exit fullscreen" else "Fullscreen",
                size = 52.dp
            ) {
                hub.sendKey(if (fullscreen) "exitfullscreen" else "fullscreen")
                fullscreen = !fullscreen
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RemoteButton(Icons.Filled.UnfoldLess, "Hide bar", size = 52.dp) { hub.sendKey("hidebar") }
            RemoteButton(Icons.Filled.UnfoldMore, "Show bar", size = 52.dp) { hub.sendKey("showbar") }
            RemoteButton(Icons.Filled.Send, "Send this page", size = 52.dp) {
                val normalized = controller.pageUrl?.let(RemoteUrl::normalize)
                if (normalized == null) {
                    addressError = "Open a page in Lobby first."
                } else {
                    hub.sendUrl(normalized)
                    addressError = null
                }
            }
            RemoteButton(
                Icons.Filled.Language,
                "Open address",
                size = 52.dp,
                accent = enteringAddress
            ) {
                enteringAddress = !enteringAddress
                addressError = null
            }
        }
        if (enteringAddress) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        addressError = null
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    placeholder = { Text("https://…") },
                    colors = fieldColors()
                )
                Spacer(Modifier.width(8.dp))
                RemoteButton(Icons.Filled.Send, "Open", accent = true, size = 52.dp) {
                    val normalized = RemoteUrl.normalize(address)
                    if (normalized == null) {
                        addressError = "Enter a web address."
                    } else {
                        hub.sendUrl(normalized)
                        address = ""
                        addressError = null
                    }
                }
            }
        }
        addressError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
    if (hub.hosting) {
        Spacer(Modifier.height(16.dp))
        Text(
            if (hub.pairedDevices.isEmpty()) "PIN ${hub.pin}" else pairingSummary(hub),
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RemoteButton(
    icon: ImageVector?,
    description: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 64.dp,
    repeat: Boolean = false,
    contentColor: Color? = null,
    onPress: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Button(
        onClick = onPress,
        interactionSource = interaction,
        modifier = modifier
            .padding(vertical = 4.dp)
            .size(size)
            .focusRing(focused, CircleShape)
            .onPreviewKeyEvent { event ->
                if (!repeat) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent
                val center = native.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                    native.keyCode == android.view.KeyEvent.KEYCODE_ENTER
                if (center && native.action == android.view.KeyEvent.ACTION_DOWN && native.repeatCount > 0) {
                    onPress()
                    true
                } else {
                    false
                }
            },
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (accent) LobbyAccent else Color.White.copy(alpha = 0.08f),
            contentColor = contentColor ?: if (accent) LobbyBackground else Color.White
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = description)
        } else {
            Text(description, fontWeight = FontWeight.Bold)
        }
    }
}

private fun pairingSummary(hub: RemoteHub): String {
    val paired = hub.pairedDevices
    val controlling = paired.firstOrNull { it.controlling }?.name
    val names = paired.joinToString { it.name }
    return when {
        controlling != null && paired.size == 1 -> "$controlling is controlling."
        controlling != null -> "$controlling is controlling. Paired: $names."
        paired.isNotEmpty() -> "Paired: $names. None are controlling."
        else -> "Enter this PIN on another Lobby. Several phones can pair. Only one controls at a time."
    }
}

private fun Modifier.focusRing(focused: Boolean, shape: Shape = RoundedCornerShape(16.dp)): Modifier = border(
    width = if (focused) 3.dp else 0.dp,
    color = Color.White,
    shape = shape
)
@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = LobbyAccent,
    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
    cursorColor = LobbyAccent,
    focusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f)
)
