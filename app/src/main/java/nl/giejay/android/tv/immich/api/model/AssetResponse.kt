package nl.giejay.android.tv.immich.api.model

/**
 * Assets returned by one request and whether the source can be queried again.
 * Random sources can load again even though they do not have literal pages.
 */
data class AssetResponse(
    val assets: List<Asset>,
    val canLoadMore: Boolean
)
