package nl.giejay.android.tv.immich.settings

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import nl.giejay.android.tv.immich.R
import nl.giejay.mediaslider.adapter.AlignOption
import nl.giejay.mediaslider.adapter.MetaDataItem

class MetaDataCustomizerAdapter(val context: Context, val metaData: MutableList<MetaDataItem>) : BaseAdapter() {
    private val layoutInflater: LayoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int {
        return metaData.size
    }

    override fun getItem(p0: Int): Any {
        return metaData[p0]
    }

    override fun getItemId(p0: Int): Long {
        return 0
    }

    override fun getView(p0: Int, p1: View?, p2: ViewGroup?): View {
        val metaDataItem = getItem(p0) as MetaDataItem
        val item: LinearLayout = layoutInflater.inflate(R.layout.fragment_metadata_item, null) as LinearLayout
        val textView = item.findViewById<TextView>(R.id.meta_data_customizer_text_view)
        if (metaDataItem.align == AlignOption.RIGHT) {
            val layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams.gravity = Gravity.END
            item.gravity = Gravity.END
            item.layoutParams = layoutParams
        }

//        item.findViewById<ImageButton>(R.id.delete_meta_data).setOnClickListener{
//            this.metaData.remove(metaDataItem)
//            notifyDataSetChanged()
//        }
//
//        item.findViewById<ImageButton>(R.id.down_meta_data).setOnClickListener{
//            val index = this.metaData.indexOf(metaDataItem)
//            if(index + 1 < this.metaData.size){
//                Collections.swap(this.metaData, index, index + 1)
//            }
//            notifyDataSetChanged()
//        }
//        item.findViewById<ImageButton>(R.id.up_meta_data).setOnClickListener{
//            val index = this.metaData.indexOf(metaDataItem)
//            if(index != 0){
//                Collections.swap(this.metaData, index, index - 1)
//            }
//            notifyDataSetChanged()
//        }

        textView.text = metaDataItem.getTitle()
        return item
    }
}