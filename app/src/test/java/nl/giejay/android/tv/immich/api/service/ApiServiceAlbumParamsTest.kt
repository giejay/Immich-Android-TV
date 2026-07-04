package nl.giejay.android.tv.immich.api.service

import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.http.Query

class ApiServiceAlbumParamsTest {

    @Test
    fun `listAlbums sends exactly one query param, assetId, and no shared filter`() {
        val listAlbumsMethod = ApiService::class.java.methods.first { it.name == "listAlbums" }

        val queryParamNames = listAlbumsMethod.parameterAnnotations
            .flatMap { it.toList() }
            .filterIsInstance<Query>()
            .map { it.value }

        assertEquals(listOf("assetId"), queryParamNames)
    }
}
