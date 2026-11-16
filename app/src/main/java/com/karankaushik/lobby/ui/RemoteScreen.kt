package com.karankaushik.lobby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class RemoteMode { CONTROL, SHARE }

@Composable
fun RemoteScreen(controller: BrowserController, hub: RemoteHub, onClose: () -> Unit) {
    var mode by remember { mutableStateOf(if (hub.hosting) RemoteMode.SHARE else RemoteMode.CONTROL) }
    val connected = hub.link is RemoteLink.Connected
    DisposableEffect(Unit) {
        onDispose { hub.stopScanning() }
    }
    LaunchedEffect(mode, connected) {
        if (mode == RemoteMode.CONTROL && !connected) hub.startScanning() else hub.stopScanning()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(LobbyBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
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
                ControlPad(controller, hub)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("Control", mode == RemoteMode.CONTROL) { mode = RemoteMode.CONTROL }
                    ModeChip("Allow control", mode == RemoteMode.SHARE) { mode = RemoteMode.SHARE }
                }
                Spacer(Modifier.height(20.dp))
                if (mode == RemoteMode.SHARE) SharePane(hub) else FindPane(hub)
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
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
private fun SharePane(hub: RemoteHub) {
    if (!hub.hosting) {
        Text(
            "Show a one-time PIN. The other Lobby enters it and can drive this screen.",
            color = Color.White.copy(alpha = 0.8f)
        )
        hub.fault?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(message, color = Color(0xFFFF8A80))
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { hub.startHosting() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
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
        if (hub.hostPeer != null) {
            "Paired with ${hub.hostPeer}. This PIN will not work again."
        } else {
            "Enter this PIN on the other Lobby. Five wrong tries replace it."
        },
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { hub.regeneratePin() },
            modifier = Modifier.weight(1f),
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
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) LobbyAccent.copy(alpha = 0.16f) else LobbySurface)
                .clickable {
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
            onValueChange = { pinEntry = it.filter { char -> char.isDigit() }.take(4) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { Text("4 digits") },
            colors = fieldColors()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { hub.connect(peer, pinEntry) },
            enabled = pinEntry.length == 4,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LobbyAccent, contentColor = LobbyBackground)
        ) {
            Text("Pair", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ControlPad(controller: BrowserController, hub: RemoteHub) {
    val connected = hub.link as? RemoteLink.Connected
    var address by remember { mutableStateOf("") }
    var addressError by remember { mutableStateOf<String?>(null) }
    Text(
        "Controlling ${connected?.name ?: "Lobby"}",
        color = Color.White,
        fontWeight = FontWeight.SemiBold
    )
    TextButton(onClick = { hub.disconnect() }) { Text("Disconnect", color = Color(0xFFFF8A80)) }
    OutlinedTextField(
        value = address,
        onValueChange = {
            address = it
            addressError = null
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        placeholder = { Text("https://…") },
        colors = fieldColors()
    )
    addressError?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp)
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                val normalized = RemoteUrl.normalize(address)
                if (normalized == null) {
                    addressError = "Enter a web address."
                } else {
                    hub.sendUrl(normalized)
                    address = ""
                }
            },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = LobbyAccent, contentColor = LobbyBackground)
        ) {
            Text("Open URL", fontWeight = FontWeight.Bold)
        }
        val page = controller.pageUrl
        if (page != null) {
            Button(
                onClick = { hub.sendUrl(page) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = LobbySurface, contentColor = Color.White)
            ) {
                Text("Send this page")
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    Text(
        "Arrows move through the page. OK presses the focused item.",
        color = Color.White.copy(alpha = 0.62f),
        fontSize = 13.sp
    )
    Spacer(Modifier.height(10.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PadKey("Up", Modifier.width(104.dp), repeat = true) { hub.sendKey("up") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PadKey("Left", Modifier.width(104.dp), repeat = true) { hub.sendKey("left") }
            PadKey("OK", Modifier.width(104.dp), accent = true) { hub.sendKey("select") }
            PadKey("Right", Modifier.width(104.dp), repeat = true) { hub.sendKey("right") }
        }
        PadKey("Down", Modifier.width(104.dp), repeat = true) { hub.sendKey("down") }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PadKey("Page up", Modifier.weight(1f), repeat = true) { hub.sendKey("pageup") }
        PadKey("Page down", Modifier.weight(1f), repeat = true) { hub.sendKey("pagedown") }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PadKey("Back", Modifier.weight(1f)) { hub.sendKey("back") }
        PadKey("Forward", Modifier.weight(1f)) { hub.sendKey("forward") }
        PadKey("Reload", Modifier.weight(1f)) { hub.sendKey("reload") }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PadKey("Home", Modifier.weight(1f)) { hub.sendKey("home") }
        PadKey("Stop", Modifier.weight(1f)) { hub.sendKey("stop") }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PadKey("−10s", Modifier.weight(1f)) { hub.sendKey("seekback") }
        PadKey("Play", Modifier.weight(1f), accent = true) { hub.sendKey("play") }
        PadKey("+10s", Modifier.weight(1f)) { hub.sendKey("seekfwd") }
        PadKey("Mute", Modifier.weight(1f)) { hub.sendKey("mute") }
    }
    if (!hub.hosting) {
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { hub.startHosting() }) {
            Text("Also allow control of this device", color = LobbyAccent)
        }
    } else {
        Spacer(Modifier.height(16.dp))
        Text(
            if (hub.hostPeer != null) "This device is paired with ${hub.hostPeer}" else "This device's PIN is ${hub.pin}",
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun PadKey(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    repeat: Boolean = false,
    onPress: () -> Unit
) {
    val current = rememberUpdatedState(onPress)
    val color = if (accent) LobbyAccent else LobbySurface
    val content = if (accent) LobbyBackground else Color.White
    Box(
        modifier
            .padding(vertical = 4.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .then(
                if (repeat) {
                    Modifier.pointerInput(Unit) {
                        coroutineScope {
                            while (isActive) {
                                awaitEachGesture {
                                    awaitFirstDown()
                                    val job = launch {
                                        current.value()
                                        delay(380)
                                        while (isActive) {
                                            current.value()
                                            delay(90)
                                        }
                                    }
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.changes.all { !it.pressed }) break
                                        }
                                    } finally {
                                        job.cancel()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Modifier.clickable { current.value() }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = content, fontWeight = FontWeight.SemiBold)
    }
}

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
