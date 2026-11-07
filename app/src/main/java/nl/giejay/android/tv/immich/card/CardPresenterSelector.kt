package nl.giejay.android.tv.immich.card

import android.content.Context
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.PresenterSelector
import nl.giejay.android.tv.immich.ui.card.ImmichCardPresenter

class CardPresenterSelector(val context: Context): PresenterSelector() {
    private val presenter = ImmichCardPresenter()

    override fun getPresenter(item: Any?): Presenter {
        return presenter
    }
}