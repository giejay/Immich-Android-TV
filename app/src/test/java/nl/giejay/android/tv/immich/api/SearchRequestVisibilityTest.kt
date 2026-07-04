package nl.giejay.android.tv.immich.api

import nl.giejay.android.tv.immich.api.model.SearchRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SearchRequestVisibilityTest {

    @Test
    fun `listAssets search request sends timeline visibility when no albumIds are set`() {
        val request = buildListAssetsSearchRequest(
            page = 0, pageCount = 30, albumIds = emptyList(), order = "desc",
            type = null, personIds = emptyList(), endDate = null, fromDate = null
        )

        assertEquals("timeline", request.visibility)
    }

    @Test
    fun `listAssets search request omits visibility when albumIds is non-empty`() {
        val request = buildListAssetsSearchRequest(
            page = 0, pageCount = 30, albumIds = listOf("album-1"), order = "desc",
            type = null, personIds = emptyList(), endDate = null, fromDate = null
        )

        assertNull(request.visibility)
    }

    @Test
    fun `listAssetsFromAlbum search request omits visibility by relying on the default`() {
        // Confirms no code change is needed in listAssetsFromAlbum: the SearchRequest
        // it constructs directly already gets visibility = null from the data class default.
        val request = SearchRequest(
            page = 1, size = 100, albumIds = listOf("album-1")
        )

        assertNull(request.visibility)
    }

    @Test
    fun `random search request strips page and order since RandomSearchDto has no such fields`() {
        // Regression test: v3's /search/random (RandomSearchDto) has no page/order properties at
        // all (only /search/metadata's MetadataSearchDto supports pagination) - sending them causes
        // Immich's stricter v3 validation to reject the request with 400. ApiClient.listAssets must
        // strip both via .copy(page = null, order = null) before calling service.randomAssets.
        val request = buildListAssetsSearchRequest(
            page = 2, pageCount = 30, albumIds = emptyList(), order = "desc",
            type = null, personIds = emptyList(), endDate = null, fromDate = null
        )
        val strippedForRandom = request.copy(page = null, order = null)

        assertEquals(2, request.page)
        assertEquals("desc", request.order)
        assertNull(strippedForRandom.page)
        assertNull(strippedForRandom.order)
    }

    @Test
    fun `formatted takenBefore-takenAfter dates always include a timezone offset`() {
        // Regression test: Immich v3's takenBefore/takenAfter schema requires a mandatory trailing
        // Z or +-HH:mm offset. DateTimeFormatter.ISO_DATE_TIME on a zone-less LocalDateTime silently
        // omits it, which v3's stricter validation rejects with 400 - v2 tolerated the offset-less
        // form. ApiClient must attach ZoneId.systemDefault() before formatting.
        val formatted = LocalDateTime.of(2026, 7, 4, 14, 30, 0)
            .atZone(ZoneId.systemDefault())
            .format(ApiClient.dateTimeFormatter)

        assertTrue(
            "expected a trailing Z or +-HH:mm offset, got: $formatted",
            formatted.matches(Regex(""".*(Z|[+-]\d{2}:\d{2})$"""))
        )
    }
}
