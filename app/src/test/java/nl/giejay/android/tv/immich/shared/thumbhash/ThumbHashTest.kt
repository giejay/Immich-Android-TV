package nl.giejay.android.tv.immich.shared.thumbhash

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThumbHashTest {
    /**
     * Header only hash without any AC detail (all scales 0): l=1, p and q about 0, landscape, so the image is
     * a flat near white 7:3 picture of 32 x 14 pixels.
     */
    private fun flatLandscapeHash(): ByteArray {
        val header24 = 63 or (32 shl 6) or (32 shl 12)
        val header16 = 3 or (1 shl 15)
        return byteArrayOf(
            header24.toByte(), (header24 shr 8).toByte(), (header24 shr 16).toByte(),
            header16.toByte(), (header16 shr 8).toByte(),
        ) + ByteArray(12)
    }

    @Test
    fun `aspect ratio comes from the header`() {
        assertThat(ThumbHash.aspectRatio(flatLandscapeHash())).isWithin(0.001f).of(7f / 3f)
    }

    @Test
    fun `decodes to at most 32 pixels on the long side`() {
        val image = ThumbHash.decode(flatLandscapeHash())!!
        assertThat(image.width).isEqualTo(32)
        assertThat(image.height).isEqualTo(14)
        assertThat(image.argb).hasLength(32 * 14)
    }

    @Test
    fun `a hash without detail decodes to one flat opaque color`() {
        val image = ThumbHash.decode(flatLandscapeHash())!!
        assertThat(image.argb.toSet()).hasSize(1)
        val pixel = image.argb.first()
        assertThat(pixel ushr 24).isEqualTo(0xFF)
        // l = 1 and p, q about 0: every channel is close to white
        assertThat((pixel shr 16) and 0xFF).isAtLeast(240)
        assertThat((pixel shr 8) and 0xFF).isAtLeast(240)
        assertThat(pixel and 0xFF).isAtLeast(240)
    }

    @Test
    fun `portrait hashes are taller than wide`() {
        val hash = flatLandscapeHash().also { it[4] = 0 } // clears the landscape flag: lx = 3, ly = 7
        val image = ThumbHash.decode(hash)!!
        assertThat(image.height).isEqualTo(32)
        assertThat(image.width).isLessThan(image.height)
    }

    @Test
    fun `too short hashes are rejected instead of crashing`() {
        assertThat(ThumbHash.decode(byteArrayOf(1, 2, 3))).isNull()
        assertThat(ThumbHash.aspectRatio(byteArrayOf())).isEqualTo(1f)
    }
}
