package nl.giejay.android.tv.immich.api.model

import com.google.gson.JsonElement

data class ImmichValidationError(
    val code: String? = null,
    val message: JsonElement? = null,
    val path: List<JsonElement>? = null
)

data class ImmichErrorResponse(
    val message: JsonElement? = null,
    val error: String? = null,
    val statusCode: Int? = null,
    val errors: List<ImmichValidationError>? = null
)

fun JsonElement?.toDisplayMessage(fallback: String = "Unknown error"): String {
    return try {
        when {
            this == null || this.isJsonNull -> fallback
            this.isJsonPrimitive -> this.asString
            this.isJsonArray -> this.asJsonArray.joinToString(", ") { it.toDisplayMessage(fallback) }
            else -> this.toString()
        }
    } catch (e: Exception) {
        fallback
    }
}
