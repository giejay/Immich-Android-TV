package nl.giejay.mediaslider

import com.google.common.truth.Truth.assertThat
import nl.giejay.mediaslider.transformations.PixelBlur
import org.junit.Test

class PixelBlurTest {
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    @Test
    fun flatImageStaysFlat() {
        val pixels = IntArray(8 * 6) { gray(120) }
        PixelBlur.blur(pixels, 8, 6, radius = 2)
        assertThat(pixels.toSet()).containsExactly(gray(120))
    }

    @Test
    fun sharpEdgeIsSoftenedAndStaysInRange() {
        val w = 16
        val pixels = IntArray(w * 4) { gray(if (it % w < w / 2) 0 else 255) }
        PixelBlur.blur(pixels, w, 4, radius = 2)
        val row = (0 until w).map { pixels[it] and 0xFF }
        assertThat(row[0]).isEqualTo(0)
        assertThat(row[w - 1]).isEqualTo(255)
        assertThat(row[w / 2 - 1]).isGreaterThan(0)
        assertThat(row[w / 2]).isLessThan(255)
        assertThat(row).isInOrder()
    }

    @Test
    fun radiusZeroOrEmptyImageIsANoOp() {
        val pixels = IntArray(4) { gray(it * 50) }
        val copy = pixels.copyOf()
        PixelBlur.blur(pixels, 2, 2, radius = 0)
        assertThat(pixels).isEqualTo(copy)
        PixelBlur.blur(IntArray(0), 0, 0, radius = 3)
    }
}
