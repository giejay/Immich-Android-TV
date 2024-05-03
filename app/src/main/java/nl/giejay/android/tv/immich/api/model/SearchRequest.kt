package nl.giejay.android.tv.immich.api.model

data class SearchRequest(val page: Int = 0, val size: Int = 100, val order: String = "desc")
