package nl.giejay.mediaslider.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.media3.ui.PlayerView
import androidx.viewpager.widget.PagerAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.zeuskartik.mediaslider.R
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.view.ExoPlayerListener
import nl.giejay.mediaslider.view.ExoPlayerView
import nl.giejay.mediaslider.view.TouchImageView
import timber.log.Timber

class ScreenSlidePagerAdapter(private val context: Context,
                              private var items: List<SliderItemViewHolder>,
                              private val config: MediaSliderConfiguration,
                              private val currentIndex: () -> Int,
                              private val transformResult: (String, Int) -> Unit,
                              private val buttonListener: (Int) -> Unit,
                              private val exoPlayerListener: ExoPlayerListener) : PagerAdapter() {
    private var imageView: TouchImageView? = null
    private val progressBars: MutableMap<Int, ProgressBar> = HashMap()
    private val failedPositions = mutableSetOf<String>()

    fun setItems(items: List<SliderItemViewHolder>) {
        this.items = items
        notifyDataSetChanged()
    }

    fun hideProgressBar(position: Int) {
        val progressBar = progressBars[position]
        if (progressBar != null) {
            progressBar.visibility = GONE
            progressBars.remove(position)
        }
    }

    override fun getItemPosition(`object`: Any): Int {
        return POSITION_NONE
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        var view: View? = null
        val model = items[position]
        if (model.type == SliderItemType.IMAGE) {
            if (model.hasSecondaryItem()) {
                view = inflater.inflate(R.layout.image_double_item, container, false)
                loadImageIntoView(view, R.id.left_image, position, model.mainItem)
                loadImageIntoView(view, R.id.right_image, position, model.secondaryItem!!)
            } else {
                view = inflater.inflate(R.layout.image_item, container, false)
                loadImageIntoView(view, R.id.mBigImage, position, model.mainItem)
            }
        } else if (model.type == SliderItemType.VIDEO) {
            // Use texture view for vertical videos OR if this position previously failed with SurfaceView
            val useTextureView = model.mainItem.orientation != 1 || failedPositions.contains(model.url)
            view = ExoPlayerView(context, if (useTextureView) R.layout.video_item_texture_view else R.layout.video_item)
            view.tag = "view$position"
            view.setupPlayer(config, NextRenderersFactory(context), exoPlayerListener, buttonListener) { player, error ->
                val shouldRetry = !useTextureView && !failedPositions.contains(model.url)
                Timber.e(error,
                    "Player error at position $position for url ${model.url}. Already failed: ${failedPositions.contains(model.url)}." +
                            "Should retry: $shouldRetry. " +
                            "Current index: ${currentIndex()}. " +
                            "Did use texture view: ${useTextureView}. Error: ${error.message}")

                if (shouldRetry) {
                    model.url?.let { failedPositions.add(it) }
                    Timber.w("MediaCodec error at position $position, retrying with TextureView")
                    // Release the current player to avoid memory leaks
                    player.release()
                    // Trigger recreation of this item
                    notifyDataSetChanged()
                    true
                } else {
                    Toast.makeText(context, "Cannot play video: ${error.message}", Toast.LENGTH_LONG).show()
                    false
                }
            }
        }
        container.addView(view)
        return view!!
    }

    private fun loadImageIntoView(imageRootLayout: View,
                                  imageViewResource: Int,
                                  position: Int,
                                  model: SliderItem) {
        imageView = imageRootLayout.findViewById(imageViewResource)
        val progressBar = imageRootLayout.findViewById<ProgressBar>(R.id.mProgressBar)
        if (progressBar != null) {
            progressBars[position] = progressBar
        }
        var glideLoader = Glide.with(context)
            .load(if (config.isOnlyUseThumbnails) model.thumbnailUrl else model.url)
            .transform(config.glideTransformation.transform(context, config) { result -> transformResult(result, position) })
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?,
                                          model: Any?,
                                          target: com.bumptech.glide.request.target.Target<Drawable>,
                                          isFirstResource: Boolean): Boolean {
                    Timber.e(e, "Could not fetch image: %s", model)
                    hideProgressBar(position)
                    return false
                }

                override fun onResourceReady(resource: Drawable,
                                             model: Any,
                                             target: com.bumptech.glide.request.target.Target<Drawable>,
                                             dataSource: com.bumptech.glide.load.DataSource,
                                             isFirstResource: Boolean): Boolean {
                    hideProgressBar(position)
                    return false
                }

            })
        if (!config.isOnlyUseThumbnails) {
            glideLoader = glideLoader.thumbnail(Glide.with(context)
                .load(model.thumbnailUrl))
        }
        glideLoader.into(imageView!!)
    }

    override fun getCount(): Int {
        return items.size
    }

    override fun isViewFromObject(view: View, o: Any): Boolean {
        return (view === o)
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        val view = `object` as View
        if (view is ExoPlayerView) {
            view.releasePlayer()
        } else {
            val imageView = view.findViewById<View>(R.id.mBigImage)
            if (imageView != null) {
                Glide.with(context).clear(imageView)
            }
        }
        container.removeView(view)
    }
}