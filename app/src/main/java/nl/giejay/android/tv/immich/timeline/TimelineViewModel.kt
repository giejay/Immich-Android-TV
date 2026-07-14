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
    private val prefetchDebounceMs: Long = PREFETCH_DEBOUNCE_MS
) : ViewModel() {

    private val _buckets = MutableStateFlow<List<TimeBucketSummary>>(emptyList())
    val buckets: StateFlow<List<TimeBucketSummary>> = _buckets.asStateFlow()

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
                val index = list.indexOfFirst { it.timeBucket == monthKey }
                if (index < 0) {
                    loadBucket(monthKey)
                    return@launch
                }
                loadBucket(monthKey)
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
