package nl.giejay.android.tv.immich.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import nl.giejay.android.tv.immich.R

class QTVRowPresenter : Presenter() {
    var editMode: Boolean = false

    override fun onCreateViewHolder(parent: ViewGroup): QTVRowViewHolder {
        val root: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.presenter_row, parent, false)

        val viewHolder = QTVRowViewHolder(root)
        return viewHolder
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val headerItem = if (item == null) null else (item as Row).headerItem
        val vh = viewHolder as QTVRowViewHolder
        vh.tvTitle.text = headerItem?.name

        if (editMode) {
            if (headerItem?.name == "Edit") {
                vh.tvTitle.text = "Done"
            } else if (headerItem?.contentDescription == "0") {
                vh.tvTitle.text = vh.tvTitle.text.toString() + " (Hidden)"
            } else {
                vh.tvTitle.text = vh.tvTitle.text.toString() + " (Shown)"
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val vh = viewHolder as QTVRowViewHolder
        vh.tvTitle.text = null
    }

}