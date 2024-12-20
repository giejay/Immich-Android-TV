package nl.giejay.android.tv.immich.home

import androidx.leanback.app.HeadersSupportFragment
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.CustomRowHeaderPresenter
import androidx.leanback.widget.DividerPresenter
import androidx.leanback.widget.DividerRow
import androidx.leanback.widget.PresenterSelector
import androidx.leanback.widget.Row
import androidx.leanback.widget.SectionRow

class CustomHeadersSupportFragment : HeadersSupportFragment() {

    val sHeaderPresenter: PresenterSelector = ClassPresenterSelector()
        .addClassPresenter(DividerRow::class.java, DividerPresenter())
        .addClassPresenter(SectionRow::class.java,
            CustomRowHeaderPresenter())
        .addClassPresenter(Row::class.java, CustomRowHeaderPresenter())

    init {
        presenterSelector = sHeaderPresenter
    }
}