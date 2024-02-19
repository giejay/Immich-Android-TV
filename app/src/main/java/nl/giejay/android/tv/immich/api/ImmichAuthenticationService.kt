package nl.giejay.android.tv.immich.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class DeviceRegisteredResponse(val code: String)
data class DeviceConfig(val host: String, val apiKey: String)
data class DeviceConfigResponse(val configuration: DeviceConfig, val status: String)
interface ImmichAuthenticationService {
    @POST("register-device")
    suspend fun registerDevice(): Response<DeviceRegisteredResponse>

    @GET("config/{code}")
    suspend fun getConfig(@Path("code") code: String): Response<DeviceConfigResponse>
}
