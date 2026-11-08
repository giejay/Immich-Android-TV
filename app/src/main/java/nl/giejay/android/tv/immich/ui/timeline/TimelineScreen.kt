package nl.giejay.android.tv.immich.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nl.giejay.android.tv.immich.timeline.TimelineFocusAction
import nl.giejay.android.tv.immich.timeline.TimelineFocusActions
import nl.giejay.android.tv.immich.timeline.TimelineMosaicIndex
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.model.Memory
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.timeline.TimelineLeaveOff
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
    onOpenAsset: (String) -> Unit,
    onOpenMemory: (Memory) -> Unit,
    /** Bumped by the host (resume, side navigation closed) to put focus back where the user left off. */
    restoreRequest: Int,
    isNavFocused: () -> Boolean,
    /** True while the host fragment is paused (app in background): the preview must not keep playing. */
    hostPaused: Boolean,
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
    val memoriesState = rememberLazyListState()
    // one muted player shared by the focus preview of video cells
    val context = LocalContext.current
    val previewPlayer = remember {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    DisposableEffect(previewPlayer) { onDispose { previewPlayer.release() } }
    LaunchedEffect(hostPaused) {
        if (hostPaused) previewPlayer.playWhenReady = false
        else if (previewPlayer.mediaItemCount > 0) previewPlayer.playWhenReady = true
    }
    val buckets by viewModel.buckets.collectAsState()
    var indicatorMonth by remember { mutableStateOf<String?>(null) }
    var scrubberFocused by remember { mutableStateOf(false) }
    val scrubberRequester = remember { FocusRequester() }
    // Starts pending: a freshly composed screen (after the slider, or a page switch) has to restore first, and the
    // entry cell that grabs focus before that must not overwrite the leave-off. With nothing saved it ends at once.
    var pendingRestore by remember { mutableStateOf(true) }
    // Set while an item is opened in the slider: the screen stays composed underneath, and the entry cell that
    // takes focus on the way back must not overwrite the leave-off, nor may the restore run before we are back.
    var leftForSlider by remember { mutableStateOf(false) }
    val openAsset: (String) -> Unit = { id ->
        leftForSlider = true
        pendingRestore = true
        onOpenAsset(id)
    }
    val openMemory: (Memory) -> Unit = { memory ->
        leftForSlider = true
        pendingRestore = true
        onOpenMemory(memory)
    }
    // the slider may not open at all (e.g. the asset's month is unknown): do not block the restore forever
    LaunchedEffect(leftForSlider) {
        if (leftForSlider) {
            delay(SLIDER_OPEN_GRACE_MILLIS)
            if (!hostPaused) leftForSlider = false
        }
    }
    val jumpGeneration = remember { java.util.concurrent.atomic.AtomicInteger() }
    val memoriesReady by viewModel.memoriesReady.collectAsState()

    val anchorGeneration = remember { java.util.concurrent.atomic.AtomicInteger() }

    /**
     * Keeps the list item with [listKey] at the top for a moment. Months load around a jump or restore
     * target (focus prefetches neighbors) and shift indices; an index-based scroll would then land on other
     * content. Ends when the user moves focus (see the focus handlers) or a newer anchor takes over.
     */
    fun anchorTo(listKey: String, offset: Int = 0) {
        scope.launch {
            withFrameNanos { } // let a focus change caused by this very scroll pass first
            val generation = anchorGeneration.incrementAndGet()
            withTimeoutOrNull(JUMP_ANCHOR_MILLIS) {
                snapshotFlow { currentLayout.items }.takeWhile { generation == anchorGeneration.get() }.collect { current ->
                    val index = current.indexOfFirst { it.listKey() == listKey }
                    if (index >= 0 && (listState.firstVisibleItemIndex != index || listState.firstVisibleItemScrollOffset != offset)) {
                        listState.scrollToItem(index, offset)
                    }
                }
            }
        }
    }

    /** Focuses [key] (an asset id or "memory:<id>"), scrolling its item into composition first. */
    suspend fun focusKey(key: String, itemPosition: Int, pinTop: Boolean = false) {
        // restore only: remember what is at the top now, so it can be pinned while neighbor months load
        val topKey = currentLayout.items.getOrNull(listState.firstVisibleItemIndex)?.listKey()
        val topOffset = listState.firstVisibleItemScrollOffset
        // The target's list key must be read now: while we wait below for the cell to compose, a prefetch may
        // insert rows above it and make [itemPosition] stale.
        val targetListKey = currentLayout.items.getOrNull(itemPosition)?.listKey()
        var scrolled = false
        // scroll when the target is not composed, or composed but not on screen (e.g. after stray focus
        // pulled the list elsewhere); a target that is already visible keeps the viewport as it is
        val visible = itemPosition >= 0 && listState.layoutInfo.visibleItemsInfo.any { it.index == itemPosition }
        if ((requesters[key] == null || !visible) && itemPosition >= 0) {
            scrolled = true
            listState.scrollToItem(itemPosition)
            if (key.startsWith("memory:")) {
                // the card sits in the horizontal row: scroll that row to it so the card is composed
                val memories = (currentLayout.items.firstOrNull() as? TimelineMosaicItem.MemoriesRow)?.memories.orEmpty()
                val index = memories.indexOfFirst { "memory:${it.id}" == key }
                if (index >= 0) memoriesState.scrollToItem(index)
            }
            repeat(6) {
                if (requesters[key] != null) return@repeat
                withFrameNanos { }
            }
        }
        runCatching { requesters[key]?.requestFocus() }
        if (scrolled) targetListKey?.let { anchorTo(it) }
        else if (pinTop && topKey != null) anchorTo(topKey, topOffset)
    }

    LaunchedEffect(restoreRequest) {
        if (restoreRequest > 0) {
            leftForSlider = false
            pendingRestore = true
        }
    }
    LaunchedEffect(pendingRestore, leftForSlider, restoreRequest, layout.items.size, memoriesReady) {
        if (!pendingRestore || leftForSlider || layout.items.isEmpty() || isNavFocused()) return@LaunchedEffect
        var resolved = TimelineLeaveOff.resolveRestore(viewModel.leaveOffSnapshot())
        if (resolved is TimelineLeaveOff.Target.Memory && !TimelineMosaicIndex.hasMemoriesRow(layout.items)) {
            // still loading: wait; loaded without memories (e.g. the date changed): drop the stale target
            if (!memoriesReady) return@LaunchedEffect
            viewModel.lastSelectedMemoryId = null
            resolved = TimelineLeaveOff.resolveRestore(viewModel.leaveOffSnapshot())
        }
        when (val target = resolved) {
            is TimelineLeaveOff.Target.Memory -> {
                if (!TimelineMosaicIndex.hasMemoriesRow(layout.items)) return@LaunchedEffect
                // clear the flag only after focusing: it keys this effect, so clearing it first would cancel the focus
                focusKey("memory:${target.id}", 0, pinTop = true)
                pendingRestore = false
            }
            is TimelineLeaveOff.Target.Mosaic -> {
                val position = TimelineMosaicIndex.positionOfAsset(layout.items, target.assetId)
                if (position < 0) {
                    // the month of the leave-off asset is not loaded (yet): load it and retry on the next layout
                    viewModel.lastSelectedDayKey?.let { day ->
                        withContext(Dispatchers.IO) {
                            viewModel.loadBucket(TimelineViewModel.monthBucketKey(java.time.LocalDate.parse(day)))
                        }
                    }
                    return@LaunchedEffect
                }
                focusKey(target.assetId, position, pinTop = true)
                viewModel.pendingResumeAssetId = null
                viewModel.rememberSelectionByAssetId(target.assetId)
                viewModel.leaveOffAllowScrollAdjust = true
                pendingRestore = false
            }
            TimelineLeaveOff.Target.None -> pendingRestore = false
        }
    }

    /** Scrolls the list to [monthKey] (loading that month first) and optionally moves focus into the mosaic. */
    suspend fun jumpToMonth(monthKey: String, exitToMosaic: Boolean) {
        // only the latest commit may scroll, focus or re-anchor; older ones stop at their next check
        val generation = jumpGeneration.incrementAndGet()
        fun superseded() = generation != jumpGeneration.get()
        val bucketKey = viewModel.resolveBucketKey(monthKey) ?: monthKey
        if (bucketKey !in viewModel.bucketAssets.value.keys) {
            withContext(Dispatchers.IO) { viewModel.loadBucket(bucketKey) }
        }
        if (superseded()) return
        val preferredDay = viewModel.newestDayKeyForBucket(bucketKey)
        val items = withTimeoutOrNull(4000) {
            snapshotFlow { currentLayout.items }
                .first { TimelineMosaicIndex.positionForScrubberMonth(it, monthKey, preferredDay) >= 0 }
        } ?: return
        if (superseded()) return
        val position = TimelineMosaicIndex.positionForScrubberMonth(items, monthKey, preferredDay)
        listState.scrollToItem(position)
        indicatorMonth = bucketKey
        val assetId = preferredDay?.let { TimelineMosaicIndex.rightmostAssetIdForDay(items, it) }
            ?: items.drop(position).filterIsInstance<TimelineMosaicItem.Row>().firstOrNull()?.cells?.firstOrNull()?.asset?.id
        if (assetId != null) {
            // The jumped month is the new leave-off, also when focus stays on the rail (Enter): opening and
            // closing the side navigation must not scroll back to the cell selected before the jump.
            viewModel.applyLeaveOffSnapshot(
                TimelineLeaveOff.captureForMenu(
                    focusedMemoryId = null,
                    focusedMosaicAssetId = assetId,
                    stickyMemoryId = null,
                    lastAssetId = assetId,
                )
            )
            viewModel.rememberSelectionByAssetId(assetId)
        }
        if (exitToMosaic && assetId != null) {
            focusKey(assetId, TimelineMosaicIndex.positionOfAsset(items, assetId))
        }
        // Newer/older months load around the target (focus prefetches them) and shift indices:
        // keep the target month's heading at the top for a moment, by key instead of by index.
        items.getOrNull(position)?.listKey()?.let { anchorTo(it) }
    }

    // Keep the rail's accent on the month that is on screen (unless the user is scrubbing).
    LaunchedEffect(listState, viewModel) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { first ->
            if (scrubberFocused) return@collect
            val dayKey = when (val item = currentLayout.items.getOrNull(first)) {
                is TimelineMosaicItem.Header -> item.dayKey
                is TimelineMosaicItem.Row -> item.dayKey
                else -> null
            } ?: return@collect
            runCatching { TimelineViewModel.monthBucketKey(java.time.LocalDate.parse(dayKey)) }.getOrNull()
                ?.let { indicatorMonth = it }
        }
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
            TimelineFocusAction.ExitScrubber -> {
                runCatching { scrubberRequester.requestFocus() }
                true
            }
            TimelineFocusAction.LoadOlder -> {
                scope.launch {
                    // fill the gap right below the focused day first (separate loaded islands), else extend the end
                    val next = viewModel.nextOlderUnloadedBucket(cell.dayKey) ?: viewModel.nextOlderUnloadedBucket() ?: return@launch
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

    // Load the month list and memories, like the View timeline does on creation; Retry runs it again.
    var initialLoadDone by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    // the host toasts and clears the error, so keep our own copy for the message shown in the empty state
    LaunchedEffect(viewModel) { viewModel.error.collect { if (it != null) loadError = it } }
    LaunchedEffect(viewModel, loadAttempt) {
        initialLoadDone = false
        loadError = null
        viewModel.clearError()
        withContext(Dispatchers.IO) { viewModel.loadBucketList() }
        withContext(Dispatchers.IO) { viewModel.loadMemories() }
        initialLoadDone = true
        if (loadAttempt > 0) {
            // After Retry the button that held focus is gone: put focus on the first card or photo again.
            val items = withTimeoutOrNull(3000) { snapshotFlow { currentLayout.items }.first { it.isNotEmpty() } } ?: return@LaunchedEffect
            val firstMemory = (items.firstOrNull() as? TimelineMosaicItem.MemoriesRow)?.memories?.firstOrNull()
            if (firstMemory != null) {
                focusKey("memory:${firstMemory.id}", 0)
            } else {
                items.filterIsInstance<TimelineMosaicItem.Row>().firstOrNull()?.cells?.firstOrNull()?.let {
                    focusKey(it.asset.id, TimelineMosaicIndex.positionOfAsset(items, it.asset.id))
                }
            }
        }
    }

    // Load older months while the user nears the end of what is loaded.
    LaunchedEffect(listState, viewModel) {
        // keyed by the item count too: a small month can load while the viewport stays "near the end"
        snapshotFlow {
            val info = listState.layoutInfo
            val nearEnd = info.visibleItemsInfo.lastOrNull()?.index?.let { it >= info.totalItemsCount - NEAR_END_ITEMS } ?: false
            nearEnd to info.totalItemsCount
        }.collect { (nearEnd, _) ->
            if (nearEnd) {
                val next = viewModel.nextOlderUnloadedBucket() ?: return@collect
                withContext(Dispatchers.IO) { viewModel.loadBucket(next.timeBucket) }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
    if (layout.items.isEmpty()) {
        // nothing to list yet: loading, empty library, or the load failed
        val stateModifier = Modifier.align(Alignment.Center)
        when {
            !initialLoadDone -> TimelineLoadingIndicator(stateModifier)
            loadError != null -> TimelineMessage(
                title = stringResource(R.string.timeline_error_title),
                body = loadError,
                modifier = stateModifier,
                actionLabel = stringResource(R.string.retry),
                onAction = { loadAttempt++ },
            )
            else -> TimelineMessage(
                title = stringResource(R.string.timeline_empty_title),
                body = stringResource(R.string.timeline_empty_body),
                modifier = stateModifier,
            )
        }
    }
    // Below the list so focused cells cover the year labels; raised above it while the rail has focus.
    TimelineScrubber(
        buckets = buckets,
        indicatorMonthKey = indicatorMonth,
        onCommit = { month, exit -> scope.launch { jumpToMonth(month, exit) } },
        focusRequester = scrubberRequester,
        onFocusedChange = { scrubberFocused = it },
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .width(112.dp)
            .fillMaxHeight()
            .zIndex(if (scrubberFocused) 2f else 0f),
    )
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
                                previewPlayer = previewPlayer,
                                modifier = Modifier
                                    .focusRequester(requester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            anchorGeneration.incrementAndGet()
                                            focusedAssetId = cell.asset.id
                                            viewModel.bucketKeyForAsset(cell.asset.id)?.let { indicatorMonth = it }
                                            viewModel.prefetchAroundDay(cell.dayKey)
                                            // stray focus before the restore has run must not overwrite the leave-off
                                            if (!pendingRestore) {
                                                viewModel.lastSelectedMemoryId = null
                                                // a pending target (e.g. from a scrubber jump) is outdated once the user moves on
                                                viewModel.pendingResumeAssetId = null
                                                viewModel.rememberSelection(cell.dayKey, cell.asset.id)
                                            }
                                        } else if (focusedAssetId == cell.asset.id) {
                                            focusedAssetId = null
                                        }
                                    }
                                    .onPreviewKeyEvent { event ->
                                        event.type == KeyEventType.KeyDown && onCellKey(cell, event.key.nativeKeyCode)
                                    }
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        openAsset(cell.asset.id)
                                    },
                            )
                        }
                    }
                }
                is TimelineMosaicItem.MemoriesRow -> MemoriesRow(item, requesters, viewModel, openMemory, memoriesState, liveLeaveOff = !pendingRestore)
            }
        }
    }
    }
}

@Composable
private fun MemoriesRow(
    row: TimelineMosaicItem.MemoriesRow,
    requesters: MutableMap<String, FocusRequester>,
    viewModel: TimelineViewModel,
    onOpenMemory: (Memory) -> Unit,
    state: LazyListState,
    liveLeaveOff: Boolean,
) {
    val context = LocalContext.current
    var focusedId by remember { mutableStateOf<String?>(null) }
    LazyRow(
        state = state,
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
                            if (liveLeaveOff) viewModel.lastSelectedMemoryId = memory.id
                        } else if (focusedId == memory.id) {
                            focusedId = null
                        }
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        onOpenMemory(memory)
                    },
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
private const val JUMP_ANCHOR_MILLIS = 1500L
private const val SLIDER_OPEN_GRACE_MILLIS = 2500L
private val FocusOverflow = 24.dp
