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

    private fun asset(id: String) = TimelineAsset(
        id = id,
        ratio = 1.0,
        isFavorite = false,
        isImage = true,
        thumbhash = null,
        fileCreatedAt = OffsetDateTime.parse("2026-07-01T12:00:00Z"),
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
    fun `flatAssetIndex follows bucket order then in-bucket order`() = runTest {
        val vm = TimelineViewModel(
            fetchBuckets = {
                Either.Right(
                    listOf(
                        TimeBucketSummary("2026-07-01", 2),
                        TimeBucketSummary("2026-06-01", 1)
                    )
                )
            },
            fetchBucket = { key ->
                Either.Right(
                    when (key) {
                        "2026-07-01" -> listOf(asset("jul-1"), asset("jul-2"))
                        else -> listOf(asset("jun-1"))
                    }
                )
            },
            prefetchDebounceMs = 0
        )

        vm.loadBucketList(eagerMonths = 2)
        advanceUntilIdle()

        assertEquals(
            listOf("jul-1", "jul-2", "jun-1"),
            vm.flatAssetIndex().map { it.second.id }
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
}
