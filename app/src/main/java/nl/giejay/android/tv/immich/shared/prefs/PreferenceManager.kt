package nl.giejay.android.tv.immich.shared.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import okhttp3.HttpUrl
import kotlin.reflect.KClass

object PreferenceManager {
    lateinit var sharedPreference: SharedPreferences
    private lateinit var liveSharedPreferences: LiveSharedPreferences
    private val liveContext: MutableMap<String, Any> = mutableMapOf()

    val viewSettings = PrefScreen("View Settings",
        listOf(
            PrefCategory("Ordering",
                listOf(
                    ALBUMS_SORTING,
                    PHOTOS_SORTING,
                    ALL_ASSETS_SORTING)
            ),
            PrefCategory("Slideshow", listOf(SLIDER_ONLY_USE_THUMBNAILS,
                SLIDER_MERGE_PORTRAIT_PHOTOS, SLIDER_SHOW_DESCRIPTION,
                SLIDER_SHOW_MEDIA_COUNT, SLIDER_SHOW_DATE, SLIDER_SHOW_CITY,
                SLIDER_INTERVAL, SLIDER_ANIMATION_SPEED, SLIDER_GLIDE_TRANSFORMATION,
                SLIDER_MAX_CUT_OFF_WIDTH, SLIDER_MAX_CUT_OFF_HEIGHT)),
            PrefCategory("Other", listOf(
                SIMILAR_ASSETS_YEARS_BACK,
                SIMILAR_ASSETS_PERIOD_DAYS,
                RECENT_ASSETS_MONTHS_BACK,
                LOAD_BACKGROUND_IMAGE))
        )
    )

    val screenSaverSettings = PrefScreen("Screensaver Settings",
        listOf(
            PrefCategory("",
                listOf(
                    SCREENSAVER_TYPE,
                    PHOTOS_SORTING,
                    ALL_ASSETS_SORTING)
            )
        )
    )

    fun <T : Any> subclasses(clazz: KClass<T>): List<KClass<out T>> {
        return clazz.sealedSubclasses.flatMap { subclasses(it) + it }
    }

    fun init(context: Context) {
        sharedPreference = PreferenceManager.getDefaultSharedPreferences(context)
        liveSharedPreferences = LiveSharedPreferences(sharedPreference)
        subclasses(Pref::class).filter { it.objectInstance != null }.forEach { pref ->
            val prefInstance = pref.objectInstance!!
            liveSharedPreferences.subscribe(prefInstance.key(), prefInstance.defaultValue as Any) { value ->
                if (prefInstance.defaultValue is Int) {
                    liveContext[prefInstance.key()] = value.toString().toInt()
                } else {
                    liveContext[prefInstance.key()] = value
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: Pref<T, *>): T {
        return key.parse(liveContext[key.key()])
    }

    fun <T> save(key: Pref<T, *>, value: T) {
        liveContext[key.key()] = value as Any
        key.save(sharedPreference, value)
    }

    fun isLoggedId(): Boolean {
        return isValid(get(HOST_NAME), get(API_KEY))
    }

    fun isValid(hostName: String?, apiKey: String?): Boolean {
        return hostName?.isNotBlank() == true && apiKey?.isNotBlank() == true && HttpUrl.parse(hostName) != null
    }

    fun removeApiSettings() {
        HOST_NAME.save(sharedPreference, "")
        API_KEY.save(sharedPreference, "")
    }

    fun saveSortingForAlbum(albumId: String, value: String) {
        saveString(keyAlbumsSorting(albumId), value)
    }

    fun getSortingForAlbum(albumId: String): PhotosOrder {
        return PhotosOrder.valueOfSafe(
            getString(keyAlbumsSorting(albumId), PHOTOS_SORTING.defaultValue.toString()),
            PHOTOS_SORTING.defaultValue
        )
    }

    private fun saveString(key: String, value: String) {
        sharedPreference.edit().putString(key, value).apply()
    }

    private fun getString(key: String, default: String): String {
        return sharedPreference.getString(key, default)!!
    }

    fun keyAlbumsSorting(albumId: String): String {
        return "photos_sorting_${albumId}"
    }

    private fun removeStringSetItem(item: String, prefKey: StringSetPref) {
        save(prefKey, get(prefKey).filter { it != item }.toSet())
    }

    private fun addStringSetItem(item: String, prefKey: StringSetPref) {
        save(prefKey, get(prefKey) + item)
    }

    fun toggleStringSetItem(name: String, prefKey: StringSetPref) {
        if (get(prefKey).contains(name)) {
            removeStringSetItem(name, prefKey)
        } else {
            addStringSetItem(name, prefKey)
        }
    }

    fun itemInStringSet(name: String?, prefKey: StringSetPref): Boolean {
        return name?.let { get(prefKey).contains(it) } ?: false
    }
}