package nl.giejay.android.tv.immich.album

import androidx.navigation.fragment.findNavController
import arrow.core.Either
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiUtil
import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.util.Utils.optionalReversed

class AlbumFragment : VerticalCardGridFragment<Album>() {

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
            items.sortedWith(PreferenceManager.albumsOrder().sort.optionalReversed(PreferenceManager.reverseAlbumsOrder()))
        }
    }

    override suspend fun loadItems(apiClient: ApiClient, page: Int, pageCount: Int): Either<String, List<Album>> {
        if(page == startPage){
            // no pagination possible yet!
            return apiClient.listAlbums()
        }
        return Either.Right(emptyList())
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

    override fun getBackgroundPicture(it: Album): String? {
        return ApiUtil.getFileUrl(it.albumThumbnailAssetId)
    }

    override fun createCard(a: Album): Card {
        return Card(
            a.albumName,
            a.description,
            a.id,
            ApiUtil.getThumbnailUrl(a.albumThumbnailAssetId, "WEBP"),
            ApiUtil.getFileUrl(a.albumThumbnailAssetId),
            if (selectionMode) PreferenceManager.getScreenSaverAlbums().contains(a.id) else false
        )
    }
}