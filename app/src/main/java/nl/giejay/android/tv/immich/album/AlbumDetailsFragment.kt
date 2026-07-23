package nl.giejay.android.tv.immich.album

import androidx.navigation.fragment.findNavController
import arrow.core.Either
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.TimeBucketSummary
import nl.giejay.android.tv.immich.assets.GenericAssetFragment
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.prefs.ContentType
import nl.giejay.android.tv.immich.shared.prefs.EnumByTitlePref
import nl.giejay.android.tv.immich.shared.prefs.FILTER_CONTENT_TYPE_FOR_SPECIFIC_ALBUM
import nl.giejay.android.tv.immich.shared.prefs.PHOTOS_SORTING_FOR_SPECIFIC_ALBUM
import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder
import java.time.OffsetDateTime


class AlbumDetailsFragment : GenericAssetFragment() {
    lateinit var albumId: String
    lateinit var albumName: String

    override fun getFilterKey(): EnumByTitlePref<ContentType> {
        albumId = AlbumDetailsFragmentArgs.fromBundle(requireArguments()).albumId
        albumName = AlbumDetailsFragmentArgs.fromBundle(requireArguments()).albumName
        return FILTER_CONTENT_TYPE_FOR_SPECIFIC_ALBUM(albumId, albumName)
    }

    override fun getSortingKey(): EnumByTitlePref<PhotosOrder> {
        albumId = AlbumDetailsFragmentArgs.fromBundle(requireArguments()).albumId
        albumName = AlbumDetailsFragmentArgs.fromBundle(requireArguments()).albumName
        return PHOTOS_SORTING_FOR_SPECIFIC_ALBUM(albumId, albumName)
    }

    override suspend fun loadItems(apiClient: ApiClient, page: Int, pageCount: Int): Either<String, List<Asset>> {
        val order = if (currentSort == PhotosOrder.NEWEST_OLDEST) "desc" else "asc"
        return apiClient.listAssets(
            page = page,
            pageCount = pageCount,
            order = order,
            contentType = currentFilter,
            albumIds = listOf(albumId),
            endDate = if (order == "desc") initialJumpDate else null,
            fromDate = if (order == "asc") initialJumpDate else null
        ).map { it.map { a -> a.copy(albumName = albumName) } }
    }

    override suspend fun fetchBuckets(apiClient: ApiClient): Either<String, List<TimeBucketSummary>> {
        val order = if (currentSort == PhotosOrder.NEWEST_OLDEST) "desc" else "asc"
        return apiClient.getTimeBuckets(albumId = albumId, order = order)
    }

    override fun getItemDate(it: Asset): OffsetDateTime? {
        return it.exifInfo?.dateTimeOriginal?.toInstant()?.atZone(java.time.ZoneId.systemDefault())?.toOffsetDateTime()
            ?: it.fileCreatedAt?.toInstant()?.atZone(java.time.ZoneId.systemDefault())?.toOffsetDateTime()
            ?: it.fileModifiedAt?.toInstant()?.atZone(java.time.ZoneId.systemDefault())?.toOffsetDateTime()
    }

    override fun onItemSelected(card: Card, indexOf: Int) {
        // no use case yet
    }

    override fun openPopUpMenu() {
        findNavController().navigate(
            HomeFragmentDirections.actionGlobalToSettingsDialog("album_details", albumId, albumName)
        )
    }

    override fun setTitle(response: List<Asset>) {
        title = albumName
    }
}
