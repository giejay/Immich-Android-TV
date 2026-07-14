package nl.giejay.android.tv.immich.timeline

/**
 * Pure year math for on-this-day memory labels (Context/plurals stay in [MemoryPresenter]).
 */
object MemoryLabels {

    /** Years between [nowYear] and [memoryYear], never negative. */
    fun yearsAgo(nowYear: Int, memoryYear: Int): Int =
        (nowYear - memoryYear).coerceAtLeast(0)
}
