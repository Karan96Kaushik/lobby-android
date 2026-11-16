package com.karankaushik.lobby.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karankaushik.lobby.BrowserController
import kotlinx.coroutines.delay
import com.karankaushik.lobby.model.MediaSource
import com.karankaushik.lobby.remote.RemoteHub

@Composable
fun FireTvHome(controller: BrowserController, remote: RemoteHub, onWatch: () -> Unit) {
    var showRemote by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var allowsDownload by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    val watchFocus = remember { FocusRequester() }
    var pairedWith by remember { mutableStateOf(remote.hostPeer) }
    var success by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(remote.hostPeer) {
        val peer = remote.hostPeer
        if (peer != null && pairedWith == null && showRemote) {
            showRemote = false
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
    LaunchedEffect(showRemote) {
        if (showRemote) return@LaunchedEffect
        delay(100)
        runCatching { watchFocus.requestFocus() }
    }
    BackHandler(enabled = showRemote) { showRemote = false }

    if (showRemote) {
        FireTvRemotePage(hub = remote, onClose = { showRemote = false })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(LobbyBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 36.dp)
            .widthIn(max = 880.dp)
    ) {
        Text("Lobby", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose a provider, then start watching.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 18.sp
        )
        success?.let { message ->
            Spacer(Modifier.height(16.dp))
            Text(message, color = LobbyAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(28.dp))
        Text("Video provider", color = LobbyAccent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        controller.sources.forEach { source ->
            ProviderRow(
                source = source,
                selected = source.id == controller.selectedSource.id,
                onClick = { controller.chooseSource(source) },
                onRemove = if (source.isBuiltIn) null else ({ controller.remove(source) })
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Allow offline downloads for ${controller.selectedSource.name}",
                color = Color.White,
                modifier = Modifier.weight(1f),
                fontSize = 18.sp
            )
            Switch(
                checked = controller.selectedSource.allowsDownload,
                onCheckedChange = { controller.setAllowsDownload(it, controller.selectedSource) },
                colors = SwitchDefaults.colors(checkedTrackColor = LobbyAccent, checkedThumbColor = LobbyBackground)
            )
        }
        Spacer(Modifier.height(20.dp))
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
                Text("Allow offline downloads", color = Color.White, modifier = Modifier.weight(1f))
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
        Spacer(Modifier.height(28.dp))
        Text("Remote", color = LobbyAccent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            remoteStatus(remote),
            color = Color.White,
            fontSize = 18.sp
        )
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
            onClick = { showRemote = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LobbySurface, contentColor = Color.White)
        ) {
            Text("Set up remote", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onWatch,
            modifier = Modifier.fillMaxWidth().height(64.dp).focusRequester(watchFocus),
            colors = ButtonDefaults.buttonColors(containerColor = LobbyAccent, contentColor = LobbyBackground)
        ) {
            Text("Watch ${controller.selectedSource.name}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}

@Composable
private fun ProviderRow(
    source: MediaSource,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?
) {
    val interaction = remember(source.id) { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(shape)
                .border(
                    width = if (focused || selected) 3.dp else 1.dp,
                    color = if (focused || selected) LobbyAccent else Color.White.copy(alpha = 0.15f),
                    shape = shape
                )
                .background(if (selected) LobbyAccent.copy(alpha = 0.12f) else LobbySurface)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(source.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(source.hostLabel, color = Color.White.copy(alpha = 0.6f))
            }
            if (selected) {
                Text("Selected", color = LobbyAccent, fontWeight = FontWeight.Bold)
            }
        }
        if (onRemove != null) {
            Button(
                onClick = onRemove,
                colors = ButtonDefaults.buttonColors(containerColor = LobbySurface, contentColor = Color(0xFFFF8A80))
            ) {
                Text("Remove")
            }
        }
    }
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
