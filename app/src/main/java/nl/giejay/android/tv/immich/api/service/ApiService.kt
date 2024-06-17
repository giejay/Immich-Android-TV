package nl.giejay.android.tv.immich.api.service

import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.AlbumDetails
import nl.giejay.android.tv.immich.api.model.SearchRequest
import nl.giejay.android.tv.immich.api.model.SearchResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query


interface ApiService {
    @POST("search/metadata")
    suspend fun listAssets(@Body searchRequest: SearchRequest): Response<SearchResponse>

    @GET("albums")
    suspend fun listAlbums(@Query("shared") shared: Boolean = false): Response<List<Album>>

    @GET("albums/{albumId}")
    suspend fun listAssetsFromAlbum(@Path("albumId") albumId: String): Response<AlbumDetails>
}