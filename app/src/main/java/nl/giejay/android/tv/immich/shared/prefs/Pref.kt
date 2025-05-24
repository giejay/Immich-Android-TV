package nl.giejay.android.tv.immich.shared.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager.sharedPreference


data class PrefScreen(val name: String, val children: List<PrefCategory>)
data class PrefCategory(val title: String, val children: List<Pref<*, *>>)

sealed class Pref<T, PREF : Preference>(val defaultValue: T, val title: String, val summary: String) {
    open fun key() = javaClass.simpleName.lowercase()
    abstract fun save(sharedPreferences: SharedPreferences, value: T)
    open fun parse(any: Any?): T {
        return any as T
    }

    fun createPreference(context: Context): PREF {
        val createPref = createPref(context)
        createPref.key = key()
        createPref.title = title
        createPref.setDefaultValue(defaultValue)
        createPref.summary = summary
        return createPref
    }

    abstract fun createPref(context: Context): PREF
}

sealed class ActionPref(title: String, summary: String, onClick: (Context) -> Unit): Pref<String, Preference>("", title, summary){
    override fun save(sharedPreferences: SharedPreferences, value: String) {

    }

    override fun createPref(context: Context): Preference {
        val preference = Preference(context)
        preference.key = key()
        return preference
    }
}

sealed class EnumPref<T : Enum<T>>(defaultValue: T, title: String, summary: String,
                                   val titlesResourceId: Int, val valuesResourceId: Int) : Pref<T, ListPreference>(defaultValue, title, summary) {
    override fun save(sharedPreferences: SharedPreferences, value: T) {
        sharedPreferences.edit().putString(key(), value.toString()).apply()
    }

    abstract override fun parse(any: Any?): T

    override fun createPref(context: Context): ListPreference {
        val listPreference = ListPreference(context)
        listPreference.setEntries(titlesResourceId)
        listPreference.dialogTitle = summary
        listPreference.setEntryValues(valuesResourceId)
        return listPreference
    }
}

sealed class BooleanPref(defaultValue: Boolean, title: String, summary: String) : Pref<Boolean, CheckBoxPreference>(defaultValue, title, summary) {
    override fun save(sharedPreferences: SharedPreferences, value: Boolean) {
        sharedPreference.edit().putBoolean(key(), value).apply()
    }

    override fun createPref(context: Context): CheckBoxPreference {
        return CheckBoxPreference(context)
    }
}

sealed class StringPref(defaultValue: String, title: String, summary: String) : Pref<String, EditTextPreference>(defaultValue, title, summary) {
    override fun save(sharedPreferences: SharedPreferences, value: String) {
        sharedPreference.edit().putString(key(), value).apply()
    }

    override fun createPref(context: Context): EditTextPreference {
        return EditTextPreference(context)
    }
}

sealed class StringSetPref(defaultValue: Set<String>, title: String, summary: String) : Pref<Set<String>, ListPreference>(defaultValue,
    title,
    summary) {
    override fun save(sharedPreferences: SharedPreferences, value: Set<String>) {
        sharedPreference.edit().putStringSet(key(), value).apply()
    }

    override fun createPref(context: Context): ListPreference {
        return ListPreference(context)
    }
}

sealed class IntSeekbarPref(defaultValue: Int, title: String, summary: String) : Pref<Int, SeekBarPreference>(defaultValue, title, summary) {
    override fun save(sharedPreferences: SharedPreferences, value: Int) {
        sharedPreference.edit().putInt(key(), value).apply()
    }

    override fun createPref(context: Context): SeekBarPreference {
        return SeekBarPreference(context)
    }
}

sealed class IntListPref(defaultValue: Int, title: String, summary: String,
                         val titlesResourceId: Int, val valuesResourceId: Int) : Pref<Int, ListPreference>(defaultValue, title, summary) {
    override fun save(sharedPreferences: SharedPreferences, value: Int) {
        sharedPreference.edit().putInt(key(), value).apply()
    }

    override fun createPref(context: Context): ListPreference {
        val listPreference = ListPreference(context)
        listPreference.setEntries(titlesResourceId)
        listPreference.dialogTitle = summary
        listPreference.setEntryValues(valuesResourceId)
        return listPreference
    }
}
