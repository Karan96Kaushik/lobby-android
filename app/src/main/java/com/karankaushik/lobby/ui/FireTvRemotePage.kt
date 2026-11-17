package com.karankaushik.lobby.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.karankaushik.lobby.remote.DiscoveredPeer
import com.karankaushik.lobby.remote.RemoteHub
import com.karankaushik.lobby.remote.RemoteLink

private enum class RemotePage { MENU, ALLOW, PICK, PIN, LINKED }

private class RemoteRow(val label: String, val onPress: () -> Unit)

@Composable
fun FireTvRemotePage(hub: RemoteHub, onClose: () -> Unit) {
    var page by remember { mutableStateOf(if (hub.hosting) RemotePage.ALLOW else RemotePage.MENU) }
    var index by remember { mutableIntStateOf(0) }
    var peer by remember { mutableStateOf<DiscoveredPeer?>(null) }
    var digits by remember { mutableStateOf(listOf(0, 0, 0, 0)) }
    val cursor = remember { intArrayOf(0) }
    val rows = remember(page, hub.hosting, hub.pin, hub.hostPeer, hub.pairedDevices, hub.peers, hub.link, hub.fault, peer, digits) {
        remoteRows(hub, page, peer, digits,         onPage = {
            cursor[0] = 0
            index = 0
            page = it
        }, onDigits = { digits = it }, onPeer = {
            peer = it
            digits = listOf(0, 0, 0, 0)
            cursor[0] = 0
            index = 0
            page = RemotePage.PIN
        }, onClose = onClose)
    }
    val selected = index.coerceIn(0, rows.lastIndex.coerceAtLeast(0))
    val rowsState = rememberUpdatedState(rows)

    LaunchedEffect(page) {
        if (page == RemotePage.PICK) hub.startScanning() else hub.stopScanning()
    }
    LaunchedEffect(hub.link) {
        if (hub.link is RemoteLink.Connected && page != RemotePage.LINKED) {
            cursor[0] = 0
            index = 0
            page = RemotePage.LINKED
        }
    }
    DisposableEffect(Unit) {
        FireTvKeySink.handler = handler@{ event ->
            val current = rowsState.value
            if (current.isEmpty()) return@handler false
            val handled = event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                event.keyCode == KeyEvent.KEYCODE_ENTER ||
                event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                event.keyCode == KeyEvent.KEYCODE_BUTTON_A
            if (!handled) return@handler false
            if (event.action != KeyEvent.ACTION_DOWN) return@handler true
            val at = cursor[0].coerceIn(0, current.lastIndex)
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    cursor[0] = (at - 1).coerceAtLeast(0)
                    index = cursor[0]
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    cursor[0] = (at + 1).coerceAtMost(current.lastIndex)
                    index = cursor[0]
                }
                else -> current.getOrNull(at)?.onPress?.invoke()
            }
            true
        }
        onDispose {
            FireTvKeySink.handler = null
            hub.stopScanning()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(LobbyBackground)
            .padding(horizontal = 72.dp, vertical = 48.dp)
    ) {
        Text(pageTitle(page), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(pageSubtitle(hub, page, peer), color = Color.White.copy(alpha = 0.72f), fontSize = 18.sp)
        if (page == RemotePage.ALLOW && hub.hosting) {
            Spacer(Modifier.height(16.dp))
            Text(
                hub.pin,
                color = LobbyAccent,
                fontSize = 72.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        hub.fault?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Color(0xFFFF8A80), fontSize = 18.sp)
        }
        if (hub.link is RemoteLink.Failed) {
            Spacer(Modifier.height(12.dp))
            Text((hub.link as RemoteLink.Failed).message, color = Color(0xFFFF8A80), fontSize = 18.sp)
        }
        Spacer(Modifier.height(24.dp))
        rows.forEachIndexed { rowIndex, row ->
            val on = rowIndex == selected
            Text(
                row.label,
                color = if (on) LobbyBackground else Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) LobbyAccent else LobbySurface)
                    .padding(horizontal = 24.dp, vertical = 18.dp)
            )
        }
    }
}

private fun remoteRows(
    hub: RemoteHub,
    page: RemotePage,
    peer: DiscoveredPeer?,
    digits: List<Int>,
    onPage: (RemotePage) -> Unit,
    onDigits: (List<Int>) -> Unit,
    onPeer: (DiscoveredPeer) -> Unit,
    onClose: () -> Unit
): List<RemoteRow> = when (page) {
    RemotePage.MENU -> buildList {
        val paired = hub.link as? RemoteLink.Paired
        if (paired != null) {
            add(RemoteRow(if (paired.controller == null) "Control ${paired.name}" else "Take control") {
                hub.takeControl()
            })
            add(RemoteRow("Unpair") { hub.unpair() })
        }
        add(RemoteRow("Allow control") {
            hub.startHosting()
            onPage(RemotePage.ALLOW)
        })
        add(RemoteRow("Control another Lobby") { onPage(RemotePage.PICK) })
        add(RemoteRow("Close") { onClose() })
    }
    RemotePage.ALLOW -> buildList {
        hub.pairedDevices.forEach { device ->
            if (device.controlling) {
                add(RemoteRow("Release ${device.name}") { hub.releaseControl() })
            }
            add(RemoteRow("Remove ${device.name}") { hub.unpairDevice(device.id) })
        }
        add(RemoteRow(if (hub.hosting) "New PIN" else "Show PIN") {
            if (hub.hosting) hub.regeneratePin() else hub.startHosting()
        })
        add(RemoteRow("Stop") {
            hub.stopHosting()
            onPage(RemotePage.MENU)
        })
        add(RemoteRow("Back") { onPage(RemotePage.MENU) })
    }
    RemotePage.PICK -> buildList {
        hub.peers.forEach { found ->
            add(RemoteRow(found.name) { onPeer(found) })
        }
        if (hub.link is RemoteLink.Failed) {
            add(RemoteRow("Try again") { hub.dismissError() })
        }
        add(RemoteRow("Back") { onPage(RemotePage.MENU) })
    }
    RemotePage.PIN -> buildList {
        digits.forEachIndexed { digitIndex, value ->
            add(RemoteRow("Digit ${digitIndex + 1}: $value") {
                onDigits(digits.mapIndexed { index, digit -> if (index == digitIndex) (digit + 1) % 10 else digit })
            })
        }
        add(RemoteRow("Pair") {
            val chosen = peer ?: return@RemoteRow
            hub.connect(chosen, digits.joinToString(""), fresh = true)
        })
        add(RemoteRow("Back") {
            hub.disconnect()
            onPage(RemotePage.PICK)
        })
    }
    RemotePage.LINKED -> listOf(
        RemoteRow("Release control") {
            hub.disconnect()
            onPage(RemotePage.MENU)
        },
        RemoteRow("Unpair") {
            hub.unpair()
            onPage(RemotePage.MENU)
        },
        RemoteRow("Close") { onClose() }
    )
}

private fun pageTitle(page: RemotePage): String = when (page) {
    RemotePage.MENU -> "Remote"
    RemotePage.ALLOW -> "Allow control"
    RemotePage.PICK -> "Nearby Lobby apps"
    RemotePage.PIN -> "Enter PIN"
    RemotePage.LINKED -> "Connected"
}

private fun pageSubtitle(hub: RemoteHub, page: RemotePage, peer: DiscoveredPeer?): String = when (page) {
    RemotePage.MENU -> if (hub.link is RemoteLink.Paired) {
        val paired = hub.link as RemoteLink.Paired
        if (paired.controller != null) "Paired with ${paired.name}. ${paired.controller} is controlling."
        else "Paired with ${paired.name}. No one is controlling."
    } else {
        "Press up and down, then select."
    }
    RemotePage.ALLOW -> when {
        !hub.hosting -> "This TV will show a PIN."
        hub.hostPeer != null -> "${hub.hostPeer} is controlling. Other phones can pair, but only one controls."
        hub.pairedDevices.isNotEmpty() -> "Phones are paired. None are controlling."
        else -> "Enter this PIN on a phone. Several can pair. Only one controls."
    }
    RemotePage.PICK -> if (hub.peers.isEmpty()) {
        "Looking on this Wi-Fi. On the other device, choose Allow control."
    } else {
        "Select a device."
    }
    RemotePage.PIN -> if (hub.link is RemoteLink.Connecting) {
        "Pairing with ${peer?.name ?: "Lobby"}…"
    } else {
        "Select a digit to change it, then Pair."
    }
    RemotePage.LINKED -> {
        val name = (hub.link as? RemoteLink.Connected)?.name ?: peer?.name ?: "Lobby"
        "Controlling $name."
    }
}
