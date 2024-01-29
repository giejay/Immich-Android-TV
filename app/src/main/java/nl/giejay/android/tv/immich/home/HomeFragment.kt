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
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.album.AlbumFragment
import nl.giejay.android.tv.immich.assets.AllAssetFragment
import nl.giejay.android.tv.immich.settings.SettingsFragment
import timber.log.Timber

class HomeFragment : BrowseSupportFragment() {
    private lateinit var mRowsAdapter: ArrayObjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.i("Loaded Home")

        setupUi()
        loadData()

        findNavController().popBackStack(R.id.homeFragment, false)

        mainFragmentRegistry.registerFragment(
            PageRow::class.java,
            PageRowFragmentFactory()
        )
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
        val headerItem1 = HeaderItem(HEADER_ID_1, HEADER_NAME_1)
        val pageRow1 = PageRow(headerItem1)
        mRowsAdapter.add(pageRow1)
        val headerItem2 = HeaderItem(HEADER_ID_2, HEADER_NAME_2)
        val pageRow2 = PageRow(headerItem2)
        mRowsAdapter.add(pageRow2)
        val headerItem3 = HeaderItem(HEADER_ID_3, HEADER_NAME_3)
        val pageRow3 = PageRow(headerItem3)
        mRowsAdapter.add(pageRow3)
    }

    private class PageRowFragmentFactory : FragmentFactory<Fragment>() {
        override fun createFragment(rowObj: Any): Fragment {
            val row = rowObj as Row
            Timber.i("Going to show page: ${row.headerItem.name}")
            return when (row.headerItem.id) {
                HEADER_ID_1 ->
                    AlbumFragment().apply {
                        arguments = bundleOf("selectionMode" to false)
                    }

                HEADER_ID_2 ->
                    AllAssetFragment()

                HEADER_ID_3 ->
                    SettingsFragment()

                else ->
                    throw IllegalStateException("Unknown fragment: $row")
            }
        }
    }

    companion object {
        private const val HEADER_ID_1: Long = 1
        private const val HEADER_NAME_1 = "Albums"
        private const val HEADER_ID_2: Long = 2
        private const val HEADER_NAME_2 = "Photos"
        private const val HEADER_ID_3: Long = 3
        private const val HEADER_NAME_3 = "Settings"
    }
}