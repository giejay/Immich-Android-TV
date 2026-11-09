package nl.giejay.android.tv.immich.api.model

/** A tag of the library (GET /tags); [value] is the full path, e.g. "Trips/Italy". */
data class TagInfo(
    val id: String,
    val name: String,
    val value: String,
    val color: String? = null,
)
