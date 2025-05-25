package nl.giejay.android.tv.immich.shared.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.navigation.NavController
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager.sharedPreference


sealed class PrefScreen(val title: String, val key: String, val children: List<PrefCategory>, val onViewCreated: (PreferenceManager) -> Unit = {}) {
    fun findByKey(key: String): Pref<*, *, *>? {
        return children.firstNotNullOfOrNull { it.findByKey(key) }
    }
}

data class PrefCategory(val title: String, val children: List<Pref<*, *, *>>) {
    fun findByKey(key: String): Pref<*, *, *>? {
        return children.find { it.key() == key }
    }
}

sealed class Pref<T, PREF : Preference, PREFTYPE>(val defaultValue: T, val title: String, val summary: String) {
    open fun key() = javaClass.simpleName.lowercase()
    abstract fun save(sharedPreferences: SharedPreferences, value: T)
    open fun fromPrefValue(prefValue: PREFTYPE): T {
        // by default stored pref value and the type of the Pref is the same, but it can differ with for example ListPreferences<Int>
        // stored by String values
        return prefValue as T
    }

    open fun onClick(context: Context, controller: NavController): Boolean {
        // can be implemented by children
        return false
    }

    open fun toPrefValue(value: T): PREFTYPE = value as PREFTYPE

    fun createPreference(context: Context): PREF {
        val createPref = createPref(context)
        createPref.key = key()
        createPref.title = title
        createPref.setDefaultValue(toPrefValue(defaultValue))
        createPref.summary = summary
        return createPref
    }

    abstract fun createPref(context: Context): PREF
}

sealed class NotUserEditableStringPref(title: String, summary: String) : Pref<String, Preference, String>("", title, summary) {
    override fun save(sharedPreferences: SharedPreferences, value: String) {
        sharedPreference.edit().putString(key(), value).apply()
    }

    override fun createPref(context: Context): Preference {
        return Preference(context)
    }
}

sealed class ActionPref(title: String, summary: String, val onClick: (Context, NavController) -> Boolean) : Pref<String, Preference, String>("",
    title,
    summary) {
    override fun save(sharedPreferences: SharedPreferences, value: String) {

    }

    override fun onClick(context: Context, controller: NavController): Boolean {
        return onClick.invoke(context, controller)
    }

    override fun createPref(context: Context): Preference {
        val preference = Preference(context)
        preference.key = key()
        return preference
    }
}

sealed class EnumPref<T>(defaultValue: T, title: String, summary: String,
                         val titlesResourceId: Int, val valuesResourceId: Int) : Pref<T, ListPreference, String>
    (defaultValue, title, summary) where T : Enum<T> {
    override fun save(sharedPreferences: SharedPreferences, value: T) {
        sharedPreferences.edit().putString(key(), value.toString()).apply()
    }

    override fun toPrefValue(value: T): String {
        return value.toString()
    }

    abstract override fun fromPrefValue(prefValue: String): T

    override fun createPref(context: Context): ListPreference {
        val listPreference = ListPreference(context)
        listPreference.setEntries(titlesResourceId)
        listPreference.dialogTitle = summary
        listPreference.setEntryValues(valuesResourceId)
        return listPreference
    }
}

sealed class EnumByTitlePref<T>(defaultValue: T, title: String, summary: String)  : Pref<T, ListPreference, String>(defaultValue, title, summary) where T : Enum<T>, T : EnumWithTitle {
    override fun save(sharedPreferences: SharedPreferences, value: T) {
        sharedPreferences.edit().putString(key(), value.toString()).apply()
    }

    override fun toPrefValue(value: T): String {
        return value.toString()
    }

    abstract override fun fromPrefValue(prefValue: String): T

    abstract fun getEnumEntries(): Array<T>

    override fun createPref(context: Context): ListPreference {
        val listPreference = ListPreference(context)
        listPreference.entries = getEnumEntries().map { it.getTitle() }.toTypedArray()
        listPreference.dialogTitle = summary
        listPreference.entryValues = getEnumEntries().map { it.toString() }.toTypedArray()
        return listPreference
    }
}

sealed class BooleanPref(defaultValue: Boolean, title: String, summary: String) : Pref<Boolean, CheckBoxPreference, Boolean>(defaultValue,
    title,
    summary) {
    override fun save(sharedPreferences: SharedPreferences, value: Boolean) {
        sharedPreference.edit().putBoolean(key(), value).apply()
    }

    override fun createPref(context: Context): CheckBoxPreference {
        return CheckBoxPreference(context)
    }
}

sealed class StringPref(defaultValue: String, title: String, summary: String) : Pref<String, EditTextPreference, String>(defaultValue,
    title,
    summary) {
    override fun save(sharedPreferences: SharedPreferences, value: String) {
        sharedPreference.edit().putString(key(), value).apply()
    }

    override fun createPref(context: Context): EditTextPreference {
        return EditTextPreference(context)
    }
}

sealed class StringSetPref(defaultValue: Set<String>, title: String, summary: String) : Pref<Set<String>, ListPreference, Set<String>>(defaultValue,
    title,
    summary) {
    override fun save(sharedPreferences: SharedPreferences, value: Set<String>) {
        sharedPreference.edit().putStringSet(key(), value).apply()
    }

    override fun createPref(context: Context): ListPreference {
        return ListPreference(context)
    }
}

sealed class IntSeekbarPref(defaultValue: Int, title: String, summary: String) : Pref<Int, SeekBarPreference, Int>(defaultValue, title, summary) {
    override fun save(sharedPreferences: SharedPreferences, value: Int) {
        sharedPreference.edit().putInt(key(), value).apply()
    }

    override fun createPref(context: Context): SeekBarPreference {
        return SeekBarPreference(context)
    }
}

sealed class IntListPref(defaultValue: Int, title: String, summary: String,
                         val titlesResourceId: Int, val valuesResourceId: Int) : Pref<Int, ListPreference, String>(defaultValue, title, summary) {
    override fun save(sharedPreferences: SharedPreferences, value: Int) {
        sharedPreference.edit().putString(key(), value.toString()).apply()
    }

    override fun toPrefValue(value: Int): String {
        return value.toString()
    }

    override fun fromPrefValue(prefValue: String): Int {
        return prefValue.toInt()
    }

    override fun createPref(context: Context): ListPreference {
        val listPreference = ListPreference(context)
        listPreference.setEntries(titlesResourceId)
        listPreference.dialogTitle = summary
        listPreference.setEntryValues(valuesResourceId)
        return listPreference
    }
}
