package nl.giejay.android.tv.immich.api.util

import arrow.core.Either
import com.google.gson.Gson
import nl.giejay.android.tv.immich.api.model.ImmichErrorResponse
import nl.giejay.android.tv.immich.api.model.toDisplayMessage
import nl.giejay.android.tv.immich.shared.prefs.HOST_NAME
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import retrofit2.HttpException
import retrofit2.Response
import timber.log.Timber
import java.util.UUID

object ApiUtil {

    fun getThumbnailUrl(assetId: String?, format: String, loadEdited: Boolean = false): String? {
        return assetId?.let {
            "${hostName().lowercase()}/api/assets/${it}/thumbnail?size=${format}&edited=${loadEdited}"
        }
    }

    private fun hostName(): String {
        return PreferenceManager.hostName
    }

    fun getFileUrl(assetId: String?, type: String, forceOriginal: Boolean = false, loadEdited: Boolean = false): String? {
        if(forceOriginal){
            return assetId?.let {
                "${hostName().lowercase()}/api/assets/${it}/original"
            }
        }
        return when (type) {
            "VIDEO" ->
                assetId?.let {
                    "${hostName().lowercase()}/api/assets/${it}/video/playback"
                }

            else ->
                assetId?.let {
                    "${hostName().lowercase()}/api/assets/${it}/original?edited=${loadEdited}"
                }

        }
    }

    fun getPersonThumbnail(personId: UUID): String {
        return "${hostName().lowercase()}/api/people/$personId/thumbnail"
    }

    suspend fun <T> executeAPICall(expectedStatus: Int, handler: suspend () -> Response<T>): Either<String, T> {
        try {
            val res = handler()
            logCorrelationId(res)
            return when (val code = res.code()) {
                expectedStatus -> {
                    return res.body()?.let { Either.Right(it) }
                        ?: Either.Left("Did not receive a input from the server")
                }

                403 -> {
                    val bodyString: String? = res.errorBody()?.string()
                    if(bodyString?.contains("required permission: all") == true){
                        Either.Left("API key is missing the permission \"all\". Please adapt your permissions in the Immich web interface.")
                    } else {
                        val parsed = parseErrorBodySafely(bodyString)
                        val message = parsed?.message?.toDisplayMessage(bodyString ?: "Unknown error") ?: (bodyString ?: "Unknown error")
                        Either.Left("API key permissions are invalid: $message")
                    }
                }
                else -> {
                    Either.Left("Invalid status code from API: $code, make sure you are using the latest Immich server release.")
                }
            }
        } catch (e: HttpException) {
            Timber.e(e, "Could not fetch items due to http error")
            return Either.Left("Could not fetch data from API, status: ${e.code()}")
        } catch (e: Exception) {
            Timber.e(e, "Could not fetch items, unknown error")
            return Either.Left("Could not fetch data from API, response: ${e.message}")
        }
    }

    private fun parseErrorBodySafely(body: String?): ImmichErrorResponse? {
        if (body.isNullOrBlank()) return null
        return try {
            Gson().fromJson(body, ImmichErrorResponse::class.java)
        } catch (e: Exception) {
            Timber.w(e, "Could not parse structured error response, falling back to raw message")
            null
        }
    }

    private fun logCorrelationId(res: Response<*>) {
        val correlationId = res.headers()["X-Correlation-ID"]
        if (correlationId != null) {
            Timber.d("X-Correlation-ID: $correlationId")
        }
    }
}