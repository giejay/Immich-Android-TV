package nl.giejay.android.tv.immich.album

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import arrow.core.Either
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment
import nl.giejay.android.tv.immich.shared.prefs.LiveSharedPreferences
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager

class AlbumFragment : VerticalCardGridFragment<Album>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LiveSharedPreferences(PreferenceManager.sharedPreference)
            .getString(PreferenceManager.KEY_ALBUMS_SORTING, PreferenceManager.albumsOrder().toString(), true)
            .observe(viewLifecycleOwner) { _ ->
                resortItems()
            }
    }

    override fun sortItems(items: List<Album>): List<Album> {
        return if (selectionMode) {
            val sorted = items.sortedWith(PreferenceManager.albumsOrder().sort)
            val selected = sorted.filter { PreferenceManager.getScreenSaverAlbums().contains(it.id) }
            val unselected = sorted.filter { !selected.contains(it) }
            selected + unselected
        } else {
            items.sortedWith(PreferenceManager.albumsOrder().sort)
        }
    }

    override suspend fun loadItems(
        apiClient: ApiClient,
        page: Int,
        pageCount: Int
    ): Either<String, List<Album>> {
        if (page == startPage) {
            // no pagination possible yet!
            return apiClient.listAlbums()
        }
        return Either.Right(emptyList())
    }

    override fun onItemSelected(card: Card, indexOf: Int) {
        val currentAlbums = PreferenceManager.getScreenSaverAlbums()
        if (card.selected) {
            PreferenceManager.saveScreenSaverAlbums(currentAlbums + card.id)
        } else {
            PreferenceManager.saveScreenSaverAlbums(currentAlbums - card.id)
        }
    }

    override fun onItemClicked(card: Card) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToAlbumDetailsFragment(
                card.id
            )
        )
    }

    override fun getBackgroundPicture(it: Album): String? {
        return ApiUtil.getFileUrl(it.albumThumbnailAssetId)
    }

    override fun openPopUpMenu() {
        findNavController().navigate(
            HomeFragmentDirections.actionGlobalToSettingsDialog("view")
        )
    }

    override fun createCard(a: Album): Card {
        return Card(
            a.albumName,
            a.description,
            a.id,
            ApiUtil.getThumbnailUrl(a.albumThumbnailAssetId, "thumbnail"),
            ApiUtil.getFileUrl(a.albumThumbnailAssetId),
            if (selectionMode) PreferenceManager.getScreenSaverAlbums().contains(a.id) else false
        )
    }
}