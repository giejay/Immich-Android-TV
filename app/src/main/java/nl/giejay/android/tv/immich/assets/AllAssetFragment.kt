package nl.giejay.android.tv.immich.assets

import androidx.navigation.fragment.findNavController
import arrow.core.Either
import com.zeuskartik.mediaslider.MediaSliderConfiguration
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.db.LocalStorage
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.util.toCard
import nl.giejay.android.tv.immich.shared.util.toSliderItems

class AllAssetFragment : VerticalCardGridFragment<Asset>() {
    override fun sortItems(items: List<Asset>): List<Asset> {
        return items
    }

    override suspend fun loadItems(
        apiClient: ApiClient,
        page: Int,
        pageCount: Int
    ): Either<String, List<Asset>> {
        return apiClient.listAssets(page, pageCount)
    }

    override fun onItemSelected(card: Card) {
        // no use case yet
    }

    override fun onItemClicked(card: Card) {
        LocalStorage.mediaSliderItems = assets.toSliderItems()
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToPhotoSlider(
                MediaSliderConfiguration(
                    PreferenceManager.sliderShowDescription(),
                    PreferenceManager.sliderShowMediaCount(),
                    false,
                    "",
                    "",
                    "",
                    adapter.indexOf(card),
                    PreferenceManager.sliderInterval(),
                    PreferenceManager.sliderOnlyUseThumbnails()
                )
            )
        )
    }

    override fun getBackgroundPicture(it: Asset): String? {
        return ApiUtil.getFileUrl(it.id)
    }

    override fun createCard(a: Asset): Card {
        return a.toCard()
    }
}