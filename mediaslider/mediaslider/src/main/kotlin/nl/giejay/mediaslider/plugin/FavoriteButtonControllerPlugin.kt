package nl.giejay.mediaslider.plugin

import android.view.View
import com.zeuskartik.mediaslider.R
import nl.giejay.mediaslider.config.MediaSliderConfiguration

class FavoriteButtonControllerPlugin(
    private val placement: ControllerButtonPlacement = ControllerButtonPlacement.RIGHT_OF,
    private val anchorViewId: Int = R.id.media_play_pause
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
            MediaSliderConfiguration.onFavoriteToggle(context.sliderItem.mainItem.id, newValue)
        }
        return ControllerButtonSpec(
            button = favoriteButton,
            placement = placement,
            anchorViewId = anchorViewId
        )
    }
}