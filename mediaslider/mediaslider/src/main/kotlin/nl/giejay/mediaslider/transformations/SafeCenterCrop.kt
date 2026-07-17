package nl.giejay.mediaslider.transformations

import android.content.Context
import android.graphics.Bitmap
import android.util.DisplayMetrics
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.TransformationUtils
import timber.log.Timber
import java.security.MessageDigest


class SafeCenterCrop(context: Context,
                     private val maxCutOffHeight: Int,
                     private val maxCutOffWidth: Int) : BitmapTransformation() {
    val width: Int
    val height: Int

    init {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        width = metrics.widthPixels
        height = metrics.heightPixels
    }

    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val percentageDiffWidth: Int = Math.round(((toTransform.width % width).toFloat() / width) * 100)
        val percentageDiffHeight: Int = Math.round(((toTransform.height % height).toFloat() / height) * 100)
        if (percentageDiffWidth <= maxCutOffWidth && percentageDiffHeight <= maxCutOffHeight) {
            Timber.i("Safe cropping because width differs with ${percentageDiffWidth}% vs screen size. Max cut off: $maxCutOffWidth and height differs with ${percentageDiffHeight}% vs screen size. Max cut off: $maxCutOffHeight")
            return TransformationUtils.centerCrop(pool, toTransform, outWidth, outHeight)
        }
        if (percentageDiffWidth > maxCutOffWidth) {
            Timber.i("Not safe cropping because width differs with ${percentageDiffWidth}% vs screen size. Max cut off: $maxCutOffWidth")
        } else {
            Timber.i("Not safe cropping because height differs with ${percentageDiffHeight}% vs screen size. Max cut off: $maxCutOffHeight")
        }
        return toTransform
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {}
}