package nl.giejay.android.tv.immich.api

import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.SearchRequest

/**
 * Without machine learning the server cannot search by image content, so the same text is looked up in the
 * fields every library has: file name, description, city and country. One request per field.
 */
internal fun buildMetadataSearchRequests(query: String, page: Int, size: Int, type: String?): List<SearchRequest> {
    val base = SearchRequest(page = page, size = size, type = type, order = "desc", visibility = "timeline")
    return listOf(
        base.copy(originalFileName = query),
        base.copy(description = query),
        base.copy(city = query),
        base.copy(country = query),
    )
}

/** Merges the hits of several searches: every asset once, newest first. */
internal fun mergeSearchResults(results: List<List<Asset>>): List<Asset> =
    results.flatten()
        .distinctBy { it.id }
        .sortedByDescending { it.fileCreatedAt }
