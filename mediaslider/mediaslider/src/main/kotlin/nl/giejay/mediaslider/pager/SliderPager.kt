package nl.giejay.mediaslider.pager

import androidx.viewpager.widget.ViewPager

/**
 * What [nl.giejay.mediaslider.view.MediaSliderController] needs from the paging surface, so the
 * controller does not depend on a concrete pager (ViewPager today, a Compose pager later).
 */
interface SliderPager {
    /** Index of the page that is (or is about to be) in view. */
    val currentIndex: Int
    val itemCount: Int

    fun scrollTo(index: Int, animate: Boolean)

    /** Gives D-pad focus back to the pager unless it already has it. */
    fun restoreFocus()
}

/** Index arithmetic of the endless slideshow: after the last asset comes the first and vice versa. */
object SliderPaging {
    fun next(current: Int, count: Int): Int = if (current < count - 1) current + 1 else 0

    fun previous(current: Int, count: Int): Int = (if (current == 0) count else current) - 1
}

class ViewPagerSliderPager(private val pager: ViewPager) : SliderPager {
    override val currentIndex: Int get() = pager.currentItem
    override val itemCount: Int get() = pager.adapter?.count ?: 0

    override fun scrollTo(index: Int, animate: Boolean) = pager.setCurrentItem(index, animate)

    override fun restoreFocus() {
        pager.post {
            if (!pager.hasFocus()) {
                pager.requestFocus()
            }
        }
    }
}

/** Lets the controller keep one pager reference while the slider chooses the implementation later. */
class DelegatingSliderPager(var delegate: SliderPager) : SliderPager {
    override val currentIndex: Int get() = delegate.currentIndex
    override val itemCount: Int get() = delegate.itemCount
    override fun scrollTo(index: Int, animate: Boolean) = delegate.scrollTo(index, animate)
    override fun restoreFocus() = delegate.restoreFocus()
}
