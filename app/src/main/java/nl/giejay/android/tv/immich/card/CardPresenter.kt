package nl.giejay.android.tv.immich.card

import android.app.Activity
import android.content.Context
import android.view.ContextThemeWrapper
import androidx.leanback.widget.ImageCardView
import com.bumptech.glide.Glide
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.presenter.AbstractPresenter


open class CardPresenter(context: Context, style: Int = R.style.DefaultCardTheme) :
    AbstractPresenter<ImageCardView, ICard>(ContextThemeWrapper(context, style)) {

    override fun onBindViewHolder(card: ICard, cardView: ImageCardView) {
        loadImage(card, cardView)

        cardView.tag = card
        cardView.titleText = card.title
        if (card.description != "") {
            cardView.contentText = card.description
        }
        setSelected(cardView, card.selected)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        super.onUnbindViewHolder(viewHolder)
        if(context is Activity && context.isFinishing){
            return
        }
        Glide.with(context).clear((viewHolder.view as ImageCardView).mainImageView!!)
    }

    open fun loadImage(card: ICard, cardView: ImageCardView) {
        card.thumbnailUrl?.let {
            Glide.with(context)
                .asBitmap()
                .load(it)
//                .addListener(object : RequestListener<Bitmap> {
//                    override fun onLoadFailed(
//                        e: GlideException?,
//                        model: Any?,
//                        target: Target<Bitmap>?,
//                        isFirstResource: Boolean
//                    ): Boolean {
//                        return false
//                    }
//
//                    override fun onResourceReady(
//                        resource: Bitmap?,
//                        model: Any?,
//                        target: Target<Bitmap>?,
//                        dataSource: DataSource?,
//                        isFirstResource: Boolean
//                    ): Boolean {
//                        Timber.i("Loaded card image for: ${card.id}")
//                        return false
//                    }
//
//                })
                .into(cardView.mainImageView!!)
        }
    }

    override fun onCreateView(): ImageCardView {
        return ImageCardView(context)
    }

    private fun setSelected(imageCardView: ImageCardView, selected: Boolean) {
        if(selected){
            imageCardView.mainImageView!!.background = context.getDrawable(R.drawable.border)
        } else {
            imageCardView.mainImageView!!.background = null
        }
    }
}