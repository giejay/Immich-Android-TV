package nl.giejay.android.tv.immich.api.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class ImmichErrorResponseTest {

    private val gson = Gson()

    @Test
    fun `parses string message without throwing`() {
        val json = """{"message": "Invalid API key", "statusCode": 403}"""
        val parsed = gson.fromJson(json, ImmichErrorResponse::class.java)
        assertEquals("Invalid API key", parsed.message?.toDisplayMessage())
    }

    @Test
    fun `parses array message without throwing`() {
        val json = """{
            "message": "Validation failed",
            "errors": [
                { "code": "invalid_format", "path": ["ids", 0], "message": "Invalid UUID" }
            ]
        }"""
        val parsed = gson.fromJson(json, ImmichErrorResponse::class.java)
        assertEquals("Validation failed", parsed.message?.toDisplayMessage())
        assertEquals("Invalid UUID", parsed.errors?.first()?.message?.toDisplayMessage())
    }

    @Test
    fun `missing fields never throw`() {
        val json = """{}"""
        val parsed = gson.fromJson(json, ImmichErrorResponse::class.java)
        assertEquals("Unknown error", parsed.message?.toDisplayMessage() ?: "Unknown error")
    }

    @Test
    fun `malformed json falls back to null, not exception`() {
        val json = """not json at all"""
        val result = try {
            gson.fromJson(json, ImmichErrorResponse::class.java)
        } catch (e: Exception) {
            null
        }
        assertEquals(null, result)
    }
}
