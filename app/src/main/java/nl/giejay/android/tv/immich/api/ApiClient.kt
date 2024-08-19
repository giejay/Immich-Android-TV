package nl.giejay.android.tv.immich.api

import arrow.core.Either
import arrow.core.flatMap
import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.AlbumDetails
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.SearchRequest
import nl.giejay.android.tv.immich.api.service.ApiService
import nl.giejay.android.tv.immich.api.util.ApiUtil.executeAPICall
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class ApiClientConfig(
    val hostName: String,
    val apiKey: String,
    val disableSslVerification: Boolean,
    val debugMode: Boolean
)

class ApiClient(private val config: ApiClientConfig) {
    companion object ApiClient {
        private var apiClient: nl.giejay.android.tv.immich.api.ApiClient? = null
        fun getClient(config: ApiClientConfig): nl.giejay.android.tv.immich.api.ApiClient {
            if (config != apiClient?.config) {
                apiClient = ApiClient(config)
            }
            return apiClient!!
        }
    }

    private val retrofit = Retrofit.Builder()
        .client(ApiClientFactory.getClient(config.disableSslVerification, config.apiKey, config.debugMode))
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl("${config.hostName}/api/")
        .build()

    private val service: ApiService = retrofit.create(ApiService::class.java)

    suspend fun listAlbums(): Either<String, List<Album>> {
        return executeAPICall(200) { service.listAlbums() }.flatMap { albums ->
            return executeAPICall(200) { service.listAlbums(true) }.map { sharedAlbums ->
                albums + sharedAlbums
            }
        }
    }

    suspend fun listAssetsFromAlbum(albumId: String): Either<String, AlbumDetails> {
        return executeAPICall(200) {
            val response = service.listAssetsFromAlbum(albumId)
            val album = response.body()
            val assets = album!!.assets.map{ Asset(it.id, it.type, it.deviceAssetId, it.exifInfo, it.fileModifiedAt, album.albumName)}
            val updatedAlbum = AlbumDetails(album.albumName, album.description, album.id, album.albumThumbnailAssetId, assets)
            Response.success(updatedAlbum)
        }
    }

    suspend fun listAssets(page: Int, pageCount: Int, order: String): Either<String, List<Asset>> {
        return executeAPICall(200) { service.listAssets(SearchRequest(page, pageCount, order)) }.map { res -> res.assets.items }
    }
}

