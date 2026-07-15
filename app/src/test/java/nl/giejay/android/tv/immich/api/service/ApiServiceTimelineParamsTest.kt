package nl.giejay.android.tv.immich.api.service

import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.http.Query

class ApiServiceTimelineParamsTest {

    @Test
    fun `getTimeBuckets sends order visibility and withPartners query params`() {
        val method = ApiService::class.java.methods.first { it.name == "getTimeBuckets" }

        val queryParamNames = method.parameterAnnotations
            .flatMap { it.toList() }
            .filterIsInstance<Query>()
            .map { it.value }

        // VIS-02: visibility=timeline must remain available (defaults in the Kotlin signature)
        assertEquals(listOf("order", "visibility", "withPartners"), queryParamNames)
    }

    @Test
    fun `getTimeBucket sends timeBucket order visibility and withPartners query params`() {
        val method = ApiService::class.java.methods.first { it.name == "getTimeBucket" }

        val queryParamNames = method.parameterAnnotations
            .flatMap { it.toList() }
            .filterIsInstance<Query>()
            .map { it.value }

        assertEquals(listOf("timeBucket", "order", "visibility", "withPartners"), queryParamNames)
    }
}
