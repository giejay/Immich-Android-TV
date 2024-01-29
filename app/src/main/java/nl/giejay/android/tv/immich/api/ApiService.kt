package nl.giejay.android.tv.immich.api

import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.AlbumDetails
import nl.giejay.android.tv.immich.api.model.Asset
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query


interface ApiService {
    @GET("assets")
    suspend fun listAssets(@Query("page") page: Int = 0, @Query("size") size: Int = 100): Response<List<Asset>>

    @GET("album")
    suspend fun listAlbums(@Query("shared") shared: Boolean = false): Response<List<Album>>

    @GET("album/{albumId}")
    suspend fun listAssetsFromAlbum(@Path("albumId") albumId: String): Response<AlbumDetails>
}