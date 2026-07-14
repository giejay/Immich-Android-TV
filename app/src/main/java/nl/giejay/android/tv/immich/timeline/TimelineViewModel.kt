package nl.giejay.android.tv.immich.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.giejay.android.tv.immich.api.model.Memory
import nl.giejay.android.tv.immich.api.model.TimeBucketSummary
import nl.giejay.android.tv.immich.api.model.TimelineAsset
import nl.giejay.android.tv.immich.shared.util.Debouncer
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Holds timeline month buckets + lazily-fetched assets, exposed as day rows for the UI.
 */
class TimelineViewModel(
    private val fetchBuckets: suspend () -> Either<String, List<TimeBucketSummary>>,
    private val fetchBucket: suspend (String) -> Either<String, List<TimelineAsset>>,
    private val fetchMemories: suspend () -> Either<String, List<Memory>> = { Either.Right(emptyList()) },
    private val prefetchDebounceMs: Long = PREFETCH_DEBOUNCE_MS
) : ViewModel() {

    private val _buckets = MutableStateFlow<List<TimeBucketSummary>>(emptyList())
    val buckets: StateFlow<List<TimeBucketSummary>> = _buckets.asStateFlow()

    private val _memories = MutableStateFlow<List<Memory>>(emptyList())
    val memories: StateFlow<List<Memory>> = _memories.asStateFlow()

    /** True after [loadMemories] finishes (even when the list is empty). */
    private val _memoriesReady = MutableStateFlow(false)
    val memoriesReady: StateFlow<Boolean> = _memoriesReady.asStateFlow()

    private val _bucketAssets = MutableStateFlow<Map<String, List<TimelineAsset>>>(emptyMap())
    val bucketAssets: StateFlow<Map<String, List<TimelineAsset>>> = _bucketAssets.asStateFlow()

    private val _days = MutableStateFlow<List<TimelineDay>>(emptyList())
    val days: StateFlow<List<TimelineDay>> = _days.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val inFlight = ConcurrentHashMap<String, Deferred<Either<String, List<TimelineAsset>>>>()

    /** Survives TimelineFragment recreation when returning from the photo slider. */
    var lastSelectedDayKey: String? = null
    var lastSelectedAssetId: String? = null

    suspend fun loadBucketList(eagerMonths: Int = 3) {
        if (_buckets.value.isNotEmpty()) return
        fetchBuckets().fold(
            { message -> _error.value = message },
            { list ->
                _buckets.value = list
                list.take(eagerMonths).forEach { loadBucket(it.timeBucket) }
            }
        )
    }

    suspend fun loadMemories() {
        fetchMemories().fold(
            { message ->
                _error.value = message
                _memoriesReady.value = true
            },
            { list ->
                // Mark ready before publishing the list so bindDays/restore sees both together.
                _memoriesReady.value = true
                _memories.value = list
            }
        )
    }

    fun rememberSelection(dayKey: String, assetId: String) {
        lastSelectedDayKey = dayKey
        lastSelectedAssetId = assetId
    }

    fun rememberSelectionByAssetId(assetId: String) {
        val match = flatAssetIndex().firstOrNull { it.second.id == assetId } ?: return
        rememberSelection(match.first, assetId)
    }

    /**
     * Loads a month's assets if not cached. Concurrent callers for the same bucket share one fetch.
     */
    suspend fun loadBucket(timeBucket: String): Either<String, List<TimelineAsset>> {
        _bucketAssets.value[timeBucket]?.let { return Either.Right(it) }

        val deferred = inFlight.computeIfAbsent(timeBucket) {
            viewModelScope.async(Dispatchers.IO) {
                try {
                    fetchBucket(timeBucket).also { result ->
                        result.fold(
                            { message -> _error.value = message },
                            { assets ->
                                _bucketAssets.update { current -> current + (timeBucket to assets) }
                                recomputeDays()
                            }
                        )
                    }
                } finally {
                    inFlight.remove(timeBucket)
                }
            }
        }
        return deferred.await()
    }

    /**
     * Prefetch the month for [dayKey] plus adjacent months. Debounced for rapid D-pad scrolling.
     */
    fun prefetchAroundDay(dayKey: String) {
        Debouncer.debounce(PREFETCH_KEY, {
            if (!viewModelScope.isActive) return@debounce
            viewModelScope.launch(Dispatchers.IO) {
                val monthKey = monthBucketKey(LocalDate.parse(dayKey))
                val list = _buckets.value
                val resolved = resolveBucketKey(monthKey) ?: monthKey
                val index = list.indexOfFirst { it.timeBucket == resolved }
                if (index < 0) {
                    loadBucket(resolved)
                    return@launch
                }
                loadBucket(resolved)
                list.getOrNull(index - 1)?.let { loadBucket(it.timeBucket) }
                list.getOrNull(index + 1)?.let { loadBucket(it.timeBucket) }
            }
        }, prefetchDebounceMs, TimeUnit.MILLISECONDS)
    }

    fun flatAssetIndex(): List<Pair<String, TimelineAsset>> =
        _days.value.flatMap { day -> day.assets.map { day.dayKey to it } }

    fun nextUnloadedBucket(): TimeBucketSummary? {
        val loaded = _bucketAssets.value
        return _buckets.value.firstOrNull { it.timeBucket !in loaded }
    }

    /**
     * Next older unloaded month after the oldest month we already have assets for.
     *
     * Used when the mosaic is near the bottom (farther back in time). Prefer this over
     * [nextUnloadedBucket], which fills the first gap near "today" and inserts days *above*
     * the viewport — causing jumpy D-pad navigation deep in the timeline.
     */
    fun nextOlderUnloadedBucket(): TimeBucketSummary? {
        val loaded = _bucketAssets.value.keys
        if (loaded.isEmpty()) {
            return _buckets.value.firstOrNull()
        }
        val oldestLoadedIndex = _buckets.value.indexOfLast { it.timeBucket in loaded }
        if (oldestLoadedIndex < 0) {
            return _buckets.value.firstOrNull { it.timeBucket !in loaded }
        }
        return _buckets.value
            .drop(oldestLoadedIndex + 1)
            .firstOrNull { it.timeBucket !in loaded }
    }

    /**
     * Immich UTC month bucket that owns [assetId], if that bucket is loaded.
     * Preferred over local calendar month when syncing the scrubber.
     */
    fun bucketKeyForAsset(assetId: String): String? =
        _bucketAssets.value.entries.firstOrNull { (_, assets) ->
            assets.any { it.id == assetId }
        }?.key

    /**
     * Resolve an Immich bucket key for a scrubber/month token. Matches by calendar
     * [YearMonth] so normalized `YYYY-MM-01` finds API keys that may differ slightly.
     */
    fun resolveBucketKey(monthKey: String): String? {
        val target = runCatching { YearMonth.from(LocalDate.parse(monthKey.take(10))) }.getOrNull()
            ?: runCatching { YearMonth.parse(monthKey.take(7)) }.getOrNull()
            ?: return null
        return _buckets.value.firstOrNull { bucket ->
            runCatching {
                YearMonth.from(LocalDate.parse(bucket.timeBucket.take(10)))
            }.getOrElse {
                runCatching { YearMonth.parse(bucket.timeBucket.take(7)) }.getOrNull()
            } == target
        }?.timeBucket
    }

    /**
     * Newest local calendar day among assets already loaded for [timeBucket].
     *
     * Immich month buckets use server/file UTC month boundaries, while our mosaic
     * groups by photographer-local day — so UTC January often contains late December
     * local days. Scrubber jumps must target a day that actually came from this bucket.
     */
    fun newestDayKeyForBucket(timeBucket: String): String? {
        val assets = _bucketAssets.value[timeBucket].orEmpty()
        if (assets.isEmpty()) return null
        return assets.maxOf { it.localCaptureDate() }.toString()
    }

    fun clearError() {
        _error.value = null
    }

    private fun recomputeDays() {
        val loaded = _bucketAssets.value
        val assets = _buckets.value.flatMap { loaded[it.timeBucket].orEmpty() }
        _days.value = assets.toTimelineDays()
    }

    companion object {
        const val PREFETCH_KEY = "timeline-prefetch"
        const val PREFETCH_DEBOUNCE_MS = 300L

        fun monthBucketKey(date: LocalDate): String =
            YearMonth.from(date).atDay(1).toString()
    }
}
