package nl.giejay.android.tv.immich.api

import com.google.common.truth.Truth.assertThat
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class ApiAuthHeadersTest {
    @Test
    fun `basic auth needs both a user and a password`() {
        assertThat(ApiAuthHeaders.basicAuth("anna", "secret")).isEqualTo("Basic YW5uYTpzZWNyZXQ=")
        assertThat(ApiAuthHeaders.basicAuth("", "secret")).isNull()
        assertThat(ApiAuthHeaders.basicAuth("anna", "")).isNull()
        assertThat(ApiAuthHeaders.basicAuth("  ", "secret")).isNull()
    }

    @Test
    fun `the player headers carry the api key and the proxy login only when set`() {
        assertThat(ApiAuthHeaders.forPlayer("key", "", "")).containsExactly("x-api-key", "key")
        assertThat(ApiAuthHeaders.forPlayer("key", "anna", "secret"))
            .containsExactly("x-api-key", "key", "Authorization", "Basic YW5uYTpzZWNyZXQ=")
    }

    /** What the server (or the proxy in front of it) would see for a request made with the client. */
    private fun headersSeenBy(proxyUser: String, proxyPassword: String): okhttp3.Headers {
        var seen: okhttp3.Headers? = null
        val capture = Interceptor { chain ->
            seen = chain.request().headers
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{}".toResponseBody()).build()
        }
        val client = ApiClientFactory.getClient(false, "the-key", false, proxyUser, proxyPassword)
            .newBuilder().addInterceptor(capture).build()
        client.newCall(Request.Builder().url("https://immich.example.org/api/albums").build()).execute().close()
        return seen!!
    }

    @Test
    fun `the client sends the proxy login next to the api key`() {
        val headers = headersSeenBy("anna", "secret")

        assertThat(headers["x-api-key"]).isEqualTo("the-key")
        assertThat(headers["Authorization"]).isEqualTo("Basic YW5uYTpzZWNyZXQ=")
    }

    @Test
    fun `without a proxy login there is no Authorization header`() {
        val headers = headersSeenBy("", "")

        assertThat(headers["x-api-key"]).isEqualTo("the-key")
        assertThat(headers["Authorization"]).isNull()
    }
}
