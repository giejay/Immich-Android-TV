package nl.giejay.android.tv.immich.shared.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import nl.giejay.mediaslider.adapter.AlignOption
import nl.giejay.mediaslider.adapter.MetaDataClock
import nl.giejay.mediaslider.adapter.MetaDataItem
import nl.giejay.mediaslider.adapter.MetaDataMediaCount
import nl.giejay.mediaslider.adapter.MetaDataSliderItem
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.util.MetaDataConverter
import okhttp3.HttpUrl
import kotlin.reflect.KClass

enum class MetaDataScreen {
    SCREENSAVER, VIEWER
}

object PreferenceManager {
    lateinit var sharedPreference: SharedPreferences
    private lateinit var liveSharedPreferences: LiveSharedPreferences

    // stores the typed values, not the internal PrefTypes (String, Int)
    private val liveContext: MutableMap<String, Any?> = mutableMapOf()

    fun <T : Any> subclasses(clazz: KClass<T>): List<KClass<out T>> {
        return clazz.sealedSubclasses.flatMap { subclasses(it) + it }
    }

    fun init(context: Context) {
        sharedPreference = PreferenceManager.getDefaultSharedPreferences(context)
        liveSharedPreferences = LiveSharedPreferences(sharedPreference)
        subclasses(Pref::class).filter { it.objectInstance != null }.forEach { pref ->
            val prefInstance = pref.objectInstance!! as Pref<Any, *, *>
            liveSharedPreferences.subscribeTyped(prefInstance) { typeValue ->
                liveContext[prefInstance.key()] = typeValue
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: Pref<T, *, *>): T {
        return liveContext[key.key()] as T ?: key.getValue(sharedPreference)
    }

    fun <T, PREFTYPE> save(key: Pref<T, *, PREFTYPE>, value: T) {
        liveContext[key.key()] = value
        key.save(sharedPreference, value)
    }

    fun <T> subscribe(key: Pref<T, *, *>, onChange: (T) -> Unit) {
        liveSharedPreferences.subscribeTyped(key, onChange)
    }

    fun subscribeMultiple(keys: List<Pref<*, *, *>>, onChange: (Map<String, Any?>) -> Unit) {
        liveSharedPreferences.subscribeMultiple(keys, onChange)
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

    fun getString(key: String, default: String): String {
        return sharedPreference.getString(key, default)!!
    }

    fun getViewMetaDataFromOldPrefs(): List<MetaDataItem> {
        val metaData: MutableList<MetaDataItem> = mutableListOf()
        if (get(SLIDER_SHOW_DESCRIPTION)) {
            metaData.add(MetaDataSliderItem(MetaDataType.DESCRIPTION, AlignOption.RIGHT))
//            metaData.add(MetaDataSliderItem(MetaDataType.ALBUM_NAME, AlignOption.RIGHT))
        }
        if (get(SLIDER_SHOW_CITY)) {
            metaData.add(MetaDataSliderItem(MetaDataType.CITY, AlignOption.RIGHT))
            metaData.add(MetaDataSliderItem(MetaDataType.COUNTRY, AlignOption.RIGHT))
        }
        // todo add toggles

        if (get(SLIDER_SHOW_DATE)) {
            metaData.add(MetaDataSliderItem(MetaDataType.DATE, AlignOption.RIGHT))
        }

        if (get(SLIDER_SHOW_MEDIA_COUNT)) {
            metaData.add(MetaDataMediaCount(AlignOption.RIGHT))
        }
        return metaData
    }

    fun getScreenSaverMetaDataFromOldPrefs(): List<MetaDataItem> {
        val metaData: MutableList<MetaDataItem> = mutableListOf()
        if (get(SCREENSAVER_SHOW_CLOCK)) {
            metaData.add(MetaDataClock(AlignOption.RIGHT))
        }
        if (get(SCREENSAVER_SHOW_DESCRIPTION)) {
            metaData.add(MetaDataSliderItem(MetaDataType.DESCRIPTION, AlignOption.RIGHT))
            metaData.add(MetaDataSliderItem(MetaDataType.CITY, AlignOption.RIGHT))
            metaData.add(MetaDataSliderItem(MetaDataType.COUNTRY, AlignOption.RIGHT))
        }

        if (get(SCREENSAVER_SHOW_ALBUM_NAME)) {
            metaData.add(MetaDataSliderItem(MetaDataType.ALBUM_NAME, AlignOption.RIGHT))
        }
        if (get(SCREENSAVER_SHOW_DATE)) {
            metaData.add(MetaDataSliderItem(MetaDataType.DATE, AlignOption.RIGHT))
        }
        if (get(SCREENSAVER_SHOW_MEDIA_COUNT)) {
            metaData.add(MetaDataMediaCount(AlignOption.RIGHT))
        }

        return metaData
    }

    fun saveMetaData(align: AlignOption, metaDataScreen: MetaDataScreen, metaData: List<MetaDataItem>) {
        sharedPreference.edit().putString(createKey(metaDataScreen, align), MetaDataConverter.metaDataListToJson(metaData)).apply()
    }

    private fun createKey(metaDataScreen: MetaDataScreen, align: AlignOption) = "meta_data_${metaDataScreen}_${align}"

    fun getMetaData(align: AlignOption, metaDataScreen: MetaDataScreen): List<MetaDataItem> {
        return MetaDataConverter.metaDataListFromJson(sharedPreference.getString(createKey(metaDataScreen, align), "[]")!!)
    }

    fun getAllMetaData(metaDataScreen: MetaDataScreen): List<MetaDataItem> {
        return getMetaData(AlignOption.RIGHT, metaDataScreen) + getMetaData(AlignOption.LEFT, metaDataScreen)
    }

    fun hasMetaDataForScreen(metaDataScreen: MetaDataScreen, align: AlignOption): Boolean {
        return sharedPreference.contains(createKey(metaDataScreen, align))
    }
}