package nl.giejay.android.tv.immich.api

import arrow.core.Either
import arrow.core.flatMap
import nl.giejay.android.tv.immich.api.interceptor.ResponseLoggingInterceptor
import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.AlbumDetails
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.service.ApiService
import nl.giejay.android.tv.immich.api.util.UnsafeOkHttpClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber

class ApiClientConfig(
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

    private val interceptor: Interceptor = Interceptor { chain ->
        val newRequest = chain.request().newBuilder()
            .addHeader("x-api-key", config.apiKey.trim())
            .build();
        chain.proceed(newRequest)
    }

    private val clientBuilder = if (config.disableSslVerification)
        UnsafeOkHttpClient.unsafeOkHttpClient(interceptor)
    else OkHttpClient.Builder().addInterceptor(interceptor)

    private val client = if (config.debugMode) clientBuilder.addInterceptor(
        ResponseLoggingInterceptor()
    ).build() else clientBuilder.build()

    private val retrofit = Retrofit.Builder()
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl("${config.hostName}/api/")
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

