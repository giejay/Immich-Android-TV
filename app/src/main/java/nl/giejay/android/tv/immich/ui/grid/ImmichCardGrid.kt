package nl.giejay.android.tv.immich.ui.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import nl.giejay.android.tv.immich.card.ICard
import nl.giejay.android.tv.immich.ui.card.ImmichFocusableCard

/**
 * Vertical grid of [ImmichFocusableCard]s with D-Pad navigation, focus restoration and
 * programmatic focus requests.
 *
 * @param focusIndexRequest when non-null, the grid scrolls to this index and focuses it as soon as
 * the item exists, then calls [onFocusIndexRequestHandled].
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ImmichCardGrid(
    cards: List<ICard>,
    columns: Int,
    onCardClick: (index: Int, card: ICard) -> Unit,
    onCardFocused: (index: Int, card: ICard) -> Unit,
    focusIndexRequest: Int?,
    onFocusIndexRequestHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val requesters = remember { HashMap<Int, FocusRequester>() }

    LaunchedEffect(focusIndexRequest, cards.size) {
        val target = focusIndexRequest ?: return@LaunchedEffect
        if (target !in cards.indices) return@LaunchedEffect // wait until the data has been loaded
        gridState.scrollToItem(target)
        // the item is composed after the scroll; give it a frame to register its requester
        withFrameNanos { }
        requesters[target]?.runCatching { requestFocus() }
        onFocusIndexRequestHandled()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 24.dp),
        modifier = modifier.focusRestorer().focusGroup(),
    ) {
        itemsIndexed(cards) { index, card ->
            val requester = remember { FocusRequester() }
            DisposableEffect(index) {
                requesters[index] = requester
                onDispose { requesters.remove(index) }
            }
            ImmichFocusableCard(
                card = card,
                onClick = { onCardClick(index, card) },
                onFocused = { onCardFocused(index, card) },
                modifier = Modifier.focusRequester(requester),
            )
        }
    }
}
