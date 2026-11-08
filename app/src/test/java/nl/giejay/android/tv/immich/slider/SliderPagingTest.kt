package nl.giejay.android.tv.immich.slider

import com.google.common.truth.Truth.assertThat
import nl.giejay.mediaslider.pager.SliderPaging
import org.junit.Test

class SliderPagingTest {
    @Test
    fun `next moves forward and wraps to the first asset`() {
        assertThat(SliderPaging.next(0, 5)).isEqualTo(1)
        assertThat(SliderPaging.next(3, 5)).isEqualTo(4)
        assertThat(SliderPaging.next(4, 5)).isEqualTo(0)
    }

    @Test
    fun `previous moves back and wraps to the last asset`() {
        assertThat(SliderPaging.previous(3, 5)).isEqualTo(2)
        assertThat(SliderPaging.previous(1, 5)).isEqualTo(0)
        assertThat(SliderPaging.previous(0, 5)).isEqualTo(4)
    }

    @Test
    fun `a single asset stays on itself`() {
        assertThat(SliderPaging.next(0, 1)).isEqualTo(0)
        assertThat(SliderPaging.previous(0, 1)).isEqualTo(0)
    }
}
