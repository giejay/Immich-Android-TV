package nl.giejay.android.tv.immich.album

import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiUtil
import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import retrofit2.Response

class AlbumFragment : VerticalCardGridFragment<Album, List<Album>>() {

    override fun sortItems(items: List<Album>): List<Album> {
        return if (selectionMode) {
            items.sortedWith { b, a ->
                compareValuesBy(
                    a,
                    b,
                    { PreferenceManager.getScreenSaverAlbums().contains(it.id) },
                    { it.endDate })
            }
        } else {
            items.sortedByDescending { it.endDate }
        }
    }

    override fun loadItems(apiClient: ApiClient): Response<List<Album>> {
        return apiClient.listAlbums()
    }

    override fun onItemSelected(card: Card) {
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

    override fun getPicture(it: Album): String? {
        return ApiUtil.getThumbnailUrl(it.albumThumbnailAssetId)
    }

    override fun mapResponseToItems(response: List<Album>): List<Album> {
        return response
    }

    override fun createCard(a: Album): Card {
        return Card(
            a.albumName,
            a.description,
            a.id,
            ApiUtil.getThumbnailUrl(a.albumThumbnailAssetId),
            ApiUtil.getFileUrl(a.albumThumbnailAssetId),
            if (selectionMode) PreferenceManager.getScreenSaverAlbums().contains(a.id) else false
        )
    }
}