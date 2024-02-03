package nl.giejay.android.tv.immich.api

import arrow.core.Either
import arrow.core.flatMap
import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.AlbumDetails
import nl.giejay.android.tv.immich.api.model.Asset
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber

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
            .addHeader("x-api-key", apiKey.trim())
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

    suspend fun listAlbums(): Either<String, List<Album>> {
        return executeAPICall { service.listAlbums() }.flatMap { albums ->
            return executeAPICall { service.listAlbums(true) }.map { sharedAlbums ->
                albums + sharedAlbums
            }
        }
    }

    suspend fun listAssetsFromAlbum(albumId: String): Either<String, AlbumDetails> {
        return executeAPICall { service.listAssetsFromAlbum(albumId) }
    }

    suspend fun listAssets(page: Int, pageCount: Int): Either<String, List<Asset>> {
        return executeAPICall { service.listAssets(page, pageCount) }
    }

    private suspend fun <T> executeAPICall(handler: suspend () -> Response<T>): Either<String, T> {
        try {
            val res = handler()
            return when (val code = res.code()) {
                200 -> {
                    return res.body()?.let { Either.Right(it) }
                        ?: Either.Left("Did not receive a input from the server")
                }

                else -> {
                    Either.Left("Could not fetch items! Status code: $code")
                }
            }
        } catch (e: HttpException) {
            Timber.e(e, "Could not fetch items due to http error")
            return Either.Left("Could not fetch items! Status code: ${e.code()}")
        } catch (e: Exception) {
            Timber.e(e, "Could not fetch items, unknown error")
            return Either.Left("Could not fetch items: ${e.message}")
        }
    }
}

