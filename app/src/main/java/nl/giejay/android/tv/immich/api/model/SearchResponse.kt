package nl.giejay.android.tv.immich.api.model

data class SearchResponse(
    val albums: SearchAlbumResponseDto,
    val assets: SearchAssetResponseDto)

data class SearchAlbumResponseDto(val total: Int,
                                  val count: Int,
                                  val items: List<Album>)

data class SearchAssetResponseDto(val total: Int,
                                  val count: Int,
                                  val items: List<Asset>)

