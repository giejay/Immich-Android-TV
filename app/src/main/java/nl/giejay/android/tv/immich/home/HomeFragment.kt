package nl.giejay.android.tv.immich.home

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.PageRow
import androidx.leanback.widget.Row
import nl.giejay.android.tv.immich.album.AlbumFragment
import nl.giejay.android.tv.immich.assets.AllAssetFragment
import nl.giejay.android.tv.immich.assets.RandomAssetsFragment
import nl.giejay.android.tv.immich.assets.RecentAssetsFragment
import nl.giejay.android.tv.immich.assets.SimilarTimeAssetsFragment
import nl.giejay.android.tv.immich.people.PeopleFragment
import nl.giejay.android.tv.immich.settings.SettingsFragment
import timber.log.Timber

class HomeFragment : BrowseSupportFragment() {
    private lateinit var mRowsAdapter: ArrayObjectAdapter

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
    }

    private fun setupUi() {
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = resources.getColor(android.R.color.black)
        title = "Albums"
//        setOnSearchClickedListener {
//            Toast.makeText(
//                activity, "Search!", Toast.LENGTH_SHORT
//            )
//                .show()
//        }
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
            Header("Settings") { SettingsFragment() },
        )
    }
}

class Header(val name: String, val fragment: () -> Fragment)