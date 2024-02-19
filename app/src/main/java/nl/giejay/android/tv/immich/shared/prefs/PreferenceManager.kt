package nl.giejay.android.tv.immich.shared.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import okhttp3.HttpUrl

object PreferenceManager {
    private lateinit var sharedPreference: SharedPreferences
    private lateinit var liveSharedPreferences: LiveSharedPreferences
    private val liveContext: MutableMap<String, Any> = mutableMapOf()

    // host settings
    private val KEY_HOST_NAME = "hostName"
    private val KEY_API_KEY = "apiKey"
    private val KEY_DISABLE_SSL_VERIFICATION = "disableSSLVerification"

    // screensaver settings
    private val KEY_SCREENSAVER_INTERVAL = "screensaver_interval"
    private val KEY_SCREENSAVER_SHOW_MEDIA_COUNT = "screensaver_show_media_count"
    private val KEY_SCREENSAVER_SHOW_DESCRIPTION = "screensaver_show_description"
    private val KEY_SCREENSAVER_ALBUMS = "screensaver_albums"

    // slider/view settings
    private val KEY_SLIDER_INTERVAL = "slider_interval"
    private val KEY_SLIDER_SHOW_DESCRIPTION = "slider_show_description"
    private val KEY_SLIDER_SHOW_MEDIA_COUNT = "slider_show_media_count"
    private val KEY_ALBUMS_SORTING = "albums_sorting"
    private val KEY_PHOTOS_SORTING = "photos_sorting"
    private val KEY_ALBUMS_SORTING_REVERSE = "albums_sorting_reverse"
    private val KEY_PHOTOS_SORTING_REVERSE = "photos_sorting_reverse"

    // other
    private val KEY_DEBUG_MODE = "debug_mode"
    private val KEY_USER_ID = "user_id"

    private val propsToWatch = mapOf(
        KEY_HOST_NAME to "",
        KEY_API_KEY to "",
        KEY_DISABLE_SSL_VERIFICATION to false,
        KEY_SCREENSAVER_INTERVAL to "3",
        KEY_SLIDER_INTERVAL to "3",
        KEY_SLIDER_SHOW_DESCRIPTION to true,
        KEY_SLIDER_SHOW_MEDIA_COUNT to true,
        KEY_SCREENSAVER_SHOW_DESCRIPTION to true,
        KEY_SCREENSAVER_SHOW_MEDIA_COUNT to true,
        KEY_SCREENSAVER_ALBUMS to mutableSetOf<String>(),
        KEY_DEBUG_MODE to false,
        KEY_ALBUMS_SORTING to AlbumsOrder.LAST_UPDATED.toString(),
        KEY_PHOTOS_SORTING to PhotosOrder.OLDEST_NEWEST.toString(),
        KEY_ALBUMS_SORTING_REVERSE to false,
        KEY_PHOTOS_SORTING_REVERSE to false
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

    fun saveSslVerification(value: Boolean){
        saveBoolean(KEY_DISABLE_SSL_VERIFICATION, value)
    }

    fun isLoggedId(): Boolean {
        return isValid(hostName(), apiKey())
    }

    fun isValid(hostName: String, apiKey: String): Boolean {
        return hostName.isNotBlank() && apiKey.isNotBlank() && HttpUrl.parse(hostName) != null
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

    fun disableSslVerification(): Boolean {
        return liveContext[KEY_DISABLE_SSL_VERIFICATION] as Boolean
    }

    fun debugEnabled(): Boolean {
        return liveContext[KEY_DEBUG_MODE] as Boolean
    }

    fun saveDebugMode(debugMode: Boolean){
        liveContext[KEY_DEBUG_MODE] = debugMode
        saveBoolean(KEY_DEBUG_MODE, debugMode)
    }

    fun getUserId(): String {
        return getString(KEY_USER_ID, "")
    }

    fun setUserId(userId: String){
        saveString(KEY_USER_ID, userId)
    }

    fun albumsOrder(): AlbumsOrder {
        return AlbumsOrder.valueOf(liveContext[KEY_ALBUMS_SORTING] as String)
    }

    fun photosOrder(): PhotosOrder {
        return PhotosOrder.valueOf(liveContext[KEY_PHOTOS_SORTING] as String)
    }

    fun reversePhotosOrder(): Boolean {
        return liveContext[KEY_PHOTOS_SORTING_REVERSE] as Boolean
    }

    fun reverseAlbumsOrder(): Boolean {
        return liveContext[KEY_ALBUMS_SORTING_REVERSE] as Boolean
    }

    private fun saveString(key: String, value: String) {
        liveContext[key] = value
        sharedPreference.edit().putString(key, value).apply()
    }

    private fun saveBoolean(key: String, value: Boolean) {
        liveContext[key] = value
        sharedPreference.edit().putBoolean(key, value).apply()
    }

    private fun saveStringSet(key: String, value: Set<String>) {
        liveContext[key] = value
        sharedPreference.edit().putStringSet(key, value).apply()
    }

    private fun getString(key: String, defaultValue: String): String {
        return sharedPreference.getString(key, defaultValue) ?: defaultValue
    }
}