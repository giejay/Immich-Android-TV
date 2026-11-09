package nl.giejay.android.tv.immich.auth

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

/** Texts of the page the phone shows (already localized by the caller). */
data class LocalAuthTexts(
    val title: String,
    val hostLabel: String,
    val keyLabel: String,
    val submit: String,
    val success: String,
    val invalid: String,
    val language: String = "en",
)

/**
 * Tiny HTTP server that runs on the TV while the "sign in with your phone" screen is open. The phone
 * opens `http://<tv>:<port>/?t=<token>`, fills in server URL and API key and posts them back; no third
 * party is involved. The token is random, only valid for this server instance and used up by the first
 * accepted submission. Requests and bodies are size limited, and only a handful run at the same time.
 *
 * @param onAccepted called after the success page was written to the phone (safe moment to leave the screen).
 * @param onCredentials called with the submitted server URL and API key; returns true when they were
 *   accepted (the token is then used up), false to let the user retry.
 */
class LocalAuthServer(
    private val texts: LocalAuthTexts,
    private val onCredentials: (host: String, apiKey: String) -> Boolean,
    private val onAccepted: () -> Unit = {},
    val token: String = newToken(),
) {
    private var serverSocket: ServerSocket? = null
    private val lock = Any()
    private var stopped = false
    private var used = false
    private val openSockets = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Socket, Boolean>())

    // Bounded: at most MAX_CONNECTIONS workers and MAX_QUEUED waiting sockets, everything else is closed.
    private val executor = java.util.concurrent.ThreadPoolExecutor(
        MAX_CONNECTIONS, MAX_CONNECTIONS, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
        java.util.concurrent.ArrayBlockingQueue(MAX_QUEUED),
        { r -> Thread(r, "local-auth").apply { isDaemon = true } },
    )

    /** The port the server listens on (0 until [start] was called). */
    val port: Int get() = serverSocket?.localPort ?: 0

    /** Starts listening; does nothing when [stop] was called before, so a late start never leaks a socket. */
    fun start(port: Int = 0) {
        val socket = synchronized(lock) {
            if (stopped) return
            ServerSocket(port).also { serverSocket = it }
        }
        Thread({
            while (!socket.isClosed) {
                try {
                    val client = socket.accept()
                    client.soTimeout = SOCKET_TIMEOUT_MS
                    openSockets += client
                    try {
                        executor.execute { handle(client) }
                    } catch (_: java.util.concurrent.RejectedExecutionException) {
                        openSockets -= client
                        client.close()
                    }
                } catch (_: java.io.IOException) {
                    // closed by stop()
                }
            }
        }, "local-auth-accept").apply { isDaemon = true }.start()
    }

    /** Closes the listener and every accepted connection; credentials are never handed over afterwards. */
    fun stop() {
        synchronized(lock) {
            stopped = true
            try {
                serverSocket?.close()
            } catch (_: java.io.IOException) {
            }
        }
        executor.shutdownNow()
        openSockets.toList().forEach {
            try {
                it.close()
            } catch (_: java.io.IOException) {
            }
        }
        openSockets.clear()
    }

    private fun handle(socket: Socket) {
        socket.use {
            try {
                val request = readRequest(it.getInputStream()) ?: return respond(it, 400, "Bad request")
                val response = route(request)
                respond(it, response.first, response.second)
                if (response.first == 200 && request.method == "POST") onAccepted()
            } catch (_: IllegalArgumentException) {
                // malformed percent escapes
                try {
                    respond(it, 400, "Bad request")
                } catch (_: java.io.IOException) {
                }
            } catch (_: SocketTimeoutException) {
            } catch (_: java.io.IOException) {
            } finally {
                openSockets -= socket
            }
        }
    }

    internal fun route(request: HttpRequest): Pair<Int, String> {
        val tokenOk = tokenMatches(request.query["t"])
        return when {
            request.path == "/" && request.method == "GET" ->
                if (tokenOk && !synchronized(lock) { used || stopped }) 200 to formPage(null) else 403 to messagePage(texts.invalid)
            request.path == "/submit" && request.method == "POST" -> {
                if (!tokenOk) return 403 to messagePage(texts.invalid)
                val form = parseForm(request.body)
                val host = form["host"]?.trim().orEmpty()
                val key = form["key"]?.trim().orEmpty()
                // Check, hand-over and consuming the token happen under one lock: one submission at a time,
                // and never after stop().
                synchronized(lock) {
                    when {
                        used || stopped -> 403 to messagePage(texts.invalid)
                        host.isEmpty() || key.isEmpty() || !onCredentials(host, key) -> 400 to formPage(texts.invalid)
                        else -> {
                            used = true
                            200 to messagePage(texts.success)
                        }
                    }
                }
            }
            else -> 404 to messagePage("Not found")
        }
    }

    private fun tokenMatches(candidate: String?): Boolean =
        candidate != null && MessageDigest.isEqual(candidate.toByteArray(), token.toByteArray())

    private fun formPage(error: String?): String = page(
        buildString {
            append("<h1>${escape(texts.title)}</h1>")
            if (error != null) append("<p class=\"error\">${escape(error)}</p>")
            append("<form method=\"post\" action=\"/submit?t=${escape(token)}\">")
            append("<label>${escape(texts.hostLabel)}<input name=\"host\" type=\"url\" placeholder=\"https://immich.example.com\" autocapitalize=\"off\" autocorrect=\"off\" required></label>")
            append("<label>${escape(texts.keyLabel)}<input name=\"key\" type=\"password\" autocapitalize=\"off\" autocorrect=\"off\" required></label>")
            append("<button type=\"submit\">${escape(texts.submit)}</button></form>")
        },
    )

    private fun messagePage(message: String): String = page("<h1>${escape(texts.title)}</h1><p>${escape(message)}</p>")

    private fun page(body: String): String =
        "<!doctype html><html lang=\"${escape(texts.language)}\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>${escape(texts.title)}</title><style>" +
            "body{font-family:sans-serif;margin:24px;max-width:480px;background:#10131a;color:#eee}" +
            "label{display:block;margin:16px 0}input{display:block;width:100%;box-sizing:border-box;padding:12px;margin-top:6px;font-size:16px}" +
            "button{padding:12px 24px;font-size:16px}.error{color:#ff8a80}</style></head><body>$body</body></html>"

    internal data class HttpRequest(val method: String, val path: String, val query: Map<String, String>, val body: String)

    private fun readRequest(input: InputStream): HttpRequest? {
        val head = ByteArrayOutputStream()
        var matched = 0
        val end = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        while (matched < end.size) {
            val b = input.read()
            if (b < 0 || head.size() >= MAX_HEAD_BYTES) return null
            head.write(b)
            matched = if (b.toByte() == end[matched]) matched + 1 else if (b.toByte() == end[0]) 1 else 0
        }
        val lines = head.toString(Charsets.ISO_8859_1.name()).split("\r\n")
        val parts = lines[0].split(" ")
        if (parts.size < 2) return null
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = parseForm(target.substringAfter('?', ""))
        val length = lines.drop(1)
            .firstOrNull { it.startsWith("content-length:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
        if (length < 0 || length > MAX_BODY_BYTES) return null
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) return null
            read += n
        }
        return HttpRequest(parts[0], path, query, String(body, Charsets.UTF_8))
    }

    private fun respond(socket: Socket, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "Error"
        }
        val out = socket.getOutputStream()
        out.write(
            ("HTTP/1.1 $status $reason\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\n").toByteArray(),
        )
        out.write(bytes)
        out.flush()
    }

    companion object {
        const val MAX_CONNECTIONS = 4
        const val MAX_QUEUED = 8
        const val MAX_HEAD_BYTES = 8 * 1024
        const val MAX_BODY_BYTES = 4 * 1024
        const val SOCKET_TIMEOUT_MS = 5_000

        fun newToken(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun parseForm(text: String): Map<String, String> =
            text.split('&').filter { it.contains('=') }.associate {
                URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
            }

        fun escape(text: String): String =
            text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

        private val IGNORED_INTERFACES = listOf("tun", "tap", "ppp", "wg", "ipsec", "utun", "dummy", "rmnet", "lo")

        /**
         * Picks the address of the home network from (interface name, address) pairs: private IPv4 only, no
         * VPN/tunnel/mobile interfaces, Ethernet and Wi-Fi first.
         */
        fun pickLanAddress(candidates: List<Pair<String, InetAddress>>): InetAddress? =
            candidates
                .filter { (name, address) ->
                    address is Inet4Address && address.isSiteLocalAddress &&
                        IGNORED_INTERFACES.none { name.startsWith(it, ignoreCase = true) }
                }
                .sortedBy { (name, _) -> if (name.startsWith("eth") || name.startsWith("wlan") || name.startsWith("en")) 0 else 1 }
                .firstOrNull()?.second

        /** The address of this device in the home network, if any. */
        fun findLanAddress(): InetAddress? = try {
            pickLanAddress(
                NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { nic -> nic.inetAddresses.toList().map { nic.name to it } },
            )
        } catch (_: java.io.IOException) {
            null
        }
    }
}
