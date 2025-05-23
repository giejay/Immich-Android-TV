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

sealed class EnumPref<T: Enum<T>>(defaultValue: T): PrefI<T>(defaultValue){
    override fun save(sharedPreferences: SharedPreferences, value: T) {
        sharedPreferences.edit().putString(key(), value.toString()).apply()
    }

    abstract override fun parse(any: Any?): T
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
data object SCREENSAVER_TYPE : EnumPref<ScreenSaverType>(ScreenSaverType.RECENT) {
    override fun parse(any: Any?): ScreenSaverType {
        return ScreenSaverType.valueOf(any as String)
    }
}

data object SLIDER_INTERVAL : IntPref(3)
data object SLIDER_ANIMATION_SPEED : IntPref(0)
data object SLIDER_SHOW_DESCRIPTION : BooleanPref(true)
data object SLIDER_SHOW_MEDIA_COUNT : BooleanPref(true)
data object SLIDER_SHOW_DATE : BooleanPref(false)
data object SLIDER_SHOW_CITY : BooleanPref(true)
data object SLIDER_ONLY_USE_THUMBNAILS : BooleanPref(true)
data object SLIDER_MERGE_PORTRAIT_PHOTOS : BooleanPref(true)
data object SLIDER_MAX_CUT_OFF_WIDTH : IntPref(20)
data object SLIDER_MAX_CUT_OFF_HEIGHT : IntPref(20)
data object SLIDER_GLIDE_TRANSFORMATION : EnumPref<GlideTransformations>(GlideTransformations.CENTER_INSIDE) {
    override fun parse(any: Any?): GlideTransformations {
        return GlideTransformations.valueOfSafe(any as String, defaultValue)
    }
}


// other
data object ALBUMS_SORTING : EnumPref<AlbumsOrder>(AlbumsOrder.LAST_UPDATED) {
    override fun parse(any: Any?): AlbumsOrder {
        return AlbumsOrder.valueOfSafe(any as String, defaultValue)
    }
}
data object PHOTOS_SORTING : EnumPref<PhotosOrder>(PhotosOrder.OLDEST_NEWEST) {
    override fun parse(any: Any?): PhotosOrder {
        return PhotosOrder.valueOfSafe(any as String, defaultValue)
    }
}
data object ALL_ASSETS_SORTING : EnumPref<PhotosOrder>(PhotosOrder.NEWEST_OLDEST) {
    override fun parse(any: Any?): PhotosOrder {
        return PhotosOrder.valueOfSafe(any as String, defaultValue)
    }
}

data object DEBUG_MODE : BooleanPref(false)
data object HIDDEN_HOME_ITEMS : StringSetPref(emptySet())
data object SIMILAR_ASSETS_YEARS_BACK : IntPref(10)
data object SIMILAR_ASSETS_PERIOD_DAYS : IntPref(30)
data object RECENT_ASSETS_MONTHS_BACK : IntPref(5)
data object USER_ID : StringPref("")


object PreferenceManager {
    lateinit var sharedPreference: SharedPreferences
    private lateinit var liveSharedPreferences: LiveSharedPreferences
    private val liveContext: MutableMap<String, Any> = mutableMapOf()

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
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: PrefI<T>): T {
        return key.parse(liveContext[key.key()])
    }

    fun <T> save(key: PrefI<T>, value: T) {
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

    fun removeHiddenHomeItem(item: String) {
        save(HIDDEN_HOME_ITEMS, get(HIDDEN_HOME_ITEMS).filter { it != item }.toSet())
    }

    fun addHiddenHomeItem(item: String) {
        save(HIDDEN_HOME_ITEMS, get(HIDDEN_HOME_ITEMS) + item)
    }

    fun toggleHiddenHomeItem(name: String) {
        if (get(HIDDEN_HOME_ITEMS).contains(name)) {
            removeHiddenHomeItem(name)
        } else {
            addHiddenHomeItem(name)
        }
    }

    fun isHomeItemHidden(name: String?): Boolean {
        return name?.let { get(HIDDEN_HOME_ITEMS).contains(it) } ?: false
    }
}