package nl.giejay.android.tv.immich.api.service

import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.Memory
import nl.giejay.android.tv.immich.api.model.PeopleResponse
import nl.giejay.android.tv.immich.api.model.SearchRequest
import nl.giejay.android.tv.immich.api.model.SearchResponse
import nl.giejay.android.tv.immich.api.model.TimeBucketAssetsResponse
import nl.giejay.android.tv.immich.api.model.TimeBucketSummary
import nl.giejay.android.tv.immich.api.model.UpdateAssetRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query


interface ApiService {
    @POST("search/metadata")
    suspend fun listAssets(@Body searchRequest: SearchRequest): Response<SearchResponse>

    @POST("search/random")
    suspend fun randomAssets(@Body searchRequest: SearchRequest): Response<List<Asset>>

    @GET("albums")
    suspend fun listAlbums(@Query("assetId") assetId: String? = null): Response<List<Album>>

    @GET("albums/{id}")
    suspend fun getAlbum(@Path("id") id: String): Response<Album>

    @GET("assets/{id}")
    suspend fun getAsset(@Path("id") id: String): Response<Asset>

    @GET("people")
    suspend fun listPeople(): Response<PeopleResponse>

    @PUT("assets/{id}")
    suspend fun updateAsset(@Path("id") id: String, @Body request: UpdateAssetRequest): Response<Asset>

    @GET("timeline/buckets")
    suspend fun getTimeBuckets(
        @Query("albumId") albumId: String? = null,
        @Query("order") order: String = "desc",
        @Query("visibility") visibility: String? = "timeline",
        @Query("withPartners") withPartners: Boolean? = null
    ): Response<List<TimeBucketSummary>>

    @GET("timeline/bucket")
    suspend fun getTimeBucket(
        @Query("timeBucket") timeBucket: String,
        @Query("order") order: String = "desc",
        @Query("visibility") visibility: String? = "timeline",
        @Query("withPartners") withPartners: Boolean? = null
    ): Response<TimeBucketAssetsResponse>

    @GET("memories")
    suspend fun getMemories(@Query("for") forDate: String): Response<List<Memory>>

    @GET("view/folder/unique-paths")
    suspend fun getUniquePaths(): Response<List<String>>

    @GET("view/folder")
    suspend fun getAssetsForPath(@Query("path") path: String): Response<List<Asset>>
}
