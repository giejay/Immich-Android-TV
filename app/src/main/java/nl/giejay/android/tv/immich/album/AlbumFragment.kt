package nl.giejay.android.tv.immich.album

import android.os.Bundle
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiUtil
import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment
import retrofit2.Response

class AlbumFragment : VerticalCardGridFragment<Album, List<Album>>() {

    override fun sortItems(items: List<Album>): List<Album> {
        return items.sortedByDescending { it.endDate }
    }

    override fun loadItems(apiClient: ApiClient): Response<List<Album>> {
        return apiClient.listAlbums()
    }

    override fun getPicture(it: Album): String? {
        return ApiUtil.getFileUrl(it.albumThumbnailAssetId)
    }

    override fun mapResponseToItems(response: List<Album>): List<Album> {
        return response
    }

    override fun createCard(a: Album): Card {
        return Card(a.albumName, a.description, a.id, ApiUtil.getThumbnailUrl(a.albumThumbnailAssetId),
            ApiUtil.getFileUrl(a.albumThumbnailAssetId))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onItemViewClickedListener = (OnItemViewClickedListener { _, item, _, _ ->
            val card: Card = item as Card
            findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToAlbumDetailsFragment(card.id))
        })
    }
}