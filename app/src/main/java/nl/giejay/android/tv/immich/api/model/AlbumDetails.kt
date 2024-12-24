package nl.giejay.android.tv.immich.api.model

data class AlbumDetails(
    val albumName: String,
    val description: String,
    val id: String,
    val albumThumbnailAssetId: String?,
    val assets: List<Asset>
)