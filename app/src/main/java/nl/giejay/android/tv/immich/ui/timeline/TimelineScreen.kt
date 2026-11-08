package nl.giejay.android.tv.immich.ui.timeline

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import nl.giejay.android.tv.immich.timeline.TimelineFocusAction
import nl.giejay.android.tv.immich.timeline.TimelineFocusActions
import nl.giejay.android.tv.immich.timeline.TimelineMosaicIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.timeline.MemoriesRowAdapter
import nl.giejay.android.tv.immich.timeline.TimelineMosaicCell
import nl.giejay.android.tv.immich.timeline.TimelineMosaicItem
import nl.giejay.android.tv.immich.timeline.TimelineViewModel

/** Same insets the View timeline gives its RecyclerView, so rows get the same content width. */
private val ContentPaddingStart = 40.dp
private val ContentPaddingEnd = 64.dp

/**
 * The timeline as a Compose list (step K2): day headers, mosaic rows from the layout engine and
 * the memories row. Layout, paging and data come from the shared [TimelineViewModel]; focus
 * handling, the scrubber, video preview and opening items follow in later steps.
 */
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    rowHeightPx: Int,
    gapPx: Int,
    todayLabel: String,
    yesterdayLabel: String,
    modifier: Modifier = Modifier,
) {
    val layout by viewModel.layout.collectAsState()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var focusedAssetId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val currentLayout by rememberUpdatedState(layout)
    // Cells register their FocusRequester while composed, so the neighbor map can target them.
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val loadingNewer = remember { mutableStateOf(false) }

    /** Focuses [key] (an asset id or "memory:<id>"), scrolling its item into composition first. */
    suspend fun focusKey(key: String, itemPosition: Int) {
        if (requesters[key] == null && itemPosition >= 0) {
            listState.scrollToItem(itemPosition)
            repeat(6) {
                if (requesters[key] != null) return@repeat
                withFrameNanos { }
            }
        }
        runCatching { requesters[key]?.requestFocus() }
    }

    fun focusMemories() {
        val items = currentLayout.items
        val memories = items.firstOrNull() as? TimelineMosaicItem.MemoriesRow ?: return
        val first = memories.memories.firstOrNull() ?: return
        scope.launch { focusKey("memory:${first.id}", 0) }
    }

    /** D-pad on a mosaic cell: same outcomes as the View timeline, via the tested neighbor map. */
    fun onCellKey(cell: TimelineMosaicCell, keyCode: Int): Boolean {
        val layoutNow = currentLayout
        return when (val action = TimelineFocusActions.resolve(keyCode, layoutNow.neighbors[cell.asset.id])) {
            is TimelineFocusAction.Move -> {
                val position = TimelineMosaicIndex.positionOfAsset(layoutNow.items, action.assetId)
                scope.launch { focusKey(action.assetId, position) }
                true
            }
            // the scrubber arrives with step K4; keep focus where it is meanwhile
            TimelineFocusAction.ExitScrubber -> true
            TimelineFocusAction.LoadOlder -> {
                scope.launch {
                    val next = viewModel.nextOlderUnloadedBucket() ?: return@launch
                    withContext(Dispatchers.IO) { viewModel.loadBucket(next.timeBucket) }
                }
                true
            }
            TimelineFocusAction.LoadNewer -> {
                if (TimelineMosaicIndex.hasMemoriesRow(layoutNow.items) &&
                    TimelineMosaicIndex.isInFirstMosaicRow(layoutNow.items, cell.asset.id)
                ) {
                    focusMemories()
                    true
                } else {
                    val next = viewModel.nextNewerUnloadedBucket(cell.dayKey)
                    if (next == null || loadingNewer.value) {
                        next != null
                    } else {
                        loadingNewer.value = true
                        scope.launch {
                            withContext(Dispatchers.IO) { viewModel.loadBucket(next.timeBucket) }
                            loadingNewer.value = false
                        }
                        true
                    }
                }
            }
            TimelineFocusAction.Pass, TimelineFocusAction.Ignore -> false
        }
    }

    val startPx = with(density) { ContentPaddingStart.roundToPx() }
    val endPx = with(density) { ContentPaddingEnd.roundToPx() }
    val gap = with(density) { gapPx.toDp() }

    // Load the month list and memories once, like the View timeline does on creation.
    LaunchedEffect(viewModel) {
        withContext(Dispatchers.IO) { viewModel.loadBucketList() }
        withContext(Dispatchers.IO) { viewModel.loadMemories() }
    }

    // Load older months while the user nears the end of what is loaded.
    LaunchedEffect(listState, viewModel) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.visibleItemsInfo.lastOrNull()?.index?.let { it >= info.totalItemsCount - NEAR_END_ITEMS } ?: false
        }.collect { nearEnd ->
            if (nearEnd) {
                val next = viewModel.nextOlderUnloadedBucket() ?: return@collect
                withContext(Dispatchers.IO) { viewModel.loadBucket(next.timeBucket) }
            }
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = ContentPaddingStart, top = 28.dp, end = ContentPaddingEnd, bottom = 48.dp),
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                val width = (size.width - startPx - endPx).coerceAtLeast(1)
                viewModel.setLayoutConfig(width, rowHeightPx, gapPx, todayLabel, yesterdayLabel)
                viewModel.triggerRecomputeLayout()
            },
    ) {
        items(layout.items, key = { it.listKey() }) { item ->
            when (item) {
                is TimelineMosaicItem.Header -> TimelineDayHeader(item.label)
                is TimelineMosaicItem.Row -> {
                    val rowHasFocus = item.cells.any { it.asset.id == focusedAssetId }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        modifier = Modifier
                            .padding(top = 8.dp, bottom = 12.dp)
                            .zIndex(if (rowHasFocus) 1f else 0f),
                    ) {
                        item.cells.forEach { cell ->
                            val requester = remember(cell.asset.id) { FocusRequester() }
                            DisposableEffect(cell.asset.id) {
                                requesters[cell.asset.id] = requester
                                onDispose { if (requesters[cell.asset.id] === requester) requesters.remove(cell.asset.id) }
                            }
                            TimelineMosaicCell(
                                cell = cell,
                                focused = cell.asset.id == focusedAssetId,
                                modifier = Modifier
                                    .focusRequester(requester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            focusedAssetId = cell.asset.id
                                            viewModel.lastSelectedMemoryId = null
                                            viewModel.prefetchAroundDay(cell.dayKey)
                                            viewModel.rememberSelection(cell.dayKey, cell.asset.id)
                                        } else if (focusedAssetId == cell.asset.id) {
                                            focusedAssetId = null
                                        }
                                    }
                                    .onPreviewKeyEvent { event ->
                                        event.type == KeyEventType.KeyDown && onCellKey(cell, event.key.nativeKeyCode)
                                    }
                                    .focusable(),
                            )
                        }
                    }
                }
                is TimelineMosaicItem.MemoriesRow -> MemoriesRow(item, requesters, viewModel)
            }
        }
    }
}

@Composable
private fun MemoriesRow(
    row: TimelineMosaicItem.MemoriesRow,
    requesters: MutableMap<String, FocusRequester>,
    viewModel: TimelineViewModel,
) {
    val context = LocalContext.current
    var focusedId by remember { mutableStateOf<String?>(null) }
    LazyRow(
        // The row clips its content: start it left of the mosaic and pad it back, so the focus
        // scale of the first card has room to grow instead of being cut off.
        contentPadding = PaddingValues(start = FocusOverflow, top = 24.dp, bottom = 24.dp, end = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.offset(x = -FocusOverflow),
    ) {
        items(row.memories, key = { it.id }) { memory ->
            val requester = remember(memory.id) { FocusRequester() }
            DisposableEffect(memory.id) {
                requesters["memory:${memory.id}"] = requester
                onDispose { if (requesters["memory:${memory.id}"] === requester) requesters.remove("memory:${memory.id}") }
            }
            TimelineMemoryCard(
                coverUrl = ApiUtil.getThumbnailUrl(memory.assets.firstOrNull()?.id, "thumbnail"),
                label = MemoriesRowAdapter.yearsAgoLabel(context, memory),
                focused = memory.id == focusedId,
                modifier = Modifier
                    .focusRequester(requester)
                    .onFocusChanged {
                        if (it.isFocused) {
                            focusedId = memory.id
                            viewModel.lastSelectedMemoryId = memory.id
                        } else if (focusedId == memory.id) {
                            focusedId = null
                        }
                    }
                    .focusable(),
            )
        }
    }
}

private fun TimelineMosaicItem.listKey(): String = when (this) {
    is TimelineMosaicItem.Header -> "header:$dayKey"
    is TimelineMosaicItem.Row -> "row:$rowId"
    is TimelineMosaicItem.MemoriesRow -> "memories"
}

private const val NEAR_END_ITEMS = 6
private val FocusOverflow = 24.dp
