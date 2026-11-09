package nl.giejay.android.tv.immich.assets

import arrow.core.Either
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.shared.prefs.ContentType
import nl.giejay.android.tv.immich.shared.prefs.EnumByTitlePref
import nl.giejay.android.tv.immich.shared.prefs.FILTER_CONTENT_TYPE_FOR_SPECIFIC_ALBUM
import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder

/** All videos of the library as a grid; the content type filter of the other grids does not apply here. */
class VideosFragment : GenericAssetFragment() {
    // a filter of its own that nobody changes (stays on ALL), so the global "images only" does not hide the videos
    override fun getFilterKey(): EnumByTitlePref<ContentType> = FILTER_CONTENT_TYPE_FOR_SPECIFIC_ALBUM("videos", "videos")

    override suspend fun loadItems(apiClient: ApiClient, page: Int, pageCount: Int): Either<String, List<Asset>> =
        apiClient.listAssetsByFilter(
            page = page,
            pageCount = pageCount,
            contentType = ContentType.VIDEO,
            order = if (currentSort == PhotosOrder.NEWEST_OLDEST) "desc" else "asc",
        ).map { it.assets }

    override fun setTitle(response: List<Asset>) {
        title = getString(R.string.videos)
    }
}
