package nl.giejay.android.tv.immich.api.model

import java.util.UUID

data class SearchRequest(val page: Int = 0,
                         val size: Int = 100,
                         val order: String = "desc",
                         val type: String? = null,
                         val personIds: List<UUID> = emptyList(),
                         val takenBefore: String? = null,
                         val takenAfter: String? = null,
                         val withExif: Boolean = true
)
