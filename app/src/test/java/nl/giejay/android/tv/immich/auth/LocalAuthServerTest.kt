package nl.giejay.android.tv.immich.auth

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL

class LocalAuthServerTest {
    private val received = mutableListOf<Pair<String, String>>()
    private var accept = true
    private lateinit var server: LocalAuthServer

    private val texts = LocalAuthTexts("Sign in", "Server", "Key", "Send", "Done", "Invalid <b>")

    @Before
    fun setUp() {
        server = LocalAuthServer(texts, { host, key ->
            received += host to key
            accept
        }, token = "tok123")
        server.start()
    }

    @After
    fun tearDown() = server.stop()

    private fun call(method: String, path: String, body: String? = null): Pair<Int, String> {
        val c = URL("http://127.0.0.1:${server.port}$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        if (body != null) {
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream).readBytes().toString(Charsets.UTF_8)
        return code to text
    }

    @Test
    fun formIsServedOnlyWithTheRightToken() {
        assertThat(call("GET", "/?t=tok123").first).isEqualTo(200)
        assertThat(call("GET", "/?t=tok123").second).contains("name=\"host\"")
        assertThat(call("GET", "/").first).isEqualTo(403)
        assertThat(call("GET", "/?t=nope").first).isEqualTo(403)
        assertThat(call("GET", "/other?t=tok123").first).isEqualTo(404)
    }

    @Test
    fun submitWithoutOrWrongTokenIsRejectedAndNothingIsHandedOver() {
        assertThat(call("POST", "/submit", "host=http%3A%2F%2Fa&key=k").first).isEqualTo(403)
        assertThat(call("POST", "/submit?t=bad", "host=http%3A%2F%2Fa&key=k").first).isEqualTo(403)
        assertThat(received).isEmpty()
    }

    @Test
    fun acceptedSubmissionIsDecodedAndUsesUpTheToken() {
        val result = call("POST", "/submit?t=tok123", "host=https%3A%2F%2Fimmich.lan%3A2283&key=a%2Bb+c")
        assertThat(result.first).isEqualTo(200)
        assertThat(result.second).contains("Done")
        assertThat(received).containsExactly("https://immich.lan:2283" to "a+b c")
        // second submission and the form itself are refused now
        assertThat(call("POST", "/submit?t=tok123", "host=https%3A%2F%2Fx&key=y").first).isEqualTo(403)
        assertThat(call("GET", "/?t=tok123").first).isEqualTo(403)
        assertThat(received).hasSize(1)
    }

    @Test
    fun rejectedSubmissionShowsTheFormAgainAndKeepsTheToken() {
        accept = false
        val first = call("POST", "/submit?t=tok123", "host=x&key=y")
        assertThat(first.first).isEqualTo(400)
        assertThat(first.second).contains("name=\"host\"")
        // user text is escaped
        assertThat(first.second).contains("Invalid &lt;b&gt;")
        accept = true
        assertThat(call("POST", "/submit?t=tok123", "host=https%3A%2F%2Fok&key=y").first).isEqualTo(200)
    }

    @Test
    fun emptyFieldsAreNotHandedOver() {
        assertThat(call("POST", "/submit?t=tok123", "host=&key=").first).isEqualTo(400)
        assertThat(received).isEmpty()
    }

    @Test
    fun oversizedBodyAndBrokenEscapesAreRefused() {
        val big = "host=" + "a".repeat(LocalAuthServer.MAX_BODY_BYTES + 1)
        Socket("127.0.0.1", server.port).use { s ->
            s.getOutputStream().write("POST /submit?t=tok123 HTTP/1.1\r\nContent-Length: ${big.length}\r\n\r\n$big".toByteArray())
            assertThat(String(s.getInputStream().readBytes())).contains("400")
        }
        assertThat(call("POST", "/submit?t=tok123", "host=%zz&key=y").first).isEqualTo(400)
        assertThat(received).isEmpty()
    }

    @Test
    fun tokensAreRandomHex() {
        val a = LocalAuthServer.newToken()
        assertThat(a).matches("[0-9a-f]{32}")
        assertThat(LocalAuthServer.newToken()).isNotEqualTo(a)
    }

    @Test
    fun stoppedServerRefusesConnections() {
        val port = server.port
        server.stop()
        var refused = false
        try {
            Socket("127.0.0.1", port).close()
        } catch (_: java.io.IOException) {
            refused = true
        }
        assertThat(refused).isTrue()
    }
}
