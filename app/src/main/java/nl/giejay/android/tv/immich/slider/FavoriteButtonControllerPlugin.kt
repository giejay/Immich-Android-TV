package nl.giejay.android.tv.immich.slider

import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import nl.giejay.android.tv.immich.R
import nl.giejay.mediaslider.plugin.ControllerButtonPlacement
import nl.giejay.mediaslider.plugin.ControllerButtonSpec
import nl.giejay.mediaslider.plugin.ControllerPluginContext
import nl.giejay.mediaslider.plugin.SliderControllerPlugin

class FavoriteButtonControllerPlugin(
    private val favoriteService: FavoriteService,
    private val scope: CoroutineScope,
    private val placement: ControllerButtonPlacement = ControllerButtonPlacement.RIGHT_OF,
    private val anchorViewId: Int = com.zeuskartik.mediaslider.R.id.media_play_pause
) : SliderControllerPlugin {
    override fun provideControllerButton(context: ControllerPluginContext): ControllerButtonSpec? {
        if (context.hasSecondaryItem) {
            return null
        }

        val favoriteButton = createDefaultControllerButton(
            context = context,
            contentDescriptionRes = R.string.favorite,
            iconRes = R.drawable.ic_favorite_border
        )

        favoriteButton.visibility = View.VISIBLE
        favoriteButton.setImageResource(
            if (context.sliderItem.mainItem.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
        favoriteButton.setOnClickListener {
            val newValue = !context.sliderItem.mainItem.isFavorite
            context.sliderItem.mainItem.isFavorite = newValue
            favoriteButton.setImageResource(
                if (newValue) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            )
            scope.launch {
                favoriteService.toggleFavorite(context.sliderItem.mainItem.id, newValue)
            }
        }
        return ControllerButtonSpec(
            button = favoriteButton,
            placement = placement,
            anchorViewId = anchorViewId
        )
    }
}
