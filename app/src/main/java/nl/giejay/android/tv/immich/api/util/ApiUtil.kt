package nl.giejay.android.tv.immich.api.util

import arrow.core.Either
import nl.giejay.android.tv.immich.shared.prefs.HOST_NAME
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import retrofit2.HttpException
import retrofit2.Response
import timber.log.Timber
import java.util.UUID

object ApiUtil {

    fun getThumbnailUrl(assetId: String?, format: String): String? {
        return assetId?.let {
            "${hostName().lowercase()}/api/assets/${it}/thumbnail?size=${format}"
        }
    }

    private fun hostName(): String {
        return PreferenceManager.get(HOST_NAME)
    }

    fun getFileUrl(assetId: String?, type: String, forceOriginal: Boolean = false): String? {
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
                    "${hostName().lowercase()}/api/assets/${it}/original"
                }

        }
    }

    fun getPersonThumbnail(personId: UUID): String {
        return "${hostName().lowercase()}/api/people/$personId/thumbnail"
    }

    suspend fun <T> executeAPICall(expectedStatus: Int, handler: suspend () -> Response<T>): Either<String, T> {
        try {
            val res = handler()
            return when (val code = res.code()) {
                expectedStatus -> {
                    return res.body()?.let { Either.Right(it) }
                        ?: Either.Left("Did not receive a input from the server")
                }

                403 -> {
                    if(res.errorBody()?.string()?.contains("required permission: all") == true){
                        Either.Left("API key is missing the permission \"all\". Please adapt your permissions in the Immich web interface.")
                    } else {
                        Either.Left("API key permissions are invalid: ${res.errorBody()?.string() ?: "Unknown error"}")
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
}