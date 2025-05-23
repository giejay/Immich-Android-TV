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
import nl.giejay.android.tv.immich.shared.prefs.ALBUMS_SORTING
import nl.giejay.android.tv.immich.shared.prefs.LiveSharedPreferences
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ALBUMS
import timber.log.Timber

class AlbumFragment : VerticalCardGridFragment<Album>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LiveSharedPreferences(PreferenceManager.sharedPreference)
            .getString(PreferenceManager.get(ALBUMS_SORTING).toString(), ALBUMS_SORTING.defaultValue.toString(), true)
            .observe(viewLifecycleOwner) { _ ->
                resortItems()
            }
    }

    override fun sortItems(items: List<Album>): List<Album> {
        return if (selectionMode) {
            val sorted = items.sortedWith(PreferenceManager.get(ALBUMS_SORTING).sort)
            val selected = sorted.filter { PreferenceManager.get(SCREENSAVER_ALBUMS).contains(it.id) }
            val unselected = sorted.filter { !selected.contains(it) }
            selected + unselected
        } else {
            try {
                items.sortedWith(PreferenceManager.get(ALBUMS_SORTING).sort)
            } catch (e: IllegalArgumentException){
                Timber.e(e, "Could not sort using sorting order: " + PreferenceManager.get(ALBUMS_SORTING).toString())
                items
            }
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
        val currentAlbums = PreferenceManager.get(SCREENSAVER_ALBUMS)
        if (card.selected) {
            PreferenceManager.save(SCREENSAVER_ALBUMS, currentAlbums + card.id)
        } else {
            PreferenceManager.save(SCREENSAVER_ALBUMS, currentAlbums - card.id)
        }
    }

    override fun onItemClicked(card: Card) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToAlbumDetailsFragment(
                card.id,
                card.title
            )
        )
    }

    override fun getBackgroundPicture(it: Album): String? {
        return ApiUtil.getFileUrl(it.albumThumbnailAssetId, "IMAGE")
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
            ApiUtil.getFileUrl(a.albumThumbnailAssetId, "IMAGE"),
            if (selectionMode) PreferenceManager.get(SCREENSAVER_ALBUMS).contains(a.id) else false
        )
    }
}