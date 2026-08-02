package nl.giejay.mediaslider.util

import nl.giejay.mediaslider.model.SliderItemViewHolder

/** Keeps an empty load result distinct from reaching the end of the source. */
data class LoadMoreResult(
    val items: List<SliderItemViewHolder>,
    val canLoadMore: Boolean
)

typealias LoadMore = suspend () -> LoadMoreResult
