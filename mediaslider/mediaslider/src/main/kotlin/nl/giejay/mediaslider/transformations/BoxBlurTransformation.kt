package nl.giejay.mediaslider.transformations

import android.graphics.Bitmap
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

/** Pure pixel blur (three box blur passes approximate a gaussian), separate from Android so it can be unit tested. */
object PixelBlur {
    /** Blurs [argb] (width x height) in place; edge pixels are repeated so the borders do not darken. */
    fun blur(argb: IntArray, width: Int, height: Int, radius: Int, passes: Int = 3) {
        if (radius <= 0 || width <= 0 || height <= 0) return
        val line = IntArray(maxOf(width, height))
        repeat(passes) {
            for (y in 0 until height) {
                for (x in 0 until width) line[x] = argb[y * width + x]
                blurLine(line, width, radius)
                for (x in 0 until width) argb[y * width + x] = line[x]
            }
            for (x in 0 until width) {
                for (y in 0 until height) line[y] = argb[y * width + x]
                blurLine(line, height, radius)
                for (y in 0 until height) argb[y * width + x] = line[y]
            }
        }
    }

    private fun blurLine(line: IntArray, length: Int, radius: Int) {
        val window = 2 * radius + 1
        val source = line.copyOf(length)
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val c = source[i.coerceIn(0, length - 1)]
            a += c ushr 24
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        for (i in 0 until length) {
            line[i] = ((a / window) shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            val add = source[(i + radius + 1).coerceIn(0, length - 1)]
            val drop = source[(i - radius).coerceIn(0, length - 1)]
            a += (add ushr 24) - (drop ushr 24)
            r += ((add shr 16) and 0xFF) - ((drop shr 16) and 0xFF)
            g += ((add shr 8) and 0xFF) - ((drop shr 8) and 0xFF)
            b += (add and 0xFF) - (drop and 0xFF)
        }
    }
}

/**
 * Blurs the (already small) bitmap once on the CPU. The result is an ordinary bitmap, so showing and moving it
 * costs nothing per frame, unlike a RenderEffect blur that is recomputed whenever the page slides.
 */
class BoxBlurTransformation(private val radius: Int) : BitmapTransformation() {
    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
        val width = toTransform.width
        val height = toTransform.height
        val pixels = IntArray(width * height)
        toTransform.getPixels(pixels, 0, width, 0, 0, width, height)
        PixelBlur.blur(pixels, width, height, radius)
        val result = pool.get(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update("$ID$radius".toByteArray(Key.CHARSET))
    }

    override fun equals(other: Any?) = other is BoxBlurTransformation && other.radius == radius

    override fun hashCode() = ID.hashCode() * 31 + radius

    private companion object {
        const val ID = "nl.giejay.mediaslider.transformations.BoxBlurTransformation"
    }
}
