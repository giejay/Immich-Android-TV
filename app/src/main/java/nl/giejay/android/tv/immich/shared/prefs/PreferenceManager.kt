package nl.giejay.android.tv.immich.shared.prefs

import android.content.Context
import android.content.SharedPreferences
import android.webkit.URLUtil
import androidx.preference.PreferenceManager

object PreferenceManager {
    private lateinit var sharedPreference: SharedPreferences
    private lateinit var liveSharedPreferences: LiveSharedPreferences
    private val liveContext: MutableMap<String, Any> = mutableMapOf()

    // host settings
    private val KEY_HOST_NAME = "hostName"
    private val KEY_API_KEY = "apiKey"

    // screensaver settings
    private val KEY_SCREENSAVER_INTERVAL = "screensaver_interval"
    private val KEY_SCREENSAVER_SHOW_MEDIA_COUNT = "screensaver_show_media_count"
    private val KEY_SCREENSAVER_SHOW_DESCRIPTION = "screensaver_show_description"
    private val KEY_SCREENSAVER_ALBUMS = "screensaver_albums"

    // slider/view settings
    private val KEY_SLIDER_INTERVAL = "slider_interval"
    private val KEY_SLIDER_SHOW_DESCRIPTION = "slider_show_description"
    private val KEY_SLIDER_SHOW_MEDIA_COUNT = "slider_show_media_count"

    private val propsToWatch = mapOf(
        KEY_HOST_NAME to "",
        KEY_API_KEY to "",
        KEY_SCREENSAVER_INTERVAL to "3",
        KEY_SLIDER_INTERVAL to "3",
        KEY_SLIDER_SHOW_DESCRIPTION to true,
        KEY_SLIDER_SHOW_MEDIA_COUNT to true,
        KEY_SCREENSAVER_SHOW_DESCRIPTION to true,
        KEY_SCREENSAVER_SHOW_MEDIA_COUNT to true,
        KEY_SCREENSAVER_ALBUMS to mutableSetOf<String>()
    )

    fun init(context: Context) {
        sharedPreference = PreferenceManager.getDefaultSharedPreferences(context)
        liveSharedPreferences = LiveSharedPreferences(sharedPreference)
        propsToWatch.forEach { (key, defaultValue) ->
            liveSharedPreferences.subscribe(key, defaultValue) { value ->
                liveContext[key] = value
            }
        }
    }

    fun saveString(key: String, value: String) {
        liveContext[key] = value
        sharedPreference.edit().putString(key, value).apply()
    }

    fun saveStringSet(key: String, value: Set<String>) {
        liveContext[key] = value
        sharedPreference.edit().putStringSet(key, value).apply()
    }

    fun getString(key: String, defaultValue: String): String {
        return sharedPreference.getString(key, defaultValue) ?: defaultValue
    }

    fun apiKey(): String {
        return liveContext[KEY_API_KEY] as String
    }

    fun hostName(): String {
        return liveContext[KEY_HOST_NAME] as String
    }

    fun screensaverInterval(): Int {
        return liveContext[KEY_SCREENSAVER_INTERVAL]?.toString()?.toInt() ?: 3
    }

    fun screensaverShowDescription(): Boolean {
        return liveContext[KEY_SCREENSAVER_SHOW_DESCRIPTION] as Boolean
    }

    fun screensaverShowMediaCount(): Boolean {
        return liveContext[KEY_SCREENSAVER_SHOW_MEDIA_COUNT] as Boolean
    }

    fun sliderInterval(): Int {
        return liveContext[KEY_SLIDER_INTERVAL]?.toString()?.toInt() ?: 3
    }

    fun sliderShowDescription(): Boolean {
        return liveContext[KEY_SLIDER_SHOW_DESCRIPTION] as Boolean
    }

    fun sliderShowMediaCount(): Boolean {
        return liveContext[KEY_SLIDER_SHOW_MEDIA_COUNT] as Boolean
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

    fun getScreenSaverAlbums(): Set<String> {
        return liveContext[KEY_SCREENSAVER_ALBUMS] as Set<String>
    }

    fun saveScreenSaverAlbums(strings: Set<String>) {
        saveStringSet(KEY_SCREENSAVER_ALBUMS, strings)
        liveContext[KEY_SCREENSAVER_ALBUMS] = strings
    }
}