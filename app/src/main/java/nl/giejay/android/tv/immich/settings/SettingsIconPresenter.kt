package nl.giejay.android.tv.immich.settings

import android.content.Context
import android.view.View
import androidx.leanback.widget.ImageCardView
import com.bumptech.glide.Glide
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.card.CardPresenter
import nl.giejay.android.tv.immich.card.ICard

/**
 * Simple presenter implementation to represent settings icon as cards.
 */
class SettingsIconPresenter(context: Context) : CardPresenter(context, R.style.IconCardTheme) {
    override fun onCreateView(): ImageCardView {
        val imageCardView: ImageCardView = super.onCreateView()
        imageCardView.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                setImageBackground(imageCardView, R.color.settings_card_background_focussed)
            } else {
                setImageBackground(imageCardView, R.color.settings_card_background)
            }
        }
        setImageBackground(imageCardView, R.color.settings_card_background)
        return imageCardView
    }

    override fun loadImage(card: ICard, cardView: ImageCardView) {
        val resourceId = context.resources
            .getIdentifier(
                card.thumbnailUrl,
                "drawable", context.packageName
            )
        Glide.with(context)
            .asBitmap()
            .load(resourceId)
            .into(cardView.mainImageView)
    }

    private fun setImageBackground(imageCardView: ImageCardView, colorId: Int) {
        imageCardView.setBackgroundColor(context.resources.getColor(colorId))
    }
}