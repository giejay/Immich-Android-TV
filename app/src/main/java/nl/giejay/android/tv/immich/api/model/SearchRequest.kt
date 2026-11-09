package nl.giejay.android.tv.immich.api.model

import java.util.UUID

data class SearchRequest(val page: Int? = 0,
                         val size: Int = 100,
                         val albumIds: List<String> = emptyList(),
                         val order: String? = "desc",
                         val type: String? = null,
                         val personIds: List<UUID> = emptyList(),
                         val takenBefore: String? = null,
                         val takenAfter: String? = null,
                         val withExif: Boolean = true,
                         val visibility: String? = null,
                         // metadata search filters (null fields are not sent)
                         val originalFileName: String? = null,
                         val description: String? = null,
                         val city: String? = null,
                         val country: String? = null,
                         /** null = not sent */
                         val tagIds: List<String>? = null,
                         /** true = favorites only; null = not sent */
                         val isFavorite: Boolean? = null
)

/** POST /search/smart: CLIP search by image content, needs machine learning on the server. */
data class SmartSearchRequest(val query: String,
                              val page: Int? = 1,
                              val size: Int = 100,
                              val type: String? = null,
                              /** null = not sent (all albums) */
                              val albumIds: List<String>? = null,
                              val withExif: Boolean = true,
                              val visibility: String? = "timeline")
