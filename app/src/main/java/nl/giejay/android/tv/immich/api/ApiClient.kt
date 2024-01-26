package nl.giejay.android.tv.immich.api

import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.AlbumDetails
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Path

class ApiClient(private val hostName: String, private val apiKey: String) {
    companion object ApiClient {
        private var apiClient: nl.giejay.android.tv.immich.api.ApiClient? = null
        fun getClient(hostName: String, apiKey: String): nl.giejay.android.tv.immich.api.ApiClient {
            if (apiClient == null || apiClient?.apiKey != apiKey || apiClient?.hostName != hostName) {
                apiClient = ApiClient(hostName, apiKey)
            }
            return apiClient!!
        }
    }

    private val interceptor: Interceptor = Interceptor { chain ->
        val newRequest = chain.request().newBuilder()
            .addHeader("x-api-key", apiKey)
            .build();
        chain.proceed(newRequest)
    };

    private val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    private val retrofit = Retrofit.Builder()
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl("$hostName/api/")
        .build()

    private val service: ApiService = retrofit.create(ApiService::class.java)

    fun listAlbums(): Response<List<Album>> {
        val albums = service.listAlbums()
            .execute()
        if (albums.isSuccessful) {
            val sharedAlbums = service.listAlbums(true).execute()
            if (sharedAlbums.isSuccessful) {
                return Response.success(albums.body()!! + sharedAlbums.body()!!)
            }
        }
        return albums
    }

    fun listAssetsFromAlbum(albumId: String): Response<AlbumDetails>{
        return service.listAssetsFromAlbum(albumId).execute()
    }
}

