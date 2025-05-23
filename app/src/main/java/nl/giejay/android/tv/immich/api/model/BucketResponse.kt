package nl.giejay.android.tv.immich.api.model

data class BucketResponse(
    val city: List<String>,
    val country: List<String>,
    val id: List<String>,
    val isImage: List<Boolean>,
    val localDateTime: List<String>,
    val thumbhash: List<String>
)

