package nl.giejay.android.tv.immich.api.util

import kotlinx.coroutines.runBlocking
import okhttp3.Headers.Companion.headersOf
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import retrofit2.Response

class ApiUtilTest {

    @Test
    fun `403 with required permission all body returns exact existing message and reads body once`(): Unit = runBlocking {
        val mockBody = mock(ResponseBody::class.java)
        `when`(mockBody.string()).thenReturn("required permission: all")

        val response = mock(Response::class.java) as Response<String>
        `when`(response.code()).thenReturn(403)
        `when`(response.errorBody()).thenReturn(mockBody)
        `when`(response.headers()).thenReturn(headersOf())

        val result = ApiUtil.executeAPICall(200) { response }

        assertTrue(result.isLeft())
        assertEquals(
            "API key is missing the permission \"all\". Please adapt your permissions in the Immich web interface.",
            (result as arrow.core.Either.Left).value
        )
        verify(mockBody, times(1)).string()
    }

    @Test
    fun `403 without required permission all body does not throw and reads body once`(): Unit = runBlocking {
        val mockBody = mock(ResponseBody::class.java)
        `when`(mockBody.string()).thenReturn("some other error body")

        val response = mock(Response::class.java) as Response<String>
        `when`(response.code()).thenReturn(403)
        `when`(response.errorBody()).thenReturn(mockBody)
        `when`(response.headers()).thenReturn(headersOf())

        val result = ApiUtil.executeAPICall(200) { response }

        assertTrue(result.isLeft())
        assertTrue((result as arrow.core.Either.Left).value.contains("Invalid status (403) returned by Immich Server: some other error body"))
        verify(mockBody, times(1)).string()
    }
}
