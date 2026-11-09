package nl.giejay.android.tv.immich.api

import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.api.demo.DemoInterceptor
import nl.giejay.android.tv.immich.api.interceptor.ResponseLoggingInterceptor
import nl.giejay.android.tv.immich.api.util.UnsafeOkHttpClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import timber.log.Timber

object ApiClientFactory {

    fun getClient(disableSsl: Boolean, apiKey: String, debugMode: Boolean, proxyUser: String = "", proxyPassword: String = ""): OkHttpClient {
        val apiKeyInterceptor = interceptor(apiKey)
        val builder = if (disableSsl)
            UnsafeOkHttpClient.unsafeOkHttpClient()
        else OkHttpClient.Builder()
        // first, so the offline demo host (never a real server) is answered before anything touches the network
        builder.addInterceptor(DemoInterceptor { file -> ImmichApplication.appContext!!.assets.open("demo/$file") })
        builder.addInterceptor(apiKeyInterceptor)
        // for servers behind a reverse proxy with basic auth; set once per call, OkHttp drops it on a redirect to another host
        ApiAuthHeaders.basicAuth(proxyUser, proxyPassword)?.let { authorization ->
            builder.addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("Authorization", authorization).build()) }
        }
        return if(debugMode){
            builder.addInterceptor(ResponseLoggingInterceptor()).build()
        } else
            builder.build()
    }

    private fun interceptor(apiKey: String): Interceptor = Interceptor { chain ->
        try {
            val newRequest = chain.request().newBuilder()
                .addHeader("x-api-key", apiKey.trim())
                .build()
            chain.proceed(newRequest)
        } catch (e: Exception) {
            Timber.e(e, "Error adding API key header, invalid format")
            chain.proceed(chain.request()) // Proceed with the original request if there's an error
        }
    }
}