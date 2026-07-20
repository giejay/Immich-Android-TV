package nl.giejay.android.tv.immich.api.service

import nl.giejay.android.tv.immich.api.resolveWithPartners
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // Regression test for issue #157: partner-shared photos never showed up in the Timeline
    // because ApiClient never passed withPartners, even though ApiService already declared the
    // query param. resolveWithPartners is what ApiClient.getTimeBuckets/getTimeBucket now call,
    // wired to the SHOW_PARTNER_PHOTOS_IN_TIMELINE preference.
    @Test
    fun `resolveWithPartners sends true only when the preference is enabled`() {
        assertEquals(true, resolveWithPartners(showPartnerPhotosInTimeline = true))
        assertNull(resolveWithPartners(showPartnerPhotosInTimeline = false))
    }
}
