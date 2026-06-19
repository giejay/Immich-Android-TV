package nl.giejay.mediaslider.model

data class SliderItemViewHolder(val mainItem: SliderItem, val secondaryItem: SliderItem?) {
    constructor(mainItem: SliderItem) : this(mainItem, null)

    fun hasSecondaryItem(): Boolean {
        return secondaryItem != null
    }

    val type: SliderItemType
        get() = mainItem.type

    val url: String?
        get() = mainItem.url

    fun ids(): List<String> {
        return listOfNotNull(mainItem.id, secondaryItem?.id)
    }
}
