package nl.giejay.mediaslider.util

import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import nl.giejay.mediaslider.adapter.MetaDataItem
import nl.giejay.mediaslider.adapter.MetaDataSerializer
import java.lang.reflect.Type

object MetaDataConverter {
    private val gson = com.google.gson.GsonBuilder()
        .registerTypeAdapter(MetaDataItem::class.java, MetaDataSerializer())
        .create()

    fun metaDataListToJson(metaData: List<MetaDataItem>): String {
        val listType: Type = object : TypeToken<ArrayList<MetaDataItem?>?>() {}.type
        return gson.toJson(metaData, listType)
    }

    fun metaDataToJson(metaData: MetaDataItem): String {
        return gson.toJson(metaData, MetaDataItem::class.java)
    }

    fun metaDataListFromJson(json: String): List<MetaDataItem> {
        val listType: Type = object : TypeToken<ArrayList<MetaDataItem?>?>() {}.type
        return gson.fromJson(json, listType)
    }

    fun metaDataFromJson(json: String): MetaDataItem {
        return gson.fromJson(json, MetaDataItem::class.java)
    }

    fun metaDataFromJsonObject(json: JsonObject): MetaDataItem {
        return gson.fromJson(json, MetaDataItem::class.java)
    }
}