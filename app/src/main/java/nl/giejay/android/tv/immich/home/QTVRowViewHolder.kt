package nl.giejay.android.tv.immich.home

import android.view.View
import android.widget.TextView
import androidx.leanback.widget.RowHeaderPresenter
import nl.giejay.android.tv.immich.R

class QTVRowViewHolder(view: View) : RowHeaderPresenter.ViewHolder(view) {
    val tvTitle: TextView
    init {
        tvTitle = view.findViewById(R.id.tvTitle)
    }
}