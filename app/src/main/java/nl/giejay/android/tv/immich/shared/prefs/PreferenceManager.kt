package nl.giejay.android.tv.immich.shared.prefs

import android.content.Context
import android.content.SharedPreferences
import android.webkit.URLUtil

object PreferenceManager {
    private lateinit var sharedPreference: SharedPreferences
    private lateinit var liveSharedPreferences: LiveSharedPreferences
    private val liveContext: MutableMap<String, Any> = mutableMapOf()
    private val KEY_HOST_NAME = "hostName"
    private val KEY_API_KEY = "apiKey"
    private val propsToWatch = mapOf(KEY_HOST_NAME to "", KEY_API_KEY to "")

    fun init(context: Context) {
        sharedPreference = context.getSharedPreferences("DEFAULT", Context.MODE_PRIVATE)
        liveSharedPreferences = LiveSharedPreferences(sharedPreference)
        propsToWatch.forEach { (key, defaultValue) ->
            liveSharedPreferences.subscribe(key, defaultValue) { value ->
                liveContext[key] = value
            }
        }
    }

    fun saveString(key: String, value: String) {
        liveContext[key] = value
        sharedPreference.edit()
            .putString(key, value)
            .apply()
    }

    fun getString(key: String, defaultValue: String): String {
        return sharedPreference.getString(key, defaultValue) ?: defaultValue
    }

    fun apiKey(): String{
        return liveContext[KEY_API_KEY] as String
    }

    fun hostName(): String{
        return liveContext[KEY_HOST_NAME] as String
    }

    fun saveApiKey(value: String) {
        saveString(KEY_API_KEY, value)
    }

    fun saveHostName(value: String) {
        saveString(KEY_HOST_NAME, value)
    }

    fun isLoggedId(): Boolean {
        return hostName().isNotBlank() && apiKey().isNotBlank() && URLUtil.isValidUrl(hostName())
    }

    fun removeApiSettings() {
        saveString(KEY_HOST_NAME, "")
        saveString(KEY_API_KEY, "")
    }
}