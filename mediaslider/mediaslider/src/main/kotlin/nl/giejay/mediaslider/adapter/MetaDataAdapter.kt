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

/**
 * List adapter for EXIF / metadata columns. Only exposes non-blank rows for the
 * currently [bind]ed asset so async fetches do not leave stale or empty rows visible.
 */
class MetaDataAdapter(val context: Context,
                      val items: List<MetaDataItem>,
                      private val portraitViewItems: List<MetaDataItem>,
                      private val getCurrentItem: () -> SliderItem,
                      private val portraitMode: () -> Boolean) : BaseAdapter() {
    private val layoutInflater: LayoutInflater = LayoutInflater.from(context)
    private val stateForItem = mutableMapOf<String, String?>()
    /** Keys that have completed a fetch (including blank/null results). */
    private val fetchedKeys = mutableSetOf<String>()
    /** Asset id whose rows are currently shown; null after [clearViews]. */
    private var boundAssetId: String? = null

    fun getItemsToShow(): List<MetaDataItem> = (if (portraitMode()) portraitViewItems else items)

    fun isFullyFetched(assetId: String): Boolean {
        val toShow = getItemsToShow()
        if (toShow.isEmpty()) return true
        return toShow.indices.all { hasStateForItem(assetId, it) }
    }

    /**
     * True when this column has nothing to show, or when [assetId]'s values are fetched
     * and the list has been [bind]ed for that asset (not still cleared).
     */
    fun isReadyFor(assetId: String): Boolean {
        val toShow = getItemsToShow()
        if (toShow.isEmpty()) return true
        return isFullyFetched(assetId) && boundAssetId == assetId
    }

    fun clearState(assetId: String) {
        val prefix = "$assetId#"
        stateForItem.keys.filter { it.startsWith(prefix) }.forEach { stateForItem.remove(it) }
        fetchedKeys.removeAll { it.startsWith(prefix) }
    }

    /** Hide rows immediately (e.g. on page change) so previous EXIF never lingers. */
    fun clearViews() {
        boundAssetId = null
        notifyDataSetChanged()
    }

    /** Show fetched non-blank rows for the current asset. */
    fun bind() {
        boundAssetId = try {
            getCurrentItem().id
        } catch (_: Exception) {
            null
        }
        notifyDataSetChanged()
    }

    fun updateState(sliderItemId: String, metaDataIndex: Int, value: String?) {
        val key = stateKey(sliderItemId, metaDataIndex)
        stateForItem[key] = value
        fetchedKeys.add(key)
    }

    fun hasStateForItem(id: String, metaDataIndex: Int): Boolean {
        return stateKey(id, metaDataIndex) in fetchedKeys
    }

    private fun visibleRows(): List<Pair<MetaDataItem, String>> {
        val assetId = boundAssetId ?: return emptyList()
        return getItemsToShow().mapIndexedNotNull { index, item ->
            val value = stateForItem[stateKey(assetId, index)]
            if (value.isNullOrBlank()) null else item to value
        }
    }

    override fun getCount(): Int = visibleRows().size

    override fun getItem(position: Int): Any = visibleRows()[position].first

    override fun getItemId(position: Int): Long =
        visibleRows()[position].first.type.ordinal.toLong()

    override fun getViewTypeCount(): Int = MetaDataType.entries.size.coerceAtLeast(1)

    override fun getItemViewType(position: Int): Int =
        visibleRows()[position].first.type.ordinal

    override fun isEnabled(position: Int): Boolean = false

    override fun areAllItemsEnabled(): Boolean = false

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val (item, value) = visibleRows()[position]
        val view = convertView ?: item.createView(layoutInflater)
        val textView = view.findViewById<TextView>(item.textViewResourceId)
        if (item.align == AlignOption.RIGHT) {
            (textView.layoutParams as? RelativeLayout.LayoutParams)?.let { params ->
                textView.gravity = Gravity.RIGHT
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                textView.layoutParams = params
            }
        }
        textView.textSize = item.fontSize.toFloat()
        textView.setPadding(textView.paddingLeft, item.padding, textView.paddingRight, item.padding)
        item.updateView(textView, value)
        return view
    }

    private fun stateKey(assetId: String, metaDataIndex: Int): String = "$assetId#$metaDataIndex"
}
