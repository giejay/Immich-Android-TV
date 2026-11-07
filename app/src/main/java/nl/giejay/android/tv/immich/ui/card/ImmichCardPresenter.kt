package nl.giejay.android.tv.immich.ui.card

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.leanback.widget.Presenter
import nl.giejay.android.tv.immich.card.ICard
import nl.giejay.android.tv.immich.ui.theme.ImmichTheme

/**
 * Leanback [Presenter] that renders [ICard]s with the Compose [ImmichCard].
 *
 * The Leanback item view (a focusable [FrameLayout]) keeps owning D-Pad focus, so grid navigation,
 * scrolling, click handling and the `OnItemViewSelectedListener` keep working unchanged. Compose
 * only draws; the host view's focus state is forwarded into it.
 */
class ImmichCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder = ViewHolder(ComposeCardView(parent.context))

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        (viewHolder.view as ComposeCardView).card = item as ICard
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        (viewHolder.view as ComposeCardView).card = null
    }

    private class ComposeCardView(context: Context) : FrameLayout(context) {
        // neverEqualPolicy: Card.selected is mutated in place and the same instance is re-bound.
        var card by mutableStateOf<ICard?>(null, neverEqualPolicy())
        private var focusedState by mutableStateOf(false)

        init {
            isFocusable = true
            isFocusableInTouchMode = true
            // The host (not the ComposeView) takes focus, so Leanback's focus handling stays intact.
            descendantFocusability = FOCUS_BLOCK_DESCENDANTS
            val composeView = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                isFocusable = false
                setContent {
                    ImmichTheme {
                        card?.let { ImmichCard(it, focusedState) }
                    }
                }
            }
            addView(composeView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            setOnFocusChangeListener { _, hasFocus -> focusedState = hasFocus }
        }
    }
}
