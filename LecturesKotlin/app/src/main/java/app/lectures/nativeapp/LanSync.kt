package app.lectures.nativeapp

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class Peer(val id: String, val name: String, val ip: String, val port: Int)

class LanSync(context: Context, private val store: DataStore) {
    companion object {
        private const val TCP_PORT = 47831
        private const val UDP_PORT = 47832
        private const val DISCOVER = "LECTURES_DISCOVER_V2"
        private const val PROTOCOL = "LECTURES_SYNC_V2"
        private const val MAX_BODY = 200 * 1024 * 1024
    }
    private val appContext = context.applicationContext
    private val id = UUID.randomUUID().toString()
    private val running = AtomicBoolean(false)
    private val accepting = AtomicBoolean(false)
    private var server: ServerSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("lectures-multicast").apply { setReferenceCounted(false); acquire() }
        Thread(::serveTcp, "lectures-sync-tcp").start()
        Thread(::serveDiscovery, "lectures-sync-udp").start()
    }
    fun setAccepting(value: Boolean) { accepting.set(value) }
    fun isAccepting(): Boolean = accepting.get()

    fun stop() {
        running.set(false)
        runCatching { server?.close() }
        runCatching { multicastLock?.release() }
        multicastLock = null
    }

    private fun serveTcp() {
        try {
            ServerSocket(TCP_PORT).use { listener ->
                server = listener
                while (running.get()) {
                    val socket = runCatching { listener.accept() }.getOrNull() ?: break
                    Thread { handle(socket) }.start()
                }
            }
        } catch (_: Exception) { running.set(false) }
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            runCatching {
                client.soTimeout = 30_000
                val input = BufferedInputStream(client.getInputStream())
                val header = ByteArrayOutputStream()
                val end = byteArrayOf(13, 10, 13, 10)
                while (header.size() < 16_384) {
                    val next = input.read()
                    if (next < 0) break
                    header.write(next)
                    val bytes = header.toByteArray()
                    if (bytes.size >= 4 && bytes.takeLast(4).toByteArray().contentEquals(end)) break
                }
                val headerText = header.toString(Charsets.US_ASCII.name())
                val length = headerText.lineSequence().firstNotNullOfOrNull { line ->
                    if (line.startsWith("Content-Length:", true)) line.substringAfter(':').trim().toIntOrNull() else null
                } ?: 0
                require(length in 1..MAX_BODY) { "Размер данных вне допустимого диапазона" }
                val body = ByteArray(length)
                var offset = 0
                while (offset < length) {
                    val count = input.read(body, offset, length - offset)
                    if (count < 0) break
                    offset += count
                }
                require(offset == length) { "Передача оборвалась" }
                val request = JSONObject(body.toString(Charsets.UTF_8))
                require(request.optString("protocol") == PROTOCOL) { "Неизвестный протокол" }
                require(accepting.get()) { "На этом устройстве не включён приём данных" }
                runBlocking { store.replaceFromJson(request.getString("payload")) }
                respond(client, "200 OK", "OK")
            }.onFailure { error -> runCatching { respond(client, "400 Bad Request", error.message ?: "Ошибка") } }
        }
    }

    private fun respond(socket: Socket, status: String, message: String) {
        val body = message.toByteArray(Charsets.UTF_8)
        socket.getOutputStream().apply {
            write("HTTP/1.1 $status\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(body); flush()
        }
    }

    private fun serveDiscovery() {
        runCatching {
            DatagramSocket(UDP_PORT, InetAddress.getByName("0.0.0.0")).use { socket ->
                socket.broadcast = true
                socket.soTimeout = 1_000
                val buffer = ByteArray(1024)
                while (running.get()) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try { socket.receive(packet) }
                    catch (_: java.net.SocketTimeoutException) { continue }
                    val message = packet.data.copyOfRange(0, packet.length).toString(Charsets.UTF_8)
                    val requester = runCatching { JSONObject(message).optString("id") }.getOrDefault("")
                    val type = runCatching { JSONObject(message).optString("type") }.getOrDefault(message.trim())
                    if (type == DISCOVER && requester != id) {
                        val reply = JSONObject().put("id", id).put("name", deviceName()).put("port", TCP_PORT).toString().toByteArray()
                        socket.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                    }
                }
            }
        }
    }

    suspend fun discover(): List<Peer> = withContext(Dispatchers.IO) {
        val peers = linkedMapOf<String, Peer>()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 170
            val message = JSONObject().put("type", DISCOVER).put("id", id).toString().toByteArray()
            socket.send(DatagramPacket(message, message.size, InetAddress.getByName("255.255.255.255"), UDP_PORT))
            val until = System.currentTimeMillis() + 1_100
            val buffer = ByteArray(1024)
            while (System.currentTimeMillis() < until) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val response = JSONObject(packet.data.copyOfRange(0, packet.length).toString(Charsets.UTF_8))
                    val peerId = response.optString("id")
                    if (peerId.isNotBlank() && peerId != id) peers.putIfAbsent(peerId, Peer(peerId, response.optString("name", "Устройство"), packet.address.hostAddress ?: "", response.optInt("port", TCP_PORT)))
                } catch (_: java.net.SocketTimeoutException) { }
            }
        }
        peers.values.toList()
    }

    suspend fun send(peer: Peer, data: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("protocol", PROTOCOL).put("payload", data).toString().toByteArray(Charsets.UTF_8)
        require(body.size <= MAX_BODY) { "Резервная копия больше 200 МБ" }
        Socket().use { socket ->
            socket.connect(java.net.InetSocketAddress(peer.ip, peer.port), 5000)
            socket.soTimeout = 30_000
            val out = socket.getOutputStream()
            out.write("POST /sync HTTP/1.1\r\nHost: ${peer.ip}\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(body); out.flush()
            val response = socket.getInputStream().bufferedReader().readText()
            check(response.startsWith("HTTP/1.1 200")) { response.substringAfter("\r\n\r\n", "Ошибка принимающего устройства") }
        }
    }

    private fun deviceName(): String = android.os.Build.MODEL?.takeIf(String::isNotBlank) ?: "Android-устройство"
}
