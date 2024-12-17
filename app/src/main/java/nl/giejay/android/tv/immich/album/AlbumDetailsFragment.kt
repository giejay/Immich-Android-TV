package nl.giejay.android.tv.immich.album

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import arrow.core.Either
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.AlbumDetails
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.assets.GenericAssetFragment
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.prefs.LivePreference
import nl.giejay.android.tv.immich.shared.prefs.LiveSharedPreferences
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager


class AlbumDetailsFragment : GenericAssetFragment() {
    private lateinit var albumId: String
    private lateinit var albumName: String
    private lateinit var livePref: LivePreference<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        albumId = AlbumDetailsFragmentArgs.fromBundle(requireArguments()).albumId
        albumName = AlbumDetailsFragmentArgs.fromBundle(requireArguments()).albumName
        super.onCreate(savedInstanceState)
        livePref = LiveSharedPreferences(PreferenceManager.sharedPreference)
            .getString(PreferenceManager.keyAlbumsSorting(albumId), PreferenceManager.photosOrder().toString(), true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        livePref.observe(viewLifecycleOwner) { _ ->
            resortItems()
        }
    }

    override fun sortItems(items: List<Asset>): List<Asset> {
        return items.sortedWith(PreferenceManager.getSortingForAlbum(albumId).sort)
    }

    override suspend fun loadItems(apiClient: ApiClient, page: Int, pageCount: Int): Either<String, List<Asset>> {
        if(page == startPage){
            // no pagination possible yet!
            return apiClient.listAssetsFromAlbum(albumId).map {
                it.assets
            }
        }
        return Either.Right(emptyList())
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