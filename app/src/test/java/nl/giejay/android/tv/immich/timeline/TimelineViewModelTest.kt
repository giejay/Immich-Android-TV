package nl.giejay.android.tv.immich.timeline

import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.giejay.android.tv.immich.api.model.TimeBucketSummary
import nl.giejay.android.tv.immich.api.model.TimelineAsset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun asset(
        id: String,
        createdAt: String = "2026-07-01T12:00:00Z",
        localOffsetHours: Double = 0.0
    ) = TimelineAsset(
        id = id,
        ratio = 1.0,
        isFavorite = false,
        isImage = true,
        thumbhash = null,
        fileCreatedAt = OffsetDateTime.parse(createdAt),
        localOffsetHours = localOffsetHours,
        duration = null
    )

    @Test
    fun `loadBucketList caches buckets and eagerly loads first months`() = runTest {
        val bucketFetches = AtomicInteger(0)
        val vm = TimelineViewModel(
            fetchBuckets = {
                Either.Right(
                    listOf(
                        TimeBucketSummary("2026-07-01", 2),
                        TimeBucketSummary("2026-06-01", 1),
                        TimeBucketSummary("2026-05-01", 1),
                        TimeBucketSummary("2026-04-01", 1)
                    )
                )
            },
            fetchBucket = { key ->
                bucketFetches.incrementAndGet()
                Either.Right(listOf(asset("$key-a")))
            },
            prefetchDebounceMs = 0
        )

        vm.loadBucketList(eagerMonths = 3)
        advanceUntilIdle()

        assertEquals(4, vm.buckets.value.size)
        assertEquals(3, bucketFetches.get())
        assertTrue(vm.bucketAssets.value.containsKey("2026-07-01"))
        assertTrue(vm.bucketAssets.value.containsKey("2026-06-01"))
        assertTrue(vm.bucketAssets.value.containsKey("2026-05-01"))
        assertTrue("2026-04-01" !in vm.bucketAssets.value)
    }

    @Test
    fun `loadBucket skips duplicate in-flight fetches`() = runTest {
        val fetches = AtomicInteger(0)
        val vm = TimelineViewModel(
            fetchBuckets = { Either.Right(listOf(TimeBucketSummary("2026-07-01", 1))) },
            fetchBucket = {
                fetches.incrementAndGet()
                delay(50)
                Either.Right(listOf(asset("a")))
            },
            prefetchDebounceMs = 0
        )

        val first = async { vm.loadBucket("2026-07-01") }
        val second = async { vm.loadBucket("2026-07-01") }
        advanceUntilIdle()
        assertTrue(first.await().isRight())
        assertTrue(second.await().isRight())
        assertEquals(1, fetches.get())
    }

    @Test
    fun `days groups assets by local calendar day newest first`() = runTest {
        val vm = TimelineViewModel(
            fetchBuckets = {
                Either.Right(
                    listOf(
                        TimeBucketSummary("2026-07-01", 3),
                        TimeBucketSummary("2026-06-01", 1)
                    )
                )
            },
            fetchBucket = { key ->
                Either.Right(
                    when (key) {
                        "2026-07-01" -> listOf(
                            asset("jul-2", "2026-07-02T18:00:00Z"),
                            asset("jul-1b", "2026-07-01T15:00:00Z"),
                            asset("jul-1a", "2026-07-01T10:00:00Z")
                        )
                        else -> listOf(asset("jun-1", "2026-06-15T12:00:00Z"))
                    }
                )
            },
            prefetchDebounceMs = 0
        )

        vm.loadBucketList(eagerMonths = 2)
        advanceUntilIdle()

        assertEquals(
            listOf("2026-07-02", "2026-07-01", "2026-06-15"),
            vm.days.value.map { it.dayKey }
        )
        assertEquals(listOf("jul-1b", "jul-1a"), vm.days.value[1].assets.map { it.id })
        assertEquals(
            listOf("jul-2", "jul-1b", "jul-1a", "jun-1"),
            vm.flatAssetIndex().map { it.second.id }
        )
        assertEquals(
            listOf("2026-07-02", "2026-07-01", "2026-07-01", "2026-06-15"),
            vm.flatAssetIndex().map { it.first }
        )
    }

    @Test
    fun `nextUnloadedBucket returns first missing month`() = runTest {
        val vm = TimelineViewModel(
            fetchBuckets = {
                Either.Right(
                    listOf(
                        TimeBucketSummary("2026-07-01", 1),
                        TimeBucketSummary("2026-06-01", 1)
                    )
                )
            },
            fetchBucket = { Either.Right(listOf(asset("a"))) },
            prefetchDebounceMs = 0
        )

        vm.loadBucketList(eagerMonths = 1)
        advanceUntilIdle()

        assertEquals("2026-06-01", vm.nextUnloadedBucket()?.timeBucket)
    }

    @Test
    fun `nextOlderUnloadedBucket extends past oldest loaded not first global gap`() = runTest {
        val vm = TimelineViewModel(
            fetchBuckets = {
                Either.Right(
                    listOf(
                        TimeBucketSummary("2026-07-01", 1),
                        TimeBucketSummary("2026-06-01", 1),
                        TimeBucketSummary("2026-05-01", 1),
                        TimeBucketSummary("2005-03-01", 1),
                        TimeBucketSummary("2005-02-01", 1)
                    )
                )
            },
            fetchBucket = { key -> Either.Right(listOf(asset("$key-a"))) },
            prefetchDebounceMs = 0
        )

        vm.loadBucketList(eagerMonths = 1)
        advanceUntilIdle()
        vm.loadBucket("2005-03-01")
        advanceUntilIdle()

        // First global gap is still June 2026, but bottom-of-timeline paging must continue from 2005.
        assertEquals("2026-06-01", vm.nextUnloadedBucket()?.timeBucket)
        assertEquals("2005-02-01", vm.nextOlderUnloadedBucket()?.timeBucket)
    }

    @Test
    fun `nextNewerUnloadedBucket bridges toward today from a historic island`() = runTest {
        val vm = TimelineViewModel(
            fetchBuckets = {
                Either.Right(
                    listOf(
                        TimeBucketSummary("2026-07-01", 1),
                        TimeBucketSummary("2026-06-01", 1),
                        TimeBucketSummary("2026-02-01", 1),
                        TimeBucketSummary("2006-08-01", 1),
                        TimeBucketSummary("2006-07-01", 1)
                    )
                )
            },
            fetchBucket = { key -> Either.Right(listOf(asset("$key-a"))) },
            prefetchDebounceMs = 0
        )

        vm.loadBucketList(eagerMonths = 1)
        advanceUntilIdle()
        vm.loadBucket("2026-02-01")
        vm.loadBucket("2006-07-01")
        advanceUntilIdle()

        // Closest newer unloaded month above July 2006 is August 2006, not a 2026 gap.
        assertEquals("2006-08-01", vm.nextNewerUnloadedBucket("2006-07-15")?.timeBucket)
    }

    @Test
    fun `nextOlderUnloadedBucket from day bridges holes under focus not past oldest island`() =
        runTest {
            val vm = TimelineViewModel(
                fetchBuckets = {
                    Either.Right(
                        listOf(
                            TimeBucketSummary("2026-07-01", 1),
                            TimeBucketSummary("2004-08-01", 1),
                            TimeBucketSummary("2004-07-01", 1),
                            TimeBucketSummary("2004-01-01", 1)
                        )
                    )
                },
                fetchBucket = { key -> Either.Right(listOf(asset("$key-a"))) },
                prefetchDebounceMs = 0
            )

            vm.loadBucketList(eagerMonths = 1)
            advanceUntilIdle()
            vm.loadBucket("2004-08-01")
            vm.loadBucket("2004-01-01")
            advanceUntilIdle()

            // Global oldest-loaded paging would go past January; from-focus must fill July 2004.
            assertEquals("2004-07-01", vm.nextOlderUnloadedBucket("2004-08-21")?.timeBucket)
            assertTrue(vm.hasUnloadedBucketBetween("2004-08-21", "2004-01-01"))
            assertFalse(vm.hasUnloadedBucketBetween("2004-08-21", "2004-08-01"))
        }

    @Test
    fun `hasUnloadedBucketBetween is false for true library sparsity`() = runTest {
        val vm = TimelineViewModel(
            fetchBuckets = {
                Either.Right(
                    listOf(
                        TimeBucketSummary("2004-08-01", 1),
                        TimeBucketSummary("2004-01-01", 1)
                    )
                )
            },
            fetchBucket = { key -> Either.Right(listOf(asset("$key-a"))) },
            prefetchDebounceMs = 0
        )
        vm.loadBucketList(eagerMonths = 2)
        advanceUntilIdle()

        assertFalse(vm.hasUnloadedBucketBetween("2004-08-21", "2004-01-01"))
        assertNull(vm.nextOlderUnloadedBucket("2004-08-21"))
    }

    @Test
    fun `nextOlder and nextNewer return null when contiguous island has no gap`() = runTest {
        val vm = TimelineViewModel(
            fetchBuckets = {
                Either.Right(
                    listOf(
                        TimeBucketSummary("2026-07-01", 1),
                        TimeBucketSummary("2026-06-01", 1),
                        TimeBucketSummary("2026-05-01", 1)
                    )
                )
            },
            fetchBucket = { key -> Either.Right(listOf(asset("$key-a"))) },
            prefetchDebounceMs = 0
        )
        vm.loadBucketList(eagerMonths = 3)
        advanceUntilIdle()

        assertNull(vm.nextOlderUnloadedBucket())
        assertNull(vm.nextNewerUnloadedBucket("2026-05-15"))
        assertNull(vm.nextUnloadedBucket())
    }

    @Test
    fun `nextBucketAfter pages older than slider cursor never first global gap`() = runTest {
        val vm = TimelineViewModel(
            fetchBuckets = {
                Either.Right(
                    listOf(
                        TimeBucketSummary("2026-07-01", 1),
                        TimeBucketSummary("2026-06-01", 1),
                        TimeBucketSummary("2026-05-01", 1),
                        TimeBucketSummary("2005-03-01", 1)
                    )
                )
            },
            fetchBucket = { key -> Either.Right(listOf(asset("$key-a"))) },
            prefetchDebounceMs = 0
        )
        vm.loadBucketList(eagerMonths = 1)
        advanceUntilIdle()
        vm.loadBucket("2005-03-01")
        advanceUntilIdle()

        // Global gap is still June; slider at historic island must continue to May (via after Jul? No —
        // after 2005-03 there is nothing). After June when only Jul loaded: May is not next after Jul.
        assertEquals("2026-06-01", vm.nextBucketAfter("2026-07-01")?.timeBucket)
        assertEquals("2026-05-01", vm.nextBucketAfter("2026-06-01")?.timeBucket)
        assertNull(vm.nextBucketAfter("2005-03-01"))
        assertNull(vm.nextBucketAfter("missing"))
    }

    @Test
    fun `rememberSelection and rememberSelectionByAssetId round trip`() = runTest {
        val vm = TimelineViewModel(
            fetchBuckets = {
                Either.Right(listOf(TimeBucketSummary("2026-07-01", 2)))
            },
            fetchBucket = {
                Either.Right(
                    listOf(
                        asset("a", "2026-07-02T12:00:00Z"),
                        asset("b", "2026-07-01T12:00:00Z")
                    )
                )
            },
            prefetchDebounceMs = 0
        )
        vm.loadBucketList(eagerMonths = 1)
        advanceUntilIdle()

        vm.rememberSelection("2026-07-02", "a")
        assertEquals("2026-07-02", vm.lastSelectedDayKey)
        assertEquals("a", vm.lastSelectedAssetId)

        vm.pendingResumeAssetId = "a"
        assertEquals("a", vm.pendingResumeAssetId)
        vm.pendingResumeAssetId = null
        assertNull(vm.pendingResumeAssetId)

        vm.rememberSelectionByAssetId("b")
        assertEquals("2026-07-01", vm.lastSelectedDayKey)
        assertEquals("b", vm.lastSelectedAssetId)
    }

    @Test
    fun `slider open then advance leave-off restores last viewed asset`() {
        val vm = TimelineViewModel(
            fetchBuckets = { Either.Right(emptyList()) },
            fetchBucket = { Either.Right(emptyList()) },
            prefetchDebounceMs = 0
        )

        // Opening a mosaic item (TimelineFragment.onItemClicked).
        vm.applyLeaveOffSnapshot(TimelineLeaveOff.afterOpeningMosaic("asset-open"))
        assertEquals(
            TimelineLeaveOff.Target.Mosaic("asset-open", allowScrollAdjust = false),
            TimelineLeaveOff.resolveRestore(vm.leaveOffSnapshot())
        )

        // Advancing in the slider (MediaSliderConfiguration.onAssetSelected wiring).
        vm.applyLeaveOffSnapshot(TimelineLeaveOff.afterOpeningMosaic("asset-later"))
        vm.rememberSelectionByAssetId("asset-later") // no-op without buckets; dayKey unset
        assertEquals(
            TimelineLeaveOff.Target.Mosaic("asset-later", allowScrollAdjust = false),
            TimelineLeaveOff.resolveRestore(vm.leaveOffSnapshot())
        )
        assertEquals("asset-later", vm.pendingResumeAssetId)
        assertEquals("asset-later", vm.lastSelectedAssetId)
        assertFalse(vm.leaveOffAllowScrollAdjust)
    }

    @Test
    fun `menu capture after Left keeps sticky memory on ViewModel`() {
        val vm = TimelineViewModel(
            fetchBuckets = { Either.Right(emptyList()) },
            fetchBucket = { Either.Right(emptyList()) },
            prefetchDebounceMs = 0
        )
        vm.lastSelectedMemoryId = "mem-left"
        vm.lastSelectedAssetId = "asset-a"

        // Headers open: focus already gone (focusedMemory/Mosaic null), sticky remains.
        vm.applyLeaveOffSnapshot(
            TimelineLeaveOff.captureForMenu(
                focusedMemoryId = null,
                focusedMosaicAssetId = null,
                stickyMemoryId = vm.lastSelectedMemoryId,
                lastAssetId = vm.lastSelectedAssetId
            )
        )
        assertEquals(
            TimelineLeaveOff.Target.Memory("mem-left"),
            TimelineLeaveOff.resolveRestore(vm.leaveOffSnapshot())
        )
    }

    @Test
    fun `prefetchAroundDay loads focus month plus neighbors`() = runTest {
        val fetches = AtomicInteger(0)
        val vm = TimelineViewModel(
            fetchBuckets = {
                Either.Right(
                    listOf(
                        TimeBucketSummary("2026-07-01", 1),
                        TimeBucketSummary("2026-06-01", 1),
                        TimeBucketSummary("2026-05-01", 1),
                        TimeBucketSummary("2026-04-01", 1)
                    )
                )
            },
            fetchBucket = { key ->
                fetches.incrementAndGet()
                Either.Right(listOf(asset("$key-a", createdAt = "${key.take(8)}15T12:00:00Z")))
            },
            prefetchDebounceMs = 0
        )
        // Do not eager-load; only populate bucket list.
        vm.loadBucketList(eagerMonths = 0)
        advanceUntilIdle()
        assertEquals(0, fetches.get())

        vm.prefetchAroundDay("2026-06-15")
        // Debouncer runs on its own scheduler; give it a beat then drain Main.
        Thread.sleep(80)
        advanceUntilIdle()

        assertTrue(vm.bucketAssets.value.containsKey("2026-06-01"))
        assertTrue(vm.bucketAssets.value.containsKey("2026-07-01"))
        assertTrue(vm.bucketAssets.value.containsKey("2026-05-01"))
        assertTrue("2026-04-01" !in vm.bucketAssets.value)
    }

    @Test
    fun `loadMemories sets memoriesReady on success and error`() = runTest {
        val ok = TimelineViewModel(
            fetchBuckets = { Either.Right(emptyList()) },
            fetchBucket = { Either.Right(emptyList()) },
            fetchMemories = {
                Either.Right(
                    listOf(
                        nl.giejay.android.tv.immich.api.model.Memory(
                            id = "m1",
                            type = "on_this_day",
                            memoryAt = java.util.Date(),
                            data = nl.giejay.android.tv.immich.api.model.MemoryData(year = 2020),
                            assets = emptyList()
                        )
                    )
                )
            },
            prefetchDebounceMs = 0
        )
        ok.loadMemories()
        advanceUntilIdle()
        assertTrue(ok.memoriesReady.value)
        assertEquals(1, ok.memories.value.size)

        val fail = TimelineViewModel(
            fetchBuckets = { Either.Right(emptyList()) },
            fetchBucket = { Either.Right(emptyList()) },
            fetchMemories = { Either.Left("boom") },
            prefetchDebounceMs = 0
        )
        fail.loadMemories()
        advanceUntilIdle()
        assertTrue(fail.memoriesReady.value)
        assertTrue(fail.memories.value.isEmpty())
        assertEquals("boom", fail.error.value)
    }

    @Test
    fun `loadBucketList is idempotent`() = runTest {
        val fetches = AtomicInteger(0)
        val vm = TimelineViewModel(
            fetchBuckets = {
                fetches.incrementAndGet()
                Either.Right(listOf(TimeBucketSummary("2026-07-01", 1)))
            },
            fetchBucket = { Either.Right(listOf(asset("a"))) },
            prefetchDebounceMs = 0
        )
        vm.loadBucketList(eagerMonths = 1)
        advanceUntilIdle()
        vm.loadBucketList(eagerMonths = 1)
        advanceUntilIdle()
        assertEquals(1, fetches.get())
    }

    @Test
    fun `bucketKeyForAsset and resolveBucketKey fuzzy YearMonth`() = runTest {
        val vm = TimelineViewModel(
            fetchBuckets = {
                Either.Right(listOf(TimeBucketSummary("2022-01-01", 1)))
            },
            fetchBucket = {
                Either.Right(listOf(asset("x", "2022-01-15T12:00:00Z")))
            },
            prefetchDebounceMs = 0
        )
        vm.loadBucketList(eagerMonths = 1)
        advanceUntilIdle()

        assertEquals("2022-01-01", vm.bucketKeyForAsset("x"))
        assertEquals("2022-01-01", vm.resolveBucketKey("2022-01"))
        assertEquals("2022-01-01", vm.resolveBucketKey("2022-01-15"))
    }

    @Test
    fun `monthBucketKey uses first of month`() {
        assertEquals(
            "2026-07-01",
            TimelineViewModel.monthBucketKey(java.time.LocalDate.of(2026, 7, 13))
        )
    }

    @Test
    fun `newestDayKeyForBucket uses local capture date not UTC month label`() = runTest {
        val vm = TimelineViewModel(
            fetchBuckets = {
                Either.Right(listOf(TimeBucketSummary("2022-01-01", 1)))
            },
            fetchBucket = {
                // UTC Jan 1 05:00 with UTC-8 → local Dec 31 2021 — common scrubber miss.
                Either.Right(
                    listOf(
                        asset(
                            id = "nye",
                            createdAt = "2022-01-01T05:00:00Z",
                            localOffsetHours = -8.0
                        )
                    )
                )
            },
            prefetchDebounceMs = 0
        )

        vm.loadBucketList(eagerMonths = 1)
        advanceUntilIdle()

        assertEquals("2021-12-31", vm.newestDayKeyForBucket("2022-01-01"))
        assertEquals("2022-01-01", vm.resolveBucketKey("2022-01"))
    }
}
