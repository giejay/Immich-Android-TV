package nl.giejay.android.tv.immich.slider

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import nl.giejay.android.tv.immich.R
import nl.giejay.mediaslider.controller.ControllerIds
import nl.giejay.mediaslider.plugin.ControllerButtonPlacement
import nl.giejay.mediaslider.plugin.ControllerButtonSpec
import nl.giejay.mediaslider.plugin.ControllerPluginContext
import nl.giejay.mediaslider.plugin.SliderControllerPlugin

class FavoriteButtonControllerPlugin(
    private val favoriteService: FavoriteService,
    private val scope: CoroutineScope,
    private val placement: ControllerButtonPlacement = ControllerButtonPlacement.RIGHT_OF,
    private val anchorId: String = ControllerIds.PLAY_PAUSE
) : SliderControllerPlugin {
    override fun provideControllerButton(context: ControllerPluginContext): ControllerButtonSpec? {
        if (context.hasSecondaryItem) {
            return null
        }

        val item = context.sliderItem.mainItem
        val icon = mutableStateOf(if (item.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
        return ControllerButtonSpec(
            description = context.context.getString(R.string.favorite),
            icon = icon,
            onClick = {
                val newValue = !item.isFavorite
                item.isFavorite = newValue
                icon.value = if (newValue) R.drawable.ic_favorite else R.drawable.ic_favorite_border
                scope.launch { favoriteService.toggleFavorite(item.id, newValue) }
            },
            placement = placement,
            anchorId = anchorId
        )
    }
}
