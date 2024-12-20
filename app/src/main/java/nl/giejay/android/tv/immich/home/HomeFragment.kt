package nl.giejay.android.tv.immich.home

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DividerPresenter
import androidx.leanback.widget.DividerRow
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.PageRow
import androidx.leanback.widget.PresenterSelector
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowHeaderPresenter
import androidx.leanback.widget.SectionRow
import nl.giejay.android.tv.immich.album.AlbumFragment
import nl.giejay.android.tv.immich.assets.AllAssetFragment
import nl.giejay.android.tv.immich.assets.RandomAssetsFragment
import nl.giejay.android.tv.immich.assets.RecentAssetsFragment
import nl.giejay.android.tv.immich.assets.SimilarTimeAssetsFragment
import nl.giejay.android.tv.immich.people.PeopleFragment
import nl.giejay.android.tv.immich.settings.SettingsFragment
import nl.giejay.android.tv.immich.shared.fragment.GridFragment
import timber.log.Timber

class HomeFragment : BrowseSupportFragment() {
    private lateinit var mRowsAdapter: ArrayObjectAdapter
    val qtvRowPresenter = QTVRowPresenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.i("Loaded Home")

        setupUi()
        loadData()

        mainFragmentRegistry.registerFragment(PageRow::class.java, PageRowFragmentFactory())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headersSupportFragment.setOnHeaderViewSelectedListener { _, row ->
            title = row.headerItem.name
            selectedPosition = mRowsAdapter.indexOf(row)
        }

        headersSupportFragment.setOnHeaderClickedListener { _, row ->
            if (row.headerItem.name == "Edit") {
                qtvRowPresenter.editMode = !qtvRowPresenter.editMode
                val unmodifiableList = mRowsAdapter.unmodifiableList<PageRow>()
                adapter.notifyItemRangeChanged(0, adapter.size() - 1);
            } else if(qtvRowPresenter.editMode){
                row.headerItem.contentDescription = if(row.headerItem.contentDescription == "0") "1" else "0"
                adapter.notifyItemRangeChanged(0, adapter.size() - 1);
            } else{
                if (!this.isInHeadersTransition) {
                    this.startHeadersTransition(false)
                    this.mainFragment.requireView().requestFocus()
                }
            }
        }

    }

    private fun setupUi() {
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = resources.getColor(android.R.color.black)
        title = "Albums"
//        setOnSearchClickedListener {
//            Toast.makeText(
//                activity, "Search!", Toast.LENGTH_SHORT
//            ).show()
//        }

        // Lines of code to be added
        val sHeaderPresenter: PresenterSelector = ClassPresenterSelector()
            .addClassPresenter(DividerRow::class.java, DividerPresenter())
            .addClassPresenter(
                SectionRow::class.java,
                RowHeaderPresenter()
            )
            .addClassPresenter(Row::class.java, qtvRowPresenter)

        setHeaderPresenterSelector(sHeaderPresenter)
    }

    private fun loadData() {
        mRowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = mRowsAdapter
        createRows()
    }

    private fun createRows() {
        HEADERS.forEachIndexed { index, header -> mRowsAdapter.add(PageRow(HeaderItem(index.toLong(), header.name))) }
    }

    private class PageRowFragmentFactory : FragmentFactory<Fragment>() {
        override fun createFragment(rowObj: Any): Fragment {
            val row = rowObj as Row
            Timber.i("Going to show page: ${row.headerItem.name}")
            return HEADERS[row.headerItem.id.toInt()].fragment()
        }
    }

    companion object {
        private val HEADERS: List<Header> = listOf(
            Header("Albums") {
                AlbumFragment().apply {
                    arguments = bundleOf("selectionMode" to false)
                }
            },
            Header("Photos") { AllAssetFragment() },
            Header("Random") { RandomAssetsFragment() },
            Header("People") { PeopleFragment() },
            Header("Recent") { RecentAssetsFragment() },
            Header("Seasonal") { SimilarTimeAssetsFragment() },
            Header("Edit") { GridFragment() },
            Header("Settings") { SettingsFragment() },
        )
    }
}

class Header(val name: String, var show: Boolean = false, val fragment: () -> Fragment)