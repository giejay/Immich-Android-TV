package nl.giejay.android.tv.immich.shared

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageCacheSizeTest {
    @Test
    fun `choices are converted to bytes`() {
        assertThat(imageCacheBytes(250)).isEqualTo(250L * 1024 * 1024)
        assertThat(imageCacheBytes(2000)).isEqualTo(2000L * 1024 * 1024)
    }

    @Test
    fun `absurd values are kept within 50 MB and 4 GB`() {
        assertThat(imageCacheBytes(0)).isEqualTo(50L * 1024 * 1024)
        assertThat(imageCacheBytes(-5)).isEqualTo(50L * 1024 * 1024)
        assertThat(imageCacheBytes(100_000)).isEqualTo(4096L * 1024 * 1024)
    }
}
