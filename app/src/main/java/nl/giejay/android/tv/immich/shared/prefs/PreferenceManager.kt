package nl.giejay.android.tv.immich.shared.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import nl.giejay.android.tv.immich.screensaver.ScreenSaverType
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager.sharedPreference
import nl.giejay.mediaslider.transformations.GlideTransformations
import okhttp3.HttpUrl
import kotlin.reflect.KClass

sealed class PrefI<T>(val defaultValue: T) {
    open fun key() = javaClass.simpleName.lowercase()
    abstract fun save(sharedPreferences: SharedPreferences, value: T)
    open fun parse(any: Any?): T {
        return any as T
    }
}

sealed class BooleanPref(defaultValue: Boolean) : PrefI<Boolean>(defaultValue) {
    override fun save(sharedPreferences: SharedPreferences, value: Boolean) {
        sharedPreference.edit().putBoolean(key(), value).apply()
    }
}

sealed class StringPref(defaultValue: String) : PrefI<String>(defaultValue) {
    override fun save(sharedPreferences: SharedPreferences, value: String) {
        sharedPreference.edit().putString(key(), value).apply()
    }
}

sealed class StringSetPref(defaultValue: Set<String>) : PrefI<Set<String>>(defaultValue) {
    override fun save(sharedPreferences: SharedPreferences, value: Set<String>) {
        sharedPreference.edit().putStringSet(key(), value).apply()
    }
}

sealed class IntPref(defaultValue: Int) : PrefI<Int>(defaultValue) {
    override fun save(sharedPreferences: SharedPreferences, value: Int) {
        sharedPreference.edit().putInt(key(), value).apply()
    }
}

data object SCREENSAVER_INTERVAL : IntPref(3)

data object DISABLE_SSL_VERIFICATION : BooleanPref(false) {
    override fun key() = "disableSSLVerification"
}

data object API_KEY : StringPref("") {
    override fun key() = "apiKey"
}

data object HOST_NAME : StringPref("") {
    override fun key() = "hostName"

    override fun save(sharedPreferences: SharedPreferences, value: String) {
        super.save(sharedPreferences, value.replace(Regex("/$"), ""))
    }
}
data object SCREENSAVER_SHOW_MEDIA_COUNT : BooleanPref(true)
data object SCREENSAVER_SHOW_DESCRIPTION : BooleanPref(true)
data object SCREENSAVER_SHOW_ALBUM_NAME : BooleanPref(true)
data object SCREENSAVER_SHOW_DATE : BooleanPref(true)
data object SCREENSAVER_SHOW_CLOCK : BooleanPref(true)
data object SCREENSAVER_ANIMATE_ASSET_SLIDE : BooleanPref(true)
data object SCREENSAVER_ALBUMS : StringSetPref(mutableSetOf())
data object SCREENSAVER_INCLUDE_VIDEOS : BooleanPref(false)
data object SCREENSAVER_PLAY_SOUND : BooleanPref(false)
data object SCREENSAVER_TYPE : PrefI<ScreenSaverType>(ScreenSaverType.RECENT) {
    override fun save(sharedPreferences: SharedPreferences, value: ScreenSaverType) {
        sharedPreferences.edit().putString(key(), value.toString()).apply()
    }

    override fun parse(any: Any?): ScreenSaverType {
        return ScreenSaverType.valueOf(any as String)
    }
}


object PreferenceManager {
    lateinit var sharedPreference: SharedPreferences
    private lateinit var liveSharedPreferences: LiveSharedPreferences
    private val liveContext: MutableMap<String, Any> = mutableMapOf()

    // slider/view settings
    private val KEY_SLIDER_INTERVAL = "slider_interval"
    private val KEY_SLIDER_ANIMATION_SPEED = "slider_animation_speed"
    private val KEY_SLIDER_SHOW_DESCRIPTION = "slider_show_description"
    private val KEY_SLIDER_SHOW_MEDIA_COUNT = "slider_show_media_count"
    private val KEY_SLIDER_SHOW_DATE = "slider_show_date"
    private val KEY_SLIDER_SHOW_CITY = "slider_show_city"
    private val KEY_SLIDER_ONLY_USE_THUMBNAILS = "slider_only_use_thumbnails"
    private val KEY_SLIDER_MERGE_PORTRAIT_PHOTOS = "slider_merge_portrait_photos"
    private val KEY_MAX_CUT_OFF_WIDTH = "slider_max_cut_off_width"
    private val KEY_MAX_CUT_OFF_HEIGHT = "slider_max_cut_off_height"
    private val KEY_GLIDE_TRANSFORMATION = "slider_glide_transformation"

    // sorting
    val KEY_ALBUMS_SORTING = "albums_sorting"
    private val KEY_PHOTOS_SORTING = "photos_sorting"
    private val KEY_ALL_ASSETS_SORTING = "all_assets_sorting"
//    private val KEY_ALBUMS_SORTING_REVERSE = "albums_sorting_reverse"
//    private val KEY_PHOTOS_SORTING_REVERSE = "photos_sorting_reverse"

    // other
    val KEY_DEBUG_MODE = "debug_mode"
    val KEY_HIDDEN_HOME_ITEMS = "hidden_home_items"
    val KEY_SIMILAR_ASSETS_YEARS_BACK = "similar_assets_years_back"
    val KEY_SIMILAR_ASSETS_PERIOD_DAYS = "similar_assets_period_days"
    val KEY_RECENT_ASSETS_MONTHS_BACK = "recent_assets_months_back"
    private val KEY_USER_ID = "user_id"

    private val propsToWatch = mapOf(
        KEY_SLIDER_INTERVAL to "3",
        KEY_SLIDER_SHOW_DESCRIPTION to true,
        KEY_SLIDER_SHOW_MEDIA_COUNT to true,
        KEY_SLIDER_SHOW_DATE to false,
        KEY_SLIDER_SHOW_CITY to true,
        KEY_DEBUG_MODE to false,
        KEY_ALBUMS_SORTING to AlbumsOrder.LAST_UPDATED.toString(),
        KEY_PHOTOS_SORTING to PhotosOrder.OLDEST_NEWEST.toString(),
        KEY_ALL_ASSETS_SORTING to PhotosOrder.NEWEST_OLDEST.toString(),
//        KEY_ALBUMS_SORTING_REVERSE to false,
//        KEY_PHOTOS_SORTING_REVERSE to false,
        KEY_SLIDER_ONLY_USE_THUMBNAILS to true,
        KEY_SLIDER_MERGE_PORTRAIT_PHOTOS to true,
        KEY_HIDDEN_HOME_ITEMS to emptySet<String>(),
        KEY_SIMILAR_ASSETS_YEARS_BACK to 10,
        KEY_RECENT_ASSETS_MONTHS_BACK to 5,
        KEY_SIMILAR_ASSETS_PERIOD_DAYS to 30,
        KEY_SLIDER_ANIMATION_SPEED to 0,
        KEY_MAX_CUT_OFF_HEIGHT to 20,
        KEY_MAX_CUT_OFF_WIDTH to 20
    )

    fun <T: Any> subclasses(clazz: KClass<T>): List<KClass<out T>>{
        return clazz.sealedSubclasses.flatMap {  subclasses(it) + it }
    }

    fun init(context: Context) {
        sharedPreference = PreferenceManager.getDefaultSharedPreferences(context)
        liveSharedPreferences = LiveSharedPreferences(sharedPreference)
        subclasses(PrefI::class).filter { it.objectInstance != null }.forEach { pref ->
            val prefInstance = pref.objectInstance!!
            liveSharedPreferences.subscribe(prefInstance.key(), prefInstance.defaultValue as Any) { value ->
                if (prefInstance.defaultValue is Int) {
                    liveContext[prefInstance.key()] = value.toString().toInt()
                } else {
                    liveContext[prefInstance.key()] = value
                }
            }
        }
        propsToWatch.forEach { (key, defaultValue) ->
            liveSharedPreferences.subscribe(key, defaultValue) { value ->
                liveContext[key] = value
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: PrefI<T>): T {
        return key.parse(liveContext[key.key()])
    }

    fun <T> save(key: PrefI<T>, value: T) {
        liveContext[key.key()] = value as Any
        key.save(sharedPreference, value)
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

    fun sliderShowDate(): Boolean {
        return liveContext[KEY_SLIDER_SHOW_DATE] as Boolean
    }

    fun sliderOnlyUseThumbnails(): Boolean {
        return liveContext[KEY_SLIDER_ONLY_USE_THUMBNAILS] as Boolean
    }

    fun sliderMergePortraitPhotos(): Boolean {
        return liveContext[KEY_SLIDER_MERGE_PORTRAIT_PHOTOS] as Boolean
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

    fun debugEnabled(): Boolean {
        return liveContext[KEY_DEBUG_MODE] as Boolean
    }

    fun saveDebugMode(debugMode: Boolean) {
        liveContext[KEY_DEBUG_MODE] = debugMode
        saveBoolean(KEY_DEBUG_MODE, debugMode)
    }

    fun hiddenHomeItems(): Set<String> {
        return liveContext[KEY_HIDDEN_HOME_ITEMS] as Set<String>
    }

    fun removeHiddenHomeItem(item: String) {
        saveStringSet(KEY_HIDDEN_HOME_ITEMS, hiddenHomeItems().filter { it != item }.toSet())
    }

    fun addHiddenHomeItem(item: String) {
        saveStringSet(KEY_HIDDEN_HOME_ITEMS, hiddenHomeItems() + item)
    }

    fun getUserId(): String {
        return getString(KEY_USER_ID, "")
    }

    fun setUserId(userId: String) {
        saveString(KEY_USER_ID, userId)
    }

    fun albumsOrder(): AlbumsOrder {
        return AlbumsOrder.valueOfSafe(
            liveContext[KEY_ALBUMS_SORTING] as String,
            AlbumsOrder.LAST_UPDATED
        )
    }

    fun photosOrder(): PhotosOrder {
        return PhotosOrder.valueOfSafe(
            liveContext[KEY_PHOTOS_SORTING] as String,
            PhotosOrder.OLDEST_NEWEST
        )
    }

    fun allAssetsOrder(): PhotosOrder {
        return PhotosOrder.valueOfSafe(
            liveContext[KEY_ALL_ASSETS_SORTING] as String,
            PhotosOrder.NEWEST_OLDEST
        )
    }

    fun saveSortingForAlbum(albumId: String, value: String) {
        saveString(keyAlbumsSorting(albumId), value)
    }

    fun getSortingForAlbum(albumId: String): PhotosOrder {
        return PhotosOrder.valueOfSafe(
            getString(keyAlbumsSorting(albumId), photosOrder().toString()),
            photosOrder()
        )
    }

    fun keyAlbumsSorting(albumId: String): String {
        return "photos_sorting_${albumId}"
    }

//    fun reversePhotosOrder(): Boolean {
//        return liveContext[KEY_PHOTOS_SORTING_REVERSE] as Boolean
//    }
//
//    fun reverseAlbumsOrder(): Boolean {
//        return liveContext[KEY_ALBUMS_SORTING_REVERSE] as Boolean
//    }

    fun getBoolean(key: String, default: Boolean): Boolean {
        return sharedPreference.getBoolean(key, default)
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

    fun getString(key: String, defaultValue: String): String {
        return sharedPreference.getString(key, defaultValue) ?: defaultValue
    }

    fun toggleHiddenHomeItem(name: String) {
        if (hiddenHomeItems().contains(name)) {
            removeHiddenHomeItem(name)
        } else {
            addHiddenHomeItem(name)
        }
    }

    fun isHomeItemHidden(name: String?): Boolean {
        return name?.let { hiddenHomeItems().contains(it) } ?: false
    }

    fun similarAssetsYearsBack(): Int {
        return Integer.parseInt(liveContext[KEY_SIMILAR_ASSETS_YEARS_BACK].toString())
    }

    fun similarAssetsPeriodDays(): Int {
        return Integer.parseInt(liveContext[KEY_SIMILAR_ASSETS_PERIOD_DAYS].toString())
    }

    fun recentAssetsMonthsBack(): Int {
        return Integer.parseInt(liveContext[KEY_RECENT_ASSETS_MONTHS_BACK].toString())
    }

    fun sliderShowCity(): Boolean {
        return liveContext[KEY_SLIDER_SHOW_CITY] as Boolean
    }

    fun animationSpeedMillis(): Int {
        return liveContext[KEY_SLIDER_ANIMATION_SPEED].toString().toInt()
    }

    fun maxCutOffWidth(): Int {
        return liveContext[KEY_MAX_CUT_OFF_WIDTH].toString().toInt()
    }

    fun maxCutOffHeight(): Int {
        return liveContext[KEY_MAX_CUT_OFF_HEIGHT].toString().toInt()
    }

    fun glideTransformation(): GlideTransformations {
        return GlideTransformations.valueOfSafe(
            getString(KEY_GLIDE_TRANSFORMATION, GlideTransformations.CENTER_INSIDE.toString()),
            GlideTransformations.CENTER_INSIDE
        )
    }
}