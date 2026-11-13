package nl.giejay.android.tv.immich.album

import androidx.navigation.fragment.findNavController
import arrow.core.Either
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.MAX_SEARCH_PAGE_SIZE
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.assets.GenericAssetFragment
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.prefs.BooleanPref
import nl.giejay.android.tv.immich.shared.prefs.ContentType
import nl.giejay.android.tv.immich.shared.prefs.EnumByTitlePref
import nl.giejay.android.tv.immich.shared.prefs.FILTER_CONTENT_TYPE_FOR_SPECIFIC_ALBUM
import nl.giejay.android.tv.immich.shared.prefs.FILTER_FAVORITES_ONLY_FOR_SPECIFIC_ALBUM
import nl.giejay.android.tv.immich.shared.prefs.PHOTOS_SORTING_FOR_SPECIFIC_ALBUM
import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder


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

    // Only used while sorting on RANDOM; recreated whenever sort or filter changes (see clearState)
    private var randomLoader: RandomAlbumAssetLoader? = null

    override fun getFavoritesKey(): BooleanPref {
        albumId = AlbumDetailsFragmentArgs.fromBundle(requireArguments()).albumId
        return FILTER_FAVORITES_ONLY_FOR_SPECIFIC_ALBUM(albumId)
    }

    override suspend fun loadItems(apiClient: ApiClient, page: Int, pageCount: Int): Either<String, List<Asset>> {
        if (currentSort == PhotosOrder.RANDOM) {
            return loadRandomItems(apiClient, pageCount)
        }
        return apiClient.listAssets(
            page = page,
            pageCount = pageCount,
            order = if (currentSort == PhotosOrder.NEWEST_OLDEST) "desc" else "asc",
            contentType = currentFilter,
            albumIds = listOf(albumId),
            isFavorite = isFavoriteFilter
        ).map { it.assets.map { a -> a.copy(albumName = albumName) } }
    }

    private suspend fun loadRandomItems(apiClient: ApiClient, pageCount: Int): Either<String, List<Asset>> {
        val loader = randomLoader ?: RandomAlbumAssetLoader(
            albumAssetCount = { apiClient.getAlbum(albumId).map { it.assetCount } },
            randomFetch = { size ->
                apiClient.listAssets(
                    page = 1,
                    pageCount = size,
                    random = true,
                    contentType = currentFilter,
                    albumIds = listOf(albumId)
                ).map { it.assets }
            },
            sweepFetch = {
                apiClient.listAssetsFromAlbum(listOf(albumId), currentFilter, MAX_SEARCH_PAGE_SIZE).map { it.assets }
            }
        ).also { randomLoader = it }
        return loader.next(pageCount).map { it.map { a -> a.copy(albumName = albumName) } }
    }

    override fun allPagesLoaded(items: List<Asset>): Boolean {
        return if (currentSort == PhotosOrder.RANDOM) {
            randomLoader?.exhausted ?: true
        } else {
            super.allPagesLoaded(items)
        }
    }

    override fun clearState() {
        super.clearState()
        randomLoader = null
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
