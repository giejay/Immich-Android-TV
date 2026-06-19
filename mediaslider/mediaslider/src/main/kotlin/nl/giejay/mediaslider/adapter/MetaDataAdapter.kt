package nl.giejay.mediaslider.adapter

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.RelativeLayout
import android.widget.TextView
import com.google.gson.JsonObject
import com.zeuskartik.mediaslider.R
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.model.SliderItem
import nl.giejay.mediaslider.util.MetaDataConverter

enum class AlignOption {
    LEFT, RIGHT
}

sealed class MetaDataItem(val type: MetaDataType) {
    val textViewResourceId: Int = R.id.textView
    abstract val align: AlignOption
    abstract val fontSize: Int
    abstract val padding: Int
    abstract fun createView(layoutInflater: LayoutInflater): View
    abstract suspend fun getValue(context: Context, item: SliderItem, index: Int, totalCount: Int): String?
    abstract fun updateView(view: TextView, value: String?)
    fun withAlign(align: AlignOption): MetaDataItem {
        return create(type, align, padding, fontSize)
    }

    abstract fun getTitle(context: Context): String

    companion object {
        const val DEFAULT_PADDING = 0
        fun create(type: MetaDataType, align: AlignOption, padding: Int, fontSize: Int): MetaDataItem {
            // being able to change from a metadataclock to a mediacount or slideritem in metadata customizer
            val obj = JsonObject()
            obj.addProperty("type", type.toString())
            obj.addProperty("align", align.toString())
            obj.addProperty("padding", padding)
            obj.addProperty("fontSize", fontSize)
            return MetaDataConverter.metaDataFromJsonObject(obj)
        }
    }
}

data class MetaDataClock(override val align: AlignOption,
                         override val fontSize: Int = MetaDataType.CLOCK.defaultFontSize,
                         override val padding: Int = DEFAULT_PADDING) : MetaDataItem(MetaDataType.CLOCK) {

    override fun createView(layoutInflater: LayoutInflater): View {
        return layoutInflater.inflate(R.layout.metadata_item_clock, null)
    }

    override suspend fun getValue(context: Context, item: SliderItem, index: Int, totalCount: Int): String {
        return context.getString(R.string.clock)
    }

    override fun updateView(view: TextView, value: String?) {
        // no-op
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.clock)
    }
}

data class MetaDataMediaCount(override val align: AlignOption,
                              override val fontSize: Int = MetaDataType.MEDIA_COUNT.defaultFontSize,
                              override val padding: Int = DEFAULT_PADDING) : MetaDataItem(MetaDataType.MEDIA_COUNT) {
    override fun createView(layoutInflater: LayoutInflater): View {
        return layoutInflater.inflate(R.layout.metadata_item, null)
    }

    override suspend fun getValue(context: Context, item: SliderItem, index: Int, totalCount: Int): String {
        return "${index + 1}/$totalCount"
    }

    override fun updateView(view: TextView, value: String?) {
        view.text = value
    }

    override fun getTitle(context: Context): String {
        return context.getString(R.string.media_count)
    }
}

data class MetaDataSliderItem(val metaDataType: MetaDataType, override val align: AlignOption,
                              override val fontSize: Int = metaDataType.defaultFontSize,
                              override val padding: Int = DEFAULT_PADDING) : MetaDataItem(metaDataType) {

    override fun createView(layoutInflater: LayoutInflater): View {
        return layoutInflater.inflate(R.layout.metadata_item, null)
    }

    override suspend fun getValue(context: Context, item: SliderItem, index: Int, totalCount: Int): String? {
        return item.get(metaDataType)
    }

    override fun updateView(view: TextView, value: String?) {
        view.text = value
    }

    override fun getTitle(context: Context): String {
        return metaDataType.getTitle(context)
    }
}

class MetaDataAdapter(val context: Context,
                      val items: List<MetaDataItem>,
                      private val portraitViewItems: List<MetaDataItem>,
                      private val getCurrentItem: () -> SliderItem,
                      private val portraitMode: () -> Boolean) : BaseAdapter() {
    private val layoutInflater: LayoutInflater = LayoutInflater.from(context)
    private val viewsPerType: MutableMap<MetaDataType, View> = mutableMapOf()
    private val stateForItem = mutableMapOf<String, String?>()

    override fun getCount(): Int {
        return getItemsToShow().size
    }

    fun getItemsToShow(): List<MetaDataItem> = (if (portraitMode()) portraitViewItems else items)

    override fun getItem(p0: Int): Any {
        return getItemsToShow()[p0]
    }

    override fun getItemId(p0: Int): Long {
        return getItemsToShow()[p0].type.ordinal.toLong()
    }

    override fun isEnabled(position: Int): Boolean {
        return false
    }

    override fun areAllItemsEnabled(): Boolean {
        return false
    }

    override fun getView(p0: Int, p1: View?, p2: ViewGroup?): View {
        val key = getCurrentItem().id + p0
        val value = stateForItem[key]
        if(value.isNullOrBlank()) {
            return View(context).apply {
                layoutParams = RelativeLayout.LayoutParams(0, 0)
            }
        }
        val item = getItem(p0) as MetaDataItem
        // can't use p1 because the list might differ for every photo/adapter
        val view = viewsPerType.getOrPut(item.type) { item.createView(layoutInflater) }
        val textView = view.findViewById<TextView>(item.textViewResourceId)
        if (item.align == AlignOption.RIGHT) {
            val params = textView.layoutParams as RelativeLayout.LayoutParams
            textView.gravity = Gravity.RIGHT
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            view.layoutParams = params
        }
        textView.textSize = item.fontSize.toFloat()
        textView.setPadding(textView.paddingLeft, item.padding, textView.paddingRight, item.padding)
        item.updateView(textView, value)
        return view
    }

    fun updateState(sliderItemId: String, metaDataIndex: Int, value: String?) {
        stateForItem[sliderItemId + metaDataIndex] = value
    }

    fun hasStateForItem(id: String, metaDataIndex: Int): Boolean {
        return !stateForItem[id + metaDataIndex].isNullOrBlank()
    }
}
