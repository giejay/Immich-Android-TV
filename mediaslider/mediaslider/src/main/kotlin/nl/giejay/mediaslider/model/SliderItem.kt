package nl.giejay.mediaslider.model

import android.os.Parcel
import android.os.Parcelable
import java.util.Objects

interface MetaDataProvider : Parcelable {
    suspend fun getValue(): String?
}

class StaticMetaDataProvider(private val value: String?) : MetaDataProvider {
    constructor(parcel: Parcel) : this(parcel.readString())
    override suspend fun getValue(): String? = value
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(value)
    }
    override fun describeContents(): Int = 0
    companion object CREATOR : Parcelable.Creator<StaticMetaDataProvider> {
        override fun createFromParcel(parcel: Parcel): StaticMetaDataProvider = StaticMetaDataProvider(parcel)
        override fun newArray(size: Int): Array<StaticMetaDataProvider?> = arrayOfNulls(size)
    }
}

class SliderItem : Parcelable {
    var id: String
    val url: String?
    val type: SliderItemType
    val orientation: Int
    private val metaData: Map<MetaDataType, MetaDataProvider>
    val thumbnailUrl: String?

    constructor(id: String, url: String?, type: SliderItemType,
                orientation: Int,
                metaDataProviders: Map<MetaDataType, MetaDataProvider>, thumbnailUrl: String?) {
        this.id = id
        this.url = url
        this.type = type
        this.orientation = orientation
        this.metaData = metaDataProviders
        this.thumbnailUrl = thumbnailUrl
    }

    private constructor(`in`: Parcel) {
        id = `in`.readString()!!
        url = `in`.readString()!!
        type = SliderItemType.valueOf(`in`.readString()!!)
        orientation = `in`.readInt()
        val metaDataSize = `in`.readInt()
        val metaDataMap = mutableMapOf<MetaDataType, MetaDataProvider>()
        repeat(metaDataSize) {
            val key = MetaDataType.valueOf(`in`.readString()!!)
            val provider = `in`.readParcelable<MetaDataProvider>(MetaDataProvider::class.java.classLoader)!!
            metaDataMap[key] = provider
        }
        metaData = metaDataMap
        thumbnailUrl = `in`.readString()!!
    }

    suspend fun get(metaDataType: MetaDataType): String? {
        return this.metaData[metaDataType]?.getValue()
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(id)
        dest.writeString(url)
        dest.writeString(type.toString())
        dest.writeInt(orientation)
        dest.writeInt(metaData.size)
        for ((key, provider) in metaData) {
            dest.writeString(key.name)
            dest.writeParcelable(provider, flags)
        }
        dest.writeString(thumbnailUrl)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as SliderItem
        return url == that.url
    }

    override fun hashCode(): Int {
        return Objects.hash(url)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<SliderItem> = object : Parcelable.Creator<SliderItem> {
            override fun createFromParcel(`in`: Parcel): SliderItem {
                return SliderItem(`in`)
            }
            override fun newArray(size: Int): Array<SliderItem?> {
                return arrayOfNulls(size)
            }
        }
    }
}
