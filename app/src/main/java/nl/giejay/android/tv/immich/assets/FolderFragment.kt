package nl.giejay.android.tv.immich.assets

import androidx.navigation.fragment.findNavController
import arrow.core.Either
import arrow.core.getOrElse
import nl.giejay.mediaslider.model.MetaDataType
import kotlinx.coroutines.launch
import nl.giejay.android.tv.immich.album.AlbumDetailsFragmentDirections
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.Folder
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.MetaDataScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ANIMATE_ASSET_SLIDE
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ANIMATION_SPEED
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_GLIDE_TRANSFORMATION
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_INTERVAL
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_HEIGHT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_WIDTH
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MERGE_PORTRAIT_PHOTOS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ONLY_USE_THUMBNAILS
import nl.giejay.android.tv.immich.shared.util.toCard
import nl.giejay.android.tv.immich.shared.util.toSliderItems
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import java.util.EnumSet

data class Item(val item: Any) {
    fun isAsset() = item is Asset
    fun name() = if (item is Asset) item.deviceAssetId else (item as Folder).path
    fun asset() = item as Asset
    val id = if (item is Asset) item.id else (item as Folder).path
}

class FolderFragment : VerticalCardGridFragment<Item>() {
    private var rootFolder: Folder? = null
    private var stack = mutableListOf<Folder>()

    override fun sortItems(items: List<Item>): List<Item> {
        return items.sortedWith({ i, i2 ->
            if (!i.isAsset() && !i2.isAsset() || (i.isAsset() && i2.isAsset())) {
                // both folders or both items
                i.name()!!.compareTo(i2.name()!!)
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
        if (card.thumbnailUrl?.contains("folder") == true) {
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
            findNavController().navigate(
                AlbumDetailsFragmentDirections.actionToPhotoSlider(
                    MediaSliderConfiguration(
                        sliderItems.indexOfFirst { it.ids().contains(card.id) },
                        PreferenceManager.get(SLIDER_INTERVAL),
                        PreferenceManager.get(SLIDER_ONLY_USE_THUMBNAILS),
                        isVideoSoundEnable = true,
                        sliderItems,
                        { listOf() },
                        { item -> manualUpdatePosition(this.assets.indexOfFirst { item.ids().contains(it.id) }) },
                        animationSpeedMillis = PreferenceManager.get(SLIDER_ANIMATION_SPEED),
                        maxCutOffHeight = PreferenceManager.get(SLIDER_MAX_CUT_OFF_HEIGHT),
                        maxCutOffWidth = PreferenceManager.get(SLIDER_MAX_CUT_OFF_WIDTH),
                        transformation = PreferenceManager.get(SLIDER_GLIDE_TRANSFORMATION),
                        debugEnabled = PreferenceManager.get(DEBUG_MODE),
                        enableSlideAnimation = PreferenceManager.get(SCREENSAVER_ANIMATE_ASSET_SLIDE),
                        gradiantOverlay = false,
                        metaDataConfig = PreferenceManager.getAllMetaData(MetaDataScreen.VIEWER)
                    )
                )
            )
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
                if (a.item.path == "..") "folder_up" else "folder",
                null,
                false
            )
        } else {
            (a.item as Asset).toCard()
        }
    }
}