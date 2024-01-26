package nl.giejay.android.tv.immich.card

import android.content.Context
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.PresenterSelector

class CardPresenterSelector(val context: Context): PresenterSelector() {
    override fun getPresenter(item: Any?): Presenter {
        return CardPresenter(context)
    }
}