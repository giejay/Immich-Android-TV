package nl.giejay.android.tv.immich.assets

import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import arrow.core.Either
import arrow.core.getOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.album.AlbumDetailsFragmentDirections
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.Folder
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment
import nl.giejay.android.tv.immich.shared.prefs.MetaDataScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ANIMATE_ASSET_SLIDE
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ANIMATION_SPEED
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_GLIDE_TRANSFORMATION
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_FORCE_ORIGINAL_VIDEO
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_INTERVAL
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_DPAD_SEEK_IN_VIDEO
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_HEIGHT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_WIDTH
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MERGE_PORTRAIT_PHOTOS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ONLY_USE_THUMBNAILS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_PAN_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_SHOW_DATE_TOP_LEFT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ZOOM_EFFECT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ZOOM_SCROLL_PANORAMAS
import nl.giejay.android.tv.immich.shared.util.toCard
import nl.giejay.android.tv.immich.shared.util.toSliderItems
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.util.LoadMoreResult
import nl.giejay.mediaslider.viewmodel.MediaSliderViewModel

data class Item(val item: Any) {
    fun isAsset() = item is Asset
    fun name() = if (item is Asset) item.deviceAssetId else (item as Folder).path
    fun asset() = item as Asset
    val id = if (item is Asset) item.id else (item as Folder).path
}

/**
 * The assets that stand for a folder: up to [max] of its photos (videos only when there are fewer photos), in the
 * order of the list (which is already without excluded assets).
 */
internal fun folderCoverAssetIds(assets: List<Asset>, max: Int = 4): List<String> =
    (assets.filter { it.type.equals("IMAGE", ignoreCase = true) } + assets.filterNot { it.type.equals("IMAGE", ignoreCase = true) })
        .take(max).map { it.id }

/** A folder with at least two pictures is shown as a collage, one picture stays a single cover. */
internal fun List<String>.collageOrEmpty(): List<String> = if (size >= 2) this else emptyList()

private const val MAX_COVER_FOLDERS = 40
private const val MAX_PARALLEL_COVER_REQUESTS = 3

class FolderFragment : VerticalCardGridFragment<Item>() {
    private var rootFolder: Folder? = null
    private var stack = mutableListOf<Folder>()
    private val sliderViewModel: MediaSliderViewModel by activityViewModels()

    override fun sortItems(items: List<Item>): List<Item> {
        return items.sortedWith({ i, i2 ->
            if (!i.isAsset() && !i2.isAsset() || (i.isAsset() && i2.isAsset())) {
                // both folders or both items
                i2.name()?.let { i.name()?.compareTo(it) } ?: 0
            } else if (!i.isAsset()) {
                // i is folder, i2 is asset, precedence for folder
                -1
            } else {
                // i2 is folder, i is asset
                1
            }
        })
    }

    override suspend fun loadItems(
        apiClient: ApiClient,
        page: Int,
        pageCount: Int
    ): Either<String, List<Item>> {
        if (rootFolder == null) {
            val listFolders = apiClient.listFolders()
            return listFolders.map {
                rootFolder = it
                val folderAssets = apiClient.listAssetsForFolder(it.path).getOrElse { emptyList() }.map { Item(it) }
                it.children.map { Item(it) } + folderAssets.map { Item(it) }
            }
        }
        return Either.Right(emptyList())
    }

    override fun onItemSelected(card: Card, indexOf: Int) {
        // no use case yet
    }

    override fun onItemClicked(card: Card) {
        // a folder card is told from a photo card by its item (the thumbnail can be a photo now)
        val clicked = this.assets.firstOrNull { it.id == card.id }
        if (clicked != null && !clicked.isAsset()) {
            if (card.title == "..") {
                updateState(stack.removeAt(stack.size - 1))
            } else {
                val clickedChild = rootFolder!!.children.find { it.path == card.title }
                stack.add(rootFolder!!)
                updateState(clickedChild!!)
            }
        } else {
            val findAllAssets = this.assets.filter { it.item is Asset }.map { it.item as Asset }
            val sliderItems = findAllAssets.toSliderItems(keepOrder = true, mergePortrait = PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS))
            val config = MediaSliderConfiguration(
                sliderItems.indexOfFirst { it.ids().contains(card.id) },
                PreferenceManager.get(SLIDER_INTERVAL),
                PreferenceManager.get(SLIDER_ONLY_USE_THUMBNAILS),
                isVideoSoundEnable = true,
                sliderItems,
                { LoadMoreResult(emptyList(), false) },
                { item -> manualUpdatePosition(this.assets.indexOfFirst { item.ids().contains(it.id) }) },
                animationSpeedMillis = PreferenceManager.get(SLIDER_ANIMATION_SPEED),
                maxCutOffHeight = PreferenceManager.get(SLIDER_MAX_CUT_OFF_HEIGHT),
                maxCutOffWidth = PreferenceManager.get(SLIDER_MAX_CUT_OFF_WIDTH),
                glideTransformation = PreferenceManager.get(SLIDER_GLIDE_TRANSFORMATION),
                enableSlideAnimation = PreferenceManager.get(SCREENSAVER_ANIMATE_ASSET_SLIDE),
                gradiantOverlay = false,
                metaDataConfig = PreferenceManager.getAllMetaData(MetaDataScreen.VIEWER),
                zoomAndScrollPanorama = PreferenceManager.get(SLIDER_ZOOM_SCROLL_PANORAMAS),
                zoomEffectPercent = PreferenceManager.get(SLIDER_ZOOM_EFFECT),
                panEffectPercent = PreferenceManager.get(SLIDER_PAN_EFFECT),
                useLargeVideoBuffer = PreferenceManager.get(SLIDER_FORCE_ORIGINAL_VIDEO),
                dpadSeeksInVideo = PreferenceManager.get(SLIDER_DPAD_SEEK_IN_VIDEO),
                showDateTopLeft = PreferenceManager.get(SLIDER_SHOW_DATE_TOP_LEFT)
            )
            sliderViewModel.configuration = config
            findNavController().navigate(
                AlbumDetailsFragmentDirections.actionToPhotoSlider()
            )
        }
    }

    /** Cover picture urls of the folders of the current level, filled in the background. */
    private val covers = mutableMapOf<String, List<String>>()
    private var coverJob: Job? = null

    override fun setData(assets: List<Item>) {
        super.setData(assets)
        loadCovers()
    }

    override fun clearState() {
        coverJob?.cancel()
        covers.clear()
        super.clearState()
    }

    override fun onDestroyView() {
        coverJob?.cancel()
        super.onDestroyView()
    }

    /**
     * Fetches up to four photos of each folder (a few at a time, for the first folders only) and swaps them in for the
     * folder icon as soon as it arrives. Folders without photos, and failed requests, keep the icon.
     */
    private fun loadCovers() {
        val folders = rootFolder?.children?.filter { it.path !in covers }?.take(MAX_COVER_FOLDERS) ?: return
        if (folders.isEmpty() || coverJob?.isActive == true) return
        coverJob = ioScope.launch {
            val gate = Semaphore(MAX_PARALLEL_COVER_REQUESTS)
            folders.map { folder ->
                launch {
                    gate.withPermit {
                        val urls = folderCoverAssetIds(apiClient.listAssetsForFolder(folder.getFullPath()).getOrElse { emptyList() })
                            .mapNotNull { ApiUtil.getThumbnailUrl(it, "thumbnail") }
                        if (urls.isEmpty()) return@withPermit
                        withContext(Dispatchers.Main) {
                            covers[folder.path] = urls
                            val index = cards.indexOfFirst { it.id == folder.path }
                            if (index >= 0) cards[index] = cards[index].copy(thumbnailUrl = urls.first(), collageUrls = urls.collageOrEmpty())
                        }
                    }
                }
            }.joinAll()
        }
    }

    private fun updateState(clickedChild: Folder) {
        rootFolder = clickedChild
        clearState()
        val folders = (if (stack.isNotEmpty()) listOf(Folder("..", mutableListOf(), null)) else emptyList()) + rootFolder!!.children
        setData(folders.map { Item(it) })
        ioScope.launch {
            apiClient.listAssetsForFolder(rootFolder!!.getFullPath()).map {
                setDataOnMain(it.map { Item(it) })
            }
        }
        updateManualPositionHandler(1)
    }

    override fun getBackgroundPicture(it: Item): String? {
        return null
    }

    override fun createCard(a: Item): Card {
        return if (a.item is Folder) {
            Card(
                a.item.path,
                "",
                a.item.path,
                if (a.item.path == "..") "folder_up" else covers[a.item.path]?.first() ?: "folder",
                null,
                false,
                covers[a.item.path].orEmpty().collageOrEmpty(),
            )
        } else {
            (a.item as Asset).toCard()
        }
    }
}
