package nl.giejay.android.tv.immich.api

import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.PROXY_PASSWORD
import nl.giejay.android.tv.immich.shared.prefs.PROXY_USER
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import okhttp3.Credentials

/**
 * The headers every request to the Immich server carries: the API key, plus HTTP basic auth for servers behind a
 * reverse proxy that asks for it. Used by the OkHttp client and by the media player's data sources.
 */
object ApiAuthHeaders {
    /** The `Authorization` value for a proxy user and password, or null unless both are filled in. */
    fun basicAuth(user: String, password: String): String? =
        if (user.isBlank() || password.isEmpty()) null else Credentials.basic(user.trim(), password)

    /** Request headers for the media player (its HTTP data source does not go through OkHttp). */
    fun forPlayer(apiKey: String, proxyUser: String, proxyPassword: String): Map<String, String> =
        buildMap {
            put("x-api-key", apiKey)
            basicAuth(proxyUser, proxyPassword)?.let { put("Authorization", it) }
        }

    fun forPlayerFromPrefs(): Map<String, String> =
        forPlayer(PreferenceManager.get(API_KEY), PreferenceManager.get(PROXY_USER), PreferenceManager.get(PROXY_PASSWORD))
}
