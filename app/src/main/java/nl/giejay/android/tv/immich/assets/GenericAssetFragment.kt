package nl.giejay.android.tv.immich.assets

import androidx.navigation.fragment.findNavController
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.android.tv.immich.album.AlbumDetailsFragmentDirections
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ANIMATE_ASSET_SLIDE
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ANIMATION_SPEED
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_GLIDE_TRANSFORMATION
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_INTERVAL
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_HEIGHT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_WIDTH
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MERGE_PORTRAIT_PHOTOS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ONLY_USE_THUMBNAILS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_SHOW_CITY
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_SHOW_DATE
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_SHOW_DESCRIPTION
import nl.giejay.android.tv.immich.shared.util.toCard
import nl.giejay.android.tv.immich.shared.util.toSliderItems
import nl.giejay.mediaslider.util.LoadMore
import nl.giejay.mediaslider.config.MediaSliderConfiguration
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
        val toSliderItems = assets.toSliderItems(keepOrder = true, mergePortrait = PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS))
        val loadMore: LoadMore = suspend {
            loadAssets().toSliderItems(true, PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS))
        }

        findNavController().navigate(
            AlbumDetailsFragmentDirections.actionToPhotoSlider(
                MediaSliderConfiguration(
                    toSliderItems.indexOfFirst { it.ids().contains(card.id) },
                    PreferenceManager.get(SLIDER_INTERVAL),
                    PreferenceManager.get(SLIDER_ONLY_USE_THUMBNAILS),
                    isVideoSoundEnable = true,
                    toSliderItems,
                    loadMore,
                    { item -> manualUpdatePosition(this.assets.indexOfFirst { item.ids().contains(it.id) }) },
                    animationSpeedMillis = PreferenceManager.get(SLIDER_ANIMATION_SPEED),
                    maxCutOffHeight = PreferenceManager.get(SLIDER_MAX_CUT_OFF_HEIGHT),
                    maxCutOffWidth = PreferenceManager.get(SLIDER_MAX_CUT_OFF_WIDTH),
                    transformation = PreferenceManager.get(SLIDER_GLIDE_TRANSFORMATION),
                    debugEnabled = PreferenceManager.get(DEBUG_MODE),
                    enableSlideAnimation = PreferenceManager.get(SCREENSAVER_ANIMATE_ASSET_SLIDE),
                    gradiantOverlay = false,
                    metaDataConfig = PreferenceManager.getViewMetaData()
                )
            )
        )
    }

    override fun getBackgroundPicture(it: Asset): String? {
        return ApiUtil.getFileUrl(it.id, "IMAGE")
    }

    override fun createCard(a: Asset): Card {
        return a.toCard()
    }
}