package nl.giejay.android.tv.immich.api.service

import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.AlbumDetails
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.Bucket
import nl.giejay.android.tv.immich.api.model.PeopleResponse
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

    @POST("search/random")
    suspend fun randomAssets(@Body searchRequest: SearchRequest): Response<List<Asset>>

    @GET("albums")
    suspend fun listAlbums(@Query("shared") shared: Boolean = false): Response<List<Album>>

    @GET("people")
    suspend fun listPeople(): Response<PeopleResponse>

    @GET("albums/{albumId}")
    suspend fun listAssetsFromAlbum(@Path("albumId") albumId: String): Response<AlbumDetails>

    @GET("timeline/buckets")
    suspend fun listBuckets(@Query("albumId") albumId: String, @Query("size") size: String = "MONTH", @Query("order") order: String = "desc"): Response<List<Bucket>>

    @GET("timeline/bucket")
    suspend fun getBucket(@Query("albumId") albumId: String, @Query("timeBucket") timeBucket: String, @Query("size") size: String = "MONTH",  @Query("order") order: String = "desc"): Response<List<Asset>>

    @GET("view/folder/unique-paths")
    suspend fun getUniquePaths(): Response<List<String>>

    @GET("view/folder")
    suspend fun getAssetsForPath(@Query("path") path: String): Response<List<Asset>>
}