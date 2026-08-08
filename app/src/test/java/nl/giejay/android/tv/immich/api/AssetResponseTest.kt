package nl.giejay.android.tv.immich.api

import com.google.common.truth.Truth.assertThat
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.SearchAlbumResponseDto
import nl.giejay.android.tv.immich.api.model.SearchAssetResponseDto
import nl.giejay.android.tv.immich.api.model.SearchResponse
import org.junit.Test

class AssetResponseTest {
    @Test
    fun `search response with a next page maps to can load more`() {
        val response = searchResponse(nextPage = "2").toAssetResponse()

        assertThat(response.assets.map { it.id }).containsExactly("asset-1")
        assertThat(response.canLoadMore).isTrue()
    }

    @Test
    fun `search response without a next page maps to cannot load more`() {
        assertThat(searchResponse(nextPage = null).toAssetResponse().canLoadMore).isFalse()
    }

    @Test
    fun `random asset list maps to another sample being available`() {
        assertThat(emptyList<Asset>().toRandomAssetResponse().canLoadMore).isTrue()
    }

    private fun searchResponse(nextPage: String?) = SearchResponse(
        albums = SearchAlbumResponseDto(total = 0, count = 0, items = emptyList()),
        assets = SearchAssetResponseDto(
            total = 1,
            count = 1,
            items = listOf(asset("asset-1")),
            nextPage = nextPage
        )
    )

    private fun asset(id: String) = Asset(
        id = id,
        type = "IMAGE",
        deviceAssetId = null,
        exifInfo = null,
        fileModifiedAt = null,
        albumName = null,
        people = null,
        tags = null,
        originalPath = null,
        originalFileName = null
    )
}
