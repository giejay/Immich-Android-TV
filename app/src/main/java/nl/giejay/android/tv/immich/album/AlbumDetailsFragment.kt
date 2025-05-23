package nl.giejay.android.tv.immich.album

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import arrow.core.Either
import arrow.core.flatMap
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.assets.GenericAssetFragment
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.prefs.LivePreference
import nl.giejay.android.tv.immich.shared.prefs.LiveSharedPreferences
import nl.giejay.android.tv.immich.shared.prefs.PHOTOS_SORTING
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager


class AlbumDetailsFragment : GenericAssetFragment() {
    private lateinit var albumId: String
    private lateinit var albumName: String
    private lateinit var livePref: LivePreference<String>
    private var pageToBucket: Map<Int, String>? = null
    private var currentSort: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        albumId = AlbumDetailsFragmentArgs.fromBundle(requireArguments()).albumId
        albumName = AlbumDetailsFragmentArgs.fromBundle(requireArguments()).albumName
        super.onCreate(savedInstanceState)
        livePref = LiveSharedPreferences(PreferenceManager.sharedPreference)
            .getString(PreferenceManager.keyAlbumsSorting(albumId), PHOTOS_SORTING.defaultValue.toString(), true)
        currentSort = livePref.value
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        livePref.observe(viewLifecycleOwner) { state ->
            if(state != currentSort){
                // need to refetch everything because of the timeline buckets approach
                clearState()
                pageToBucket = null
                currentSort = state
                fetchInitialItems()
            }
        }
    }

    override fun sortItems(items: List<Asset>): List<Asset> {
        return items.sortedWith(PreferenceManager.getSortingForAlbum(albumId).sort)
    }

    override suspend fun loadData(): Either<String, List<Asset>> {
        if (pageToBucket == null) {
            // initial call, fetch the buckets
            val listBuckets = apiClient.listBuckets(albumId, PreferenceManager.getSortingForAlbum(albumId))
            return listBuckets.map { list ->
                pageToBucket = list.associateBy({ list.indexOf(it) + 1 }, { it.timeBucket })
                return internalLoadData(emptyList())
            }
        } else {
            return internalLoadData(emptyList())
        }
    }

    private suspend fun internalLoadData(prevAssets: List<Asset>): Either<String, List<Asset>> {
        return loadItems(apiClient, currentPage, FETCH_PAGE_COUNT).flatMap {
            val combined = it + prevAssets
            if (combined.size <= FETCH_COUNT && !allPagesLoaded(it)) {
                // immediately load next bucket
                currentPage += 1
                internalLoadData(combined)
            } else {
                Either.Right(combined)
            }
        }
    }

    override suspend fun loadItems(apiClient: ApiClient, page: Int, pageCount: Int): Either<String, List<Asset>> {
        val bucketForPage = pageToBucket!![page]
        return if (bucketForPage != null) {
            apiClient.getAssetsForBucket(albumId, bucketForPage, PreferenceManager.getSortingForAlbum(albumId))
        } else {
            Either.Right(emptyList())
        }
    }

    override fun allPagesLoaded(items: List<Asset>): Boolean {
        return this.pageToBucket == null || !this.pageToBucket!!.contains(currentPage + 1)
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