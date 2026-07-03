package nl.giejay.mediaslider.adapter

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import nl.giejay.mediaslider.adapter.MetaDataItem.Companion.DEFAULT_PADDING
import nl.giejay.mediaslider.model.MetaDataType
import java.lang.reflect.Type

class MetaDataSerializer : JsonSerializer<MetaDataItem>, JsonDeserializer<MetaDataItem> {
    override fun serialize(src: MetaDataItem?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        val jsonElement = context!!.serialize(src)
        jsonElement.asJsonObject.addProperty("type", src!!.type.toString())
        return jsonElement
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): MetaDataItem {
        val asJsonObject = json!!.asJsonObject
        val type = MetaDataType.valueOf(asJsonObject.get("type").asString)
        val align = AlignOption.valueOf(asJsonObject.get("align").asString)
        val fontSize = asJsonObject.get("fontSize")?.asInt ?: type.defaultFontSize
        val padding = asJsonObject.get("padding")?.asInt ?: DEFAULT_PADDING
        return when (type) {
            MetaDataType.MEDIA_COUNT -> MetaDataMediaCount(align, fontSize = fontSize, padding = padding)
            MetaDataType.CLOCK -> MetaDataClock(align,  fontSize = fontSize, padding = padding)
            else -> MetaDataSliderItem(type, align,  fontSize = fontSize, padding = padding)
        }
    }
}