package com.karankaushik.lobby.remote

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom

data class DiscoveredPeer(
    val name: String,
    val address: InetAddress,
    val port: Int,
    val seenAt: Long
)

sealed interface RemoteLink {
    data object Idle : RemoteLink
    data class Connecting(val name: String) : RemoteLink
    data class Connected(val name: String) : RemoteLink
    data class Failed(val message: String) : RemoteLink
}

sealed interface RemoteCommand {
    data class Key(val name: String) : RemoteCommand
    data class Url(val url: String) : RemoteCommand

    companion object {
        val keys = setOf(
            "up", "down", "left", "right", "select",
            "back", "forward", "play", "home", "reload", "stop",
            "pageup", "pagedown", "seekback", "seekfwd", "mute"
        )

        fun fromKey(name: String): Key? = if (name in keys) Key(name) else null
    }
}

class RemoteHub(private val context: Context) {
    val deviceName: String = cleanName(readDeviceName(context))

    var hosting by mutableStateOf(false)
        private set
    var pin by mutableStateOf("")
        private set
    var hostPeer by mutableStateOf<String?>(null)
        private set
    var peers by mutableStateOf<List<DiscoveredPeer>>(emptyList())
        private set
    var scanning by mutableStateOf(false)
        private set
    var link by mutableStateOf<RemoteLink>(RemoteLink.Idle)
        private set
    var fault by mutableStateOf<String?>(null)
        private set

    private val _incoming = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 64)
    val incoming = _incoming.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val random = SecureRandom()
    private val gate = Any()

    @Volatile private var hostRunning = false
    @Volatile private var scanRunning = false
    @Volatile private var armedPin = ""
    @Volatile private var paired = false
    @Volatile private var pinFailures = 0
    @Volatile private var connectGeneration = 0

    private var udpSocket: DatagramSocket? = null
    private var tcpServer: ServerSocket? = null
    private var accepted: Socket? = null
    private var controllerSocket: Socket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var udpJob: Job? = null
    private var acceptJob: Job? = null
    private var announceJob: Job? = null
    private var discoverJob: Job? = null
    private var pruneJob: Job? = null
    private var clientReadJob: Job? = null

    fun startHosting() {
        if (hostRunning) return
        val fresh = newPin()
        hostRunning = true
        armedPin = fresh
        paired = false
        pinFailures = 0
        onMain {
            fault = null
            hosting = true
            pin = fresh
            hostPeer = null
        }
        scope.launch {
            val server = try {
                ServerSocket(0)
            } catch (_: Exception) {
                haltHost("Couldn't open a connection on this network.")
                return@launch
            }
            synchronized(gate) { tcpServer = server }
            try {
                ensureUdp()
            } catch (_: Exception) {
                haltHost("Couldn't look for nearby Lobby apps.")
                return@launch
            }
            if (!hostRunning) {
                synchronized(gate) { if (tcpServer === server) tcpServer = null }
                runCatching { server.close() }
                releaseUdpIfIdle()
                return@launch
            }
            acceptJob = launch { acceptLoop(server) }
            announceJob = launch { announceLoop(server.localPort) }
            ensurePrune()
        }
    }

    fun stopHosting() = haltHost(null)

    fun regeneratePin() {
        if (!hostRunning) return
        val fresh = newPin()
        val previous = synchronized(gate) {
            armedPin = fresh
            paired = false
            pinFailures = 0
            val old = accepted
            accepted = null
            old
        }
        previous?.let { socket ->
            scope.launch {
                runCatching { writeLine(socket, bye()) }
                runCatching { socket.close() }
            }
        }
        onMain {
            pin = fresh
            hostPeer = null
        }
    }

    fun startScanning() {
        if (scanRunning) return
        scanRunning = true
        onMain {
            scanning = true
            if (link is RemoteLink.Failed) link = RemoteLink.Idle
        }
        scope.launch {
            try {
                ensureUdp()
            } catch (_: Exception) {
                scanRunning = false
                onMain {
                    scanning = false
                    fault = "Couldn't look for nearby Lobby apps."
                }
                return@launch
            }
            discoverJob?.cancel()
            discoverJob = launch {
                while (scanRunning && isActive) {
                    sendDiscover()
                    delay(1500)
                }
            }
            ensurePrune()
        }
    }

    fun stopScanning() {
        scanRunning = false
        discoverJob?.cancel()
        onMain {
            scanning = false
            peers = emptyList()
        }
        releaseUdpIfIdle()
    }

    fun dismissError() {
        onMain {
            if (link is RemoteLink.Failed) link = RemoteLink.Idle
        }
    }

    fun connect(peer: DiscoveredPeer, enteredPin: String) {
        val digits = enteredPin.filter { it.isDigit() }
        if (digits.length != 4) {
            onMain { link = RemoteLink.Failed("Enter the 4-digit PIN.") }
            return
        }
        if (!peer.address.isLan()) {
            onMain { link = RemoteLink.Failed("That device isn't on this network.") }
            return
        }
        val generation = ++connectGeneration
        clientReadJob?.cancel()
        closeControllerSocket()
        onMain { link = RemoteLink.Connecting(peer.name) }
        scope.launch {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.connect(InetSocketAddress(peer.address, peer.port), 5000)
                if (generation != connectGeneration) {
                    socket.close()
                    return@launch
                }
                if (!socket.inetAddress.isLan()) {
                    socket.close()
                    onMain { link = RemoteLink.Failed("That device isn't on this network.") }
                    return@launch
                }
                socket.soTimeout = 6000
                writeLine(
                    socket,
                    JSONObject()
                        .put("v", 1)
                        .put("t", "pair")
                        .put("pin", digits)
                        .put("name", deviceName)
                        .toString()
                )
                val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                val reply = runCatching { JSONObject(reader.readLine() ?: "") }.getOrNull()
                if (generation != connectGeneration) {
                    socket.close()
                    return@launch
                }
                if (reply?.optInt("v") != 1 || reply.optString("t") != "paired") {
                    socket.close()
                    val message = when (reply?.optString("reason")) {
                        "busy" -> "That Lobby is already paired. Ask for a new PIN."
                        "pin" -> "That PIN was rejected."
                        else -> "Couldn't pair."
                    }
                    onMain { link = RemoteLink.Failed(message) }
                    return@launch
                }
                socket.soTimeout = 0
                controllerSocket = socket
                val name = cleanName(reply.optString("name").ifBlank { peer.name })
                onMain { link = RemoteLink.Connected(name) }
                val connected = socket
                clientReadJob = launch {
                    try {
                        while (isActive && controllerSocket === connected) {
                            val line = reader.readLine() ?: break
                            val json = runCatching { JSONObject(line) }.getOrNull() ?: continue
                            if (json.optString("t") == "bye") break
                        }
                    } catch (_: Exception) {
                    }
                    if (controllerSocket === connected) {
                        controllerSocket = null
                        runCatching { connected.close() }
                        onMain {
                            if (link is RemoteLink.Connected) {
                                link = RemoteLink.Failed("The other device disconnected.")
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                socket?.let { runCatching { it.close() } }
                if (generation == connectGeneration) {
                    onMain { link = RemoteLink.Failed("Couldn't reach that device.") }
                }
            }
        }
    }

    fun disconnect() {
        connectGeneration++
        clientReadJob?.cancel()
        val socket = controllerSocket
        controllerSocket = null
        if (socket != null) {
            scope.launch {
                runCatching { writeLine(socket, bye()) }
                runCatching { socket.close() }
            }
        }
        onMain { link = RemoteLink.Idle }
    }

    fun sendKey(name: String) {
        if (RemoteCommand.fromKey(name) == null) return
        sendJson(JSONObject().put("v", 1).put("t", "key").put("k", name))
    }

    fun sendUrl(url: String) {
        val normalized = RemoteUrl.normalize(url) ?: return
        sendJson(JSONObject().put("v", 1).put("t", "url").put("u", normalized))
    }

    private fun sendJson(json: JSONObject) {
        val socket = controllerSocket ?: return
        scope.launch {
            try {
                writeLine(socket, json.toString())
            } catch (_: Exception) {
                if (controllerSocket === socket) {
                    controllerSocket = null
                    runCatching { socket.close() }
                    onMain { link = RemoteLink.Failed("The other device disconnected.") }
                }
            }
        }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (hostRunning && !server.isClosed) {
            val socket = try {
                server.accept()
            } catch (_: Exception) {
                break
            }
            if (!hostRunning || !socket.inetAddress.isLan()) {
                runCatching { socket.close() }
                continue
            }
            scope.launch { greet(socket) }
        }
    }

    private fun greet(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.soTimeout = 5000
            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
            val json = runCatching { JSONObject(reader.readLine() ?: "") }.getOrNull()
            val entered = json?.optString("pin").orEmpty()
            val name = cleanName(json?.optString("name").orEmpty())
            if (json?.optInt("v") != 1 || json.optString("t") != "pair") {
                runCatching { socket.close() }
                return
            }
            val acceptedNow = synchronized(gate) {
                when {
                    !hostRunning -> "closed"
                    paired || accepted != null -> "busy"
                    !pinsMatch(entered, armedPin) -> "pin"
                    else -> {
                        paired = true
                        armedPin = ""
                        accepted = socket
                        "ok"
                    }
                }
            }
            if (acceptedNow != "ok") {
                if (acceptedNow == "pin") notePinFailure()
                if (acceptedNow == "pin" || acceptedNow == "busy") {
                    runCatching {
                        writeLine(
                            socket,
                            JSONObject().put("v", 1).put("t", "reject").put("reason", acceptedNow).toString()
                        )
                    }
                }
                runCatching { socket.close() }
                return
            }
            writeLine(
                socket,
                JSONObject().put("v", 1).put("t", "paired").put("name", deviceName).toString()
            )
            socket.soTimeout = 0
            onMain { hostPeer = name }
            try {
                while (hostRunning && synchronized(gate) { accepted === socket }) {
                    val line = reader.readLine() ?: break
                    if (line.length > 4096) continue
                    val command = runCatching { JSONObject(line) }.getOrNull() ?: continue
                    if (command.optInt("v") != 1) continue
                    when (command.optString("t")) {
                        "key" -> RemoteCommand.fromKey(command.optString("k"))?.let { _incoming.tryEmit(it) }
                        "url" -> {
                            val url = command.optString("u")
                            if (url.length <= 2048) _incoming.tryEmit(RemoteCommand.Url(url))
                        }
                        "bye" -> break
                    }
                }
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        } finally {
            val wasCurrent = synchronized(gate) {
                if (accepted === socket) {
                    accepted = null
                    paired = false
                    true
                } else {
                    false
                }
            }
            runCatching { socket.close() }
            if (wasCurrent) onMain { hostPeer = null }
        }
    }

    private fun notePinFailure() {
        val fresh = synchronized(gate) {
            pinFailures += 1
            if (pinFailures < 5) return
            pinFailures = 0
            val next = newPin()
            armedPin = next
            next
        }
        onMain { pin = fresh }
    }

    private suspend fun announceLoop(port: Int) {
        while (hostRunning) {
            sendHello(port)
            delay(1500)
        }
    }

    private fun ensureUdp() {
        synchronized(gate) {
            val current = udpSocket
            if (current != null && !current.isClosed) return
            val socket = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(DISCOVERY_PORT))
            }
            udpSocket = socket
            acquireMulticast()
            udpJob = scope.launch { readLoop(socket) }
        }
    }

    private fun readLoop(socket: DatagramSocket) {
        val buffer = ByteArray(512)
        while (!socket.isClosed) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: Exception) {
                break
            }
            val text = runCatching {
                String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
            }.getOrNull() ?: continue
            val json = runCatching { JSONObject(text) }.getOrNull() ?: continue
            if (json.optInt("v") != 1) continue
            if (!packet.address.isLan() || isSelfAddress(packet.address)) continue
            when (json.optString("t")) {
                "discover" -> if (hostRunning) replyHello(socket, packet.address, packet.port)
                "hello" -> {
                    val port = json.optInt("port")
                    if (port !in 1..65535 || isSelf(packet.address, port)) continue
                    notePeer(
                        DiscoveredPeer(
                            name = cleanName(json.optString("name")),
                            address = packet.address,
                            port = port,
                            seenAt = SystemClock.elapsedRealtime()
                        )
                    )
                }
            }
        }
    }

    private fun replyHello(socket: DatagramSocket, address: InetAddress, port: Int) {
        val tcpPort = synchronized(gate) { tcpServer?.localPort } ?: return
        val bytes = helloBytes(tcpPort)
        runCatching { socket.send(DatagramPacket(bytes, bytes.size, address, port)) }
    }

    private fun sendHello(port: Int) {
        sendDatagram(helloBytes(port))
    }

    private fun sendDiscover() {
        val bytes = JSONObject()
            .put("v", 1)
            .put("t", "discover")
            .put("name", deviceName)
            .toString()
            .toByteArray(Charsets.UTF_8)
        sendDatagram(bytes)
    }

    private fun helloBytes(port: Int): ByteArray = JSONObject()
        .put("v", 1)
        .put("t", "hello")
        .put("name", deviceName)
        .put("port", port)
        .toString()
        .toByteArray(Charsets.UTF_8)

    private fun sendDatagram(bytes: ByteArray) {
        val socket = synchronized(gate) { udpSocket } ?: return
        for (target in broadcastTargets()) {
            runCatching {
                socket.send(DatagramPacket(bytes, bytes.size, target, DISCOVERY_PORT))
            }
        }
    }

    private fun notePeer(peer: DiscoveredPeer) {
        onMain {
            if (!scanRunning) return@onMain
            val now = peer.seenAt
            peers = (peers.filterNot { it.address == peer.address && it.port == peer.port } + peer)
                .filter { now - it.seenAt < PEER_TTL_MS }
                .sortedBy { it.name.lowercase() }
        }
    }

    private fun ensurePrune() {
        if (pruneJob?.isActive == true) return
        pruneJob = scope.launch {
            while (isActive && (hostRunning || scanRunning)) {
                delay(2000)
                val now = SystemClock.elapsedRealtime()
                onMain {
                    peers = peers.filter { now - it.seenAt < PEER_TTL_MS }
                }
            }
        }
    }

    private fun haltHost(error: String?) {
        hostRunning = false
        armedPin = ""
        paired = false
        announceJob?.cancel()
        acceptJob?.cancel()
        val server: ServerSocket?
        val client: Socket?
        synchronized(gate) {
            server = tcpServer
            tcpServer = null
            client = accepted
            accepted = null
        }
        client?.let { socket ->
            runCatching { writeLine(socket, bye()) }
            runCatching { socket.close() }
        }
        runCatching { server?.close() }
        onMain {
            hosting = false
            pin = ""
            hostPeer = null
            if (error != null) fault = error
        }
        releaseUdpIfIdle()
    }

    private fun releaseUdpIfIdle() {
        if (hostRunning || scanRunning) return
        val socket = synchronized(gate) {
            val current = udpSocket
            udpSocket = null
            current
        }
        socket?.close()
        udpJob?.cancel()
        val lock = multicastLock
        multicastLock = null
        runCatching { if (lock?.isHeld == true) lock.release() }
    }

    private fun closeControllerSocket() {
        val socket = controllerSocket
        controllerSocket = null
        socket?.let { runCatching { it.close() } }
    }

    private fun acquireMulticast() {
        if (multicastLock?.isHeld == true) return
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        runCatching {
            multicastLock = wifi.createMulticastLock("lobby-remote").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun isSelf(address: InetAddress, port: Int): Boolean {
        val localPort = synchronized(gate) { tcpServer?.localPort } ?: return false
        return port == localPort && isSelfAddress(address)
    }

    private fun isSelfAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress) return true
        return localAddresses().any { it.hostAddress == address.hostAddress }
    }

    private fun localAddresses(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { nif ->
            nif.inetAddresses.toList()
        }
    }.getOrDefault(emptyList())

    private fun broadcastTargets(): List<InetAddress> {
        val found = mutableListOf<InetAddress>()
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().forEach { nif ->
                if (!nif.isUp || nif.isLoopback) return@forEach
                nif.interfaceAddresses.forEach { address ->
                    address.broadcast?.let { found.add(it) }
                }
            }
        }
        if (found.isEmpty()) {
            runCatching { found.add(InetAddress.getByName("255.255.255.255")) }
        }
        return found
    }

    private fun newPin(): String = buildString(4) {
        repeat(4) { append(random.nextInt(10)) }
    }

    private fun pinsMatch(entered: String, expected: String): Boolean {
        if (expected.length != 4 || entered.length != 4) return false
        return MessageDigest.isEqual(entered.toByteArray(), expected.toByteArray())
    }

    private fun bye(): String = JSONObject().put("v", 1).put("t", "bye").toString()

    private fun writeLine(socket: Socket, line: String) {
        synchronized(socket) {
            val out = socket.getOutputStream()
            out.write(line.toByteArray(Charsets.UTF_8))
            out.write('\n'.code)
            out.flush()
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    companion object {
        private const val DISCOVERY_PORT = 47831
        private const val PEER_TTL_MS = 7000L

        private fun cleanName(raw: String): String {
            val cleaned = raw.replace(Regex("[\\p{Cntrl}]"), "").trim().take(40)
            return cleaned.ifBlank { "Lobby" }
        }

        private fun readDeviceName(context: Context): String {
            val global = runCatching {
                Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            }.getOrNull()
            val secure = runCatching {
                Settings.Secure.getString(context.contentResolver, "bluetooth_name")
            }.getOrNull()
            return global?.takeIf { it.isNotBlank() }
                ?: secure?.takeIf { it.isNotBlank() }
                ?: Build.MODEL
                ?: "Lobby"
        }
    }
}

private fun InetAddress.isLan(): Boolean {
    if (this is Inet4Address && isAnyLocalAddress) return false
    return isSiteLocalAddress || isLinkLocalAddress || isLoopbackAddress
}
