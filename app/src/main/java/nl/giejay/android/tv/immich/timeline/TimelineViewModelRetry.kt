package nl.giejay.android.tv.immich.timeline

/**
 * Loads the first [count] months that are not loaded yet. [TimelineViewModel.loadBucketList] returns at once when the
 * month list is cached, so after a failed initial load (list fetched, month requests failed) a Retry has to load
 * these months itself.
 */
suspend fun TimelineViewModel.loadFirstUnloadedBuckets(count: Int = 3) {
    val loaded = bucketAssets.value.keys
    buckets.value.filter { it.timeBucket !in loaded }.take(count).forEach { loadBucket(it.timeBucket) }
}
