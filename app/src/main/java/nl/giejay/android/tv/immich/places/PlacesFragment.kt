package nl.giejay.android.tv.immich.places

import androidx.navigation.fragment.findNavController
import arrow.core.Either
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment

/** All places (cities) of the library as cards; a card opens the photos taken there. */
class PlacesFragment : VerticalCardGridFragment<Asset>() {
    override fun sortItems(items: List<Asset>): List<Asset> = items

    override suspend fun loadItems(apiClient: ApiClient, page: Int, pageCount: Int): Either<String, List<Asset>> =
        // the server offers no paging for places
        if (page == startPage) apiClient.listPlaces() else Either.Right(emptyList())

    override fun onItemSelected(card: Card, indexOf: Int) = Unit

    override fun onItemClicked(card: Card) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToFilteredAssetsFragment(FILTER_CITY, card.id, card.title),
        )
    }

    override fun getBackgroundPicture(it: Asset): String? = ApiUtil.getFileUrl(it.id, "IMAGE")

    override fun createCard(a: Asset): Card = Card(
        a.exifInfo?.city.orEmpty(),
        a.exifInfo?.country,
        a.exifInfo?.city.orEmpty(),
        ApiUtil.getThumbnailUrl(a.id, "thumbnail"),
        ApiUtil.getFileUrl(a.id, "IMAGE"),
        false,
    )

    companion object {
        const val FILTER_CITY = "city"
    }
}
