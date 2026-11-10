package nl.giejay.android.tv.immich.shared.thumbhash

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A decoded ThumbHash: a tiny (at most 32 px) opaque image as ARGB pixels. */
class ThumbHashImage(val width: Int, val height: Int, val argb: IntArray)

/**
 * Decoder for ThumbHash, the compact image placeholder Immich stores per asset
 * (https://evanw.github.io/thumbhash/). Ported from the MIT licensed reference implementation by
 * Evan Wallace; only decoding is needed here. The alpha channel is ignored, placeholders are opaque.
 */
object ThumbHash {
    private const val MAX_SIZE = 32

    /** Approximate width / height of the original image, read from the hash header. */
    fun aspectRatio(hash: ByteArray): Float {
        if (hash.size < 5) return 1f
        val hasAlpha = (hash[2].toInt() and 0x80) != 0
        val lMax = if (hasAlpha) 5 else 7
        val lMin = hash[3].toInt() and 7
        val landscape = (hash[4].toInt() and 0x80) != 0
        val lx = if (landscape) lMax else lMin
        val ly = if (landscape) lMin else lMax
        return lx.toFloat() / ly
    }

    /** Decodes [hash]; returns null when it is too short to be a ThumbHash. */
    fun decode(hash: ByteArray): ThumbHashImage? {
        if (hash.size < 5) return null
        fun byte(i: Int) = hash.getOrElse(i) { 0 }.toInt() and 0xFF
        val header24 = byte(0) or (byte(1) shl 8) or (byte(2) shl 16)
        val header16 = byte(3) or (byte(4) shl 8)
        val lDc = (header24 and 63) / 63f
        val pDc = ((header24 shr 6) and 63) / 31.5f - 1f
        val qDc = ((header24 shr 12) and 63) / 31.5f - 1f
        val lScale = ((header24 shr 18) and 31) / 31f
        val hasAlpha = (header24 shr 23) != 0
        val pScale = ((header16 shr 3) and 63) / 63f
        val qScale = ((header16 shr 9) and 63) / 63f
        val landscape = (header16 shr 15) != 0
        val lx = max(3, if (landscape) (if (hasAlpha) 5 else 7) else header16 and 7)
        val ly = max(3, if (landscape) header16 and 7 else (if (hasAlpha) 5 else 7))

        var acIndex = 0
        val acStart = if (hasAlpha) 6 else 5
        fun decodeChannel(nx: Int, ny: Int, scale: Float): FloatArray {
            val ac = ArrayList<Float>()
            for (cy in 0 until ny) {
                var cx = if (cy > 0) 0 else 1
                while (cx * ny < nx * (ny - cy)) {
                    val nibble = (byte(acStart + (acIndex shr 1)) shr ((acIndex and 1) shl 2)) and 15
                    acIndex++
                    ac.add((nibble / 7.5f - 1f) * scale)
                    cx++
                }
            }
            return ac.toFloatArray()
        }
        val lAc = decodeChannel(lx, ly, lScale)
        // saturation is boosted by 1.25 to compensate for the quantization
        val pAc = decodeChannel(3, 3, pScale * 1.25f)
        val qAc = decodeChannel(3, 3, qScale * 1.25f)

        val ratio = aspectRatio(hash)
        val w = (if (ratio > 1f) MAX_SIZE.toFloat() else MAX_SIZE * ratio).roundToInt().coerceAtLeast(1)
        val h = (if (ratio > 1f) MAX_SIZE / ratio else MAX_SIZE.toFloat()).roundToInt().coerceAtLeast(1)
        val pixels = IntArray(w * h)
        val fx = FloatArray(max(lx, 3))
        val fy = FloatArray(max(ly, 3))
        var i = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                var l = lDc
                var p = pDc
                var q = qDc
                for (cx in fx.indices) fx[cx] = cos(PI.toFloat() / w * (x + 0.5f) * cx)
                for (cy in fy.indices) fy[cy] = cos(PI.toFloat() / h * (y + 0.5f) * cy)
                var j = 0
                for (cy in 0 until ly) {
                    var cx = if (cy > 0) 0 else 1
                    val fy2 = fy[cy] * 2f
                    while (cx * ly < lx * (ly - cy)) {
                        l += lAc[j] * fx[cx] * fy2
                        cx++
                        j++
                    }
                }
                j = 0
                for (cy in 0 until 3) {
                    var cx = if (cy > 0) 0 else 1
                    val fy2 = fy[cy] * 2f
                    while (cx < 3 - cy) {
                        val f = fx[cx] * fy2
                        p += pAc[j] * f
                        q += qAc[j] * f
                        cx++
                        j++
                    }
                }
                val b = l - 2f / 3f * p
                val r = (3f * l - b + q) / 2f
                val g = r - q
                pixels[i++] = (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
            }
        }
        return ThumbHashImage(w, h, pixels)
    }

    private fun channel(v: Float): Int = (255f * min(1f, max(0f, v))).roundToInt()
}
