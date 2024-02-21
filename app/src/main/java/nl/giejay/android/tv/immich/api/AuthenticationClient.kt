package nl.giejay.android.tv.immich.api

import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.service.DeviceConfigResponse
import nl.giejay.android.tv.immich.api.service.DeviceRegisteredResponse
import nl.giejay.android.tv.immich.api.service.ImmichAuthenticationService
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AuthenticationClient {
    private val interceptor: Interceptor = Interceptor { chain ->
        val newRequest = chain.request().newBuilder()
            .addHeader(
                "x-api-key",
                ImmichApplication.appContext!!.resources.getString(R.string.api_key)
            )
            .build();
        chain.proceed(newRequest)
    }

    private val retrofit = Retrofit.Builder()
        .client(OkHttpClient.Builder().addInterceptor(interceptor).build())
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl(ImmichApplication.appContext!!.resources.getString(R.string.authentication_url))
        .build()

    private val authService = retrofit.create(ImmichAuthenticationService::class.java)

    suspend fun registerDevice(): DeviceRegisteredResponse? {
        return authService.registerDevice().body()
    }

    suspend fun getConfig(code: String): DeviceConfigResponse? {
        return authService.getConfig(code).body()
    }
}