package nl.giejay.android.tv.immich.assets

import androidx.navigation.fragment.findNavController
import arrow.core.getOrElse
import com.zeuskartik.mediaslider.DisplayOptions
import com.zeuskartik.mediaslider.LoadMore
import com.zeuskartik.mediaslider.MediaSliderConfiguration
import com.zeuskartik.mediaslider.SliderItemViewHolder
import nl.giejay.android.tv.immich.album.AlbumDetailsFragmentDirections
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.shared.db.LocalStorage
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.util.toCard
import nl.giejay.android.tv.immich.shared.util.toSliderItems
import java.util.EnumSet

abstract class GenericAssetFragment : VerticalCardGridFragment<Asset>() {

    override fun sortItems(items: List<Asset>): List<Asset> {
        return items
    }

    override fun onItemSelected(card: Card, indexOf: Int) {
        // no use case yet
    }

    open fun showMediaCount(): Boolean {
        return false
    }

    override fun onItemClicked(card: Card) {
        val displayOptions: EnumSet<DisplayOptions> = EnumSet.noneOf(DisplayOptions::class.java)
        if (PreferenceManager.sliderShowDescription()) {
            displayOptions += DisplayOptions.TITLE
        }
        if (PreferenceManager.sliderShowCity()) {
            displayOptions += DisplayOptions.SUBTITLE
        }
        if (showMediaCount()) {
            displayOptions += DisplayOptions.MEDIA_COUNT
        }
        if (PreferenceManager.sliderShowDate()) {
            displayOptions += DisplayOptions.DATE
        }
        // todo find a better way to pass data to other fragment without using the Intent extras (possibly too large)
        val toSliderItems = assets.toSliderItems(keepOrder = true, mergePortrait = PreferenceManager.sliderMergePortraitPhotos())
        LocalStorage.mediaSliderItems = toSliderItems

        val loadMore = object: LoadMore {
            override suspend fun loadMore(): List<SliderItemViewHolder> {
                if(!allPagesLoaded){
                    return loadItems(apiClient, currentPage, FETCH_PAGE_COUNT).map { it.toSliderItems(true, PreferenceManager.sliderMergePortraitPhotos()) }
                        .getOrElse { emptyList() }
                }
                return emptyList()
            }
        }

        findNavController().navigate(
            AlbumDetailsFragmentDirections.actionToPhotoSlider(
                MediaSliderConfiguration(
                    displayOptions,
                    toSliderItems.indexOfFirst { it.ids().contains(card.id) },
                    PreferenceManager.sliderInterval(),
                    PreferenceManager.sliderOnlyUseThumbnails(),
                    true,
                    loadMore
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