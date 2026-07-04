package nl.giejay.android.tv.immich.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
