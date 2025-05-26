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
import nl.giejay.android.tv.immich.shared.prefs.EXCLUDE_ASSETS_IN_ALBUM
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ALBUMS
import nl.giejay.android.tv.immich.shared.prefs.StringSetPref
import nl.giejay.android.tv.immich.shared.prefs.ViewPrefScreen
import timber.log.Timber

enum class SelectionType {
    SET_SCREENSAVER,
    EXCLUDED_ALBUMS,
}

class AlbumFragment : VerticalCardGridFragment<Album>() {
    private lateinit var selectionTypeKey: StringSetPref
    private val selectionType: SelectionType
        get() = arguments?.getString("selectionType")?.let { SelectionType.valueOf(it) } ?: SelectionType.SET_SCREENSAVER

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectionTypeKey = when (selectionType) {
            SelectionType.SET_SCREENSAVER -> {
                SCREENSAVER_ALBUMS
            }

            SelectionType.EXCLUDED_ALBUMS -> {
                EXCLUDE_ASSETS_IN_ALBUM
            }
        }
        PreferenceManager.subscribe(ALBUMS_SORTING) {
            resortItems()
        }
    }

    override fun sortItems(items: List<Album>): List<Album> {
        if(selectionMode) {
            val sorted = items.sortedWith(PreferenceManager.get(ALBUMS_SORTING).sort)
            val selected = sorted.filter { PreferenceManager.get(selectionTypeKey).contains(it.id) }
            val unselected = sorted.filter { !selected.contains(it) }
            return selected + unselected
        } else {
            try {
                return items.sortedWith(PreferenceManager.get(ALBUMS_SORTING).sort)
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Could not sort using sorting order: " + PreferenceManager.get(ALBUMS_SORTING).toString())
                return items
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
        val currentAlbums = PreferenceManager.get(selectionTypeKey)
        if (card.selected) {
            PreferenceManager.save(selectionTypeKey, currentAlbums + card.id)
        } else {
            PreferenceManager.save(selectionTypeKey, currentAlbums - card.id)
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
            if (selectionMode) PreferenceManager.get(selectionTypeKey).contains(a.id) else false
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if(this.selectionMode){
            findNavController().navigate(
                HomeFragmentDirections.actionGlobalToSettingsDialog(ViewPrefScreen.key)
            )
        }
    }
}