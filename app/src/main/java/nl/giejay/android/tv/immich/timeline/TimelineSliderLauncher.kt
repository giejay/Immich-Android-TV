package nl.giejay.android.tv.immich.timeline

import androidx.navigation.NavDirections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.album.AlbumDetailsFragmentDirections
import nl.giejay.android.tv.immich.api.model.Memory
import nl.giejay.android.tv.immich.shared.prefs.MetaDataScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ANIMATE_ASSET_SLIDE
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ANIMATION_SPEED
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_DPAD_SEEK_IN_VIDEO
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_FORCE_ORIGINAL_VIDEO
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_GLIDE_TRANSFORMATION
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_INTERVAL
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_HEIGHT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_WIDTH
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_BLURRED_BACKDROP
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ONLY_USE_THUMBNAILS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_PAN_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_SHOW_DATE_TOP_LEFT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ZOOM_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ZOOM_SCROLL_PANORAMAS
import nl.giejay.android.tv.immich.shared.util.Utils.pmap
import nl.giejay.android.tv.immich.shared.util.toSliderItems
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.util.LoadMore
import nl.giejay.mediaslider.util.LoadMoreResult
import nl.giejay.mediaslider.viewmodel.MediaSliderViewModel

/**
 * Opens a timeline photo or memory in the media slider; used by the Compose timeline. Mirrors
 * what [TimelineFragment] does for its View mosaic (both keep the leave-off snapshot in the
 * shared [TimelineViewModel] so Back lands on the cell that was last viewed).
 */
class TimelineSliderLauncher(
    private val viewModel: TimelineViewModel,
    private val sliderViewModel: MediaSliderViewModel,
    private val scope: CoroutineScope,
    private val isActive: () -> Boolean,
    private val navigate: (NavDirections) -> Unit,
    private val setLoading: (Boolean) -> Unit,
) {
    fun openAsset(assetId: String) {
        viewModel.applyLeaveOffSnapshot(TimelineLeaveOff.afterOpeningMosaic(assetId))
        viewModel.rememberSelectionByAssetId(assetId)

        val bucketKey = viewModel.bucketKeyForAsset(assetId) ?: return

        setLoading(true)
        scope.launch(Dispatchers.Default) {
            // Seed with just the current bucket and its neighbors for fast startup.
            val initialAssets = viewModel.assetsAroundBucket(bucketKey)
            if (initialAssets.isEmpty()) return@launch

            val sliderItems = initialAssets.pmap { it.toSliderItemViewHolder() }
            val startIndex = sliderItems.indexOfFirst { it.ids().contains(assetId) }.coerceAtLeast(0)

            // Tracks the newest bucket currently in the slider so we can page forwards.
            var lastBucketKey: String? = initialAssets.lastOrNull()?.let { asset ->
                viewModel.bucketKeyForAsset(asset.id)
            }

            val loadMore: LoadMore = suspend loadMore@{
                val afterKey = lastBucketKey ?: return@loadMore LoadMoreResult(emptyList(), false)
                val next = viewModel.nextBucketAfter(afterKey) ?: return@loadMore LoadMoreResult(emptyList(), false)
                val knownIds = initialAssets.map { it.id }.toSet()
                viewModel.loadBucket(next.timeBucket)
                lastBucketKey = next.timeBucket
                val items = viewModel.bucketAssets.value[next.timeBucket].orEmpty()
                    .filter { it.id !in knownIds }
                    .pmap { it.toSliderItemViewHolder() }
                LoadMoreResult(items, items.isNotEmpty() && viewModel.nextBucketAfter(next.timeBucket) != null)
            }

            withContext(Dispatchers.Main) {
                if (!isActive()) return@withContext
                setLoading(false)
                val config = MediaSliderConfiguration(
                    startIndex,
                    PreferenceManager.get(SLIDER_INTERVAL),
                    PreferenceManager.get(SLIDER_ONLY_USE_THUMBNAILS),
                    isVideoSoundEnable = true,
                    sliderItems,
                    loadMore,
                    { item ->
                        // Keep leave-off on the asset the user is currently viewing so Back
                        // lands on that cell, not the one that opened the slider.
                        viewModel.applyLeaveOffSnapshot(
                            TimelineLeaveOff.afterOpeningMosaic(item.mainItem.id)
                        )
                        viewModel.rememberSelectionByAssetId(item.mainItem.id)
                    },
                    animationSpeedMillis = PreferenceManager.get(SLIDER_ANIMATION_SPEED),
                    maxCutOffHeight = PreferenceManager.get(SLIDER_MAX_CUT_OFF_HEIGHT),
                    maxCutOffWidth = PreferenceManager.get(SLIDER_MAX_CUT_OFF_WIDTH),
                    glideTransformation = PreferenceManager.get(SLIDER_GLIDE_TRANSFORMATION),
                    enableSlideAnimation = PreferenceManager.get(SCREENSAVER_ANIMATE_ASSET_SLIDE),
                    gradiantOverlay = false,
                    metaDataConfig = PreferenceManager.getAllMetaData(MetaDataScreen.VIEWER)
                        .filter { it.type != MetaDataType.MEDIA_COUNT },
                    zoomAndScrollPanorama = PreferenceManager.get(SLIDER_ZOOM_SCROLL_PANORAMAS),
                    zoomEffectPercent = PreferenceManager.get(SLIDER_ZOOM_EFFECT),
                    panEffectPercent = PreferenceManager.get(SLIDER_PAN_EFFECT),
                    useLargeVideoBuffer = PreferenceManager.get(SLIDER_FORCE_ORIGINAL_VIDEO),
                    dpadSeeksInVideo = PreferenceManager.get(SLIDER_DPAD_SEEK_IN_VIDEO),
                    showDateTopLeft = PreferenceManager.get(SLIDER_SHOW_DATE_TOP_LEFT),
                    blurredBackdrop = PreferenceManager.get(SLIDER_BLURRED_BACKDROP)
                )
                sliderViewModel.configuration = config
                navigate(AlbumDetailsFragmentDirections.actionToPhotoSlider())
            }
        }
    }

    /** Opens a memory's assets in the slider, auto-playing; a bounded set without [LoadMore]. */
    fun openMemory(memory: Memory) {
        if (memory.assets.isEmpty()) return
        viewModel.applyLeaveOffSnapshot(
            TimelineLeaveOff.afterOpeningMemory(memory.id, viewModel.lastSelectedAssetId)
        )
        val sliderItems = memory.assets.toSliderItems(
            keepOrder = true,
            // Memory payloads omit EXIF/dimensions; portrait pairing is unreliable and can
            // bind the wrong asset's city/country into the details strip.
            mergePortrait = false
        )
        val config = MediaSliderConfiguration(
            0,
            PreferenceManager.get(SLIDER_INTERVAL),
            PreferenceManager.get(SLIDER_ONLY_USE_THUMBNAILS),
            isVideoSoundEnable = true,
            sliderItems,
            null,
            animationSpeedMillis = PreferenceManager.get(SLIDER_ANIMATION_SPEED),
            maxCutOffHeight = PreferenceManager.get(SLIDER_MAX_CUT_OFF_HEIGHT),
            maxCutOffWidth = PreferenceManager.get(SLIDER_MAX_CUT_OFF_WIDTH),
            glideTransformation = PreferenceManager.get(SLIDER_GLIDE_TRANSFORMATION),
            enableSlideAnimation = PreferenceManager.get(SCREENSAVER_ANIMATE_ASSET_SLIDE),
            gradiantOverlay = false,
            metaDataConfig = PreferenceManager.getAllMetaData(MetaDataScreen.VIEWER)
                .filter { it.type != MetaDataType.MEDIA_COUNT },
            zoomAndScrollPanorama = PreferenceManager.get(SLIDER_ZOOM_SCROLL_PANORAMAS),
            zoomEffectPercent = PreferenceManager.get(SLIDER_ZOOM_EFFECT),
            panEffectPercent = PreferenceManager.get(SLIDER_PAN_EFFECT),
            useLargeVideoBuffer = PreferenceManager.get(SLIDER_FORCE_ORIGINAL_VIDEO),
            dpadSeeksInVideo = PreferenceManager.get(SLIDER_DPAD_SEEK_IN_VIDEO),
            showDateTopLeft = PreferenceManager.get(SLIDER_SHOW_DATE_TOP_LEFT),
            blurredBackdrop = PreferenceManager.get(SLIDER_BLURRED_BACKDROP)
        )
        sliderViewModel.configuration = config
        navigate(AlbumDetailsFragmentDirections.actionToPhotoSlider(timelineView = true))
    }
}
