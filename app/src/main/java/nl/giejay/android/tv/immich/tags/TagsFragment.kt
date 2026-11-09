package nl.giejay.android.tv.immich.tags

import androidx.navigation.fragment.findNavController
import arrow.core.Either
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.TagInfo
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment

/** All tags of the library as cards (picture: a photo with the tag); a card opens the tagged photos. */
class TagsFragment : VerticalCardGridFragment<Pair<TagInfo, String?>>() {
    override fun sortItems(items: List<Pair<TagInfo, String?>>) = items

    override suspend fun loadItems(apiClient: ApiClient, page: Int, pageCount: Int): Either<String, List<Pair<TagInfo, String?>>> =
        if (page == startPage) apiClient.listTagsWithPictures() else Either.Right(emptyList())

    override fun onItemSelected(card: Card, indexOf: Int) = Unit

    override fun onItemClicked(card: Card) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToFilteredAssetsFragment(FILTER_TAG, card.id, card.title),
        )
    }

    override fun getBackgroundPicture(it: Pair<TagInfo, String?>): String? = it.second?.let { id -> ApiUtil.getFileUrl(id, "IMAGE") }

    override fun createCard(a: Pair<TagInfo, String?>): Card = Card(
        a.first.value,
        "",
        a.first.id,
        a.second?.let { id -> ApiUtil.getThumbnailUrl(id, "thumbnail") } ?: "tag",
        a.second?.let { id -> ApiUtil.getFileUrl(id, "IMAGE") },
        false,
    )

    companion object {
        const val FILTER_TAG = "tag"
    }
}
