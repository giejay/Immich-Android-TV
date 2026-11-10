package nl.giejay.android.tv.immich.shared.thumbhash

import android.graphics.Bitmap
import android.util.Base64
import android.util.LruCache

/** Decoded placeholder bitmaps by their base64 ThumbHash string, so each hash is decoded once. */
object ThumbHashCache {
    private const val MAX_ENTRIES = 600
    private val cache = LruCache<String, Bitmap>(MAX_ENTRIES)

    /** The placeholder bitmap for [base64Hash], or null for a missing or malformed hash. */
    fun get(base64Hash: String?): Bitmap? {
        if (base64Hash.isNullOrEmpty()) return null
        cache.get(base64Hash)?.let { return it }
        val bitmap = runCatching {
            val image = ThumbHash.decode(Base64.decode(base64Hash, Base64.DEFAULT)) ?: return null
            Bitmap.createBitmap(image.argb, image.width, image.height, Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return null
        cache.put(base64Hash, bitmap)
        return bitmap
    }
}
