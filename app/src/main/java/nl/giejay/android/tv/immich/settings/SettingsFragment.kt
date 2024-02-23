package nl.giejay.android.tv.immich.settings

import android.R
import android.app.Activity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.fragment.app.FragmentTransaction
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.home.HomeFragmentDirections


class SettingsFragment : RowsSupportFragment() {
    private val mRowsAdapter: ArrayObjectAdapter

    init {
        val selector = ListRowPresenter()
        selector.setNumRows(1)
        mRowsAdapter = ArrayObjectAdapter(selector)
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            val card = item as SettingsCard
            card.onClick()
        }
        adapter = mRowsAdapter
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        loadData()
    }

    private fun loadData() {
        if (isAdded) {
            mRowsAdapter.add(
                createCardRow(
                    listOf(
                        SettingsCard(
                            "Server",
                            null,
                            "server",
                            "ic_settings_settings",
                            "ic_settings_settings"
                        ) {
                            findNavController().navigate(
                                HomeFragmentDirections.actionGlobalSignInFragment()
                            )
                        },
                        SettingsCard(
                            "View settings",
                            null,
                            "view_settings",
                            "icon_view",
                            "icon_view"
                        ) {
                            val ft: FragmentTransaction =
                                parentFragmentManager.beginTransaction()
                            ft.replace(nl.giejay.android.tv.immich.R.id.fragment_settings_holder_inner, ViewSettingsFragment())
                            ft.commit()
                            val findViewById =
                                requireActivity().findViewById<TextView>(nl.giejay.android.tv.immich.R.id.test)
                            findViewById.bringToFront()
                            findViewById.findFocus()
                        },
                        SettingsCard(
                            "Screensaver",
                            null,
                            "screensaver",
                            "screensaver",
                            "ic_settings_settings"
                        ) {
                            findNavController().navigate(
                                HomeFragmentDirections.actionHomeFragmentToScreensaverSettings()
                            )
                        },
                        SettingsCard(
                            "Debug",
                            null,
                            "debug",
                            "bug",
                            "bug"
                        ) {
                            findNavController().navigate(
                                HomeFragmentDirections.actionHomeFragmentToDebugSettings()
                            )
                        }
                    )
                )
            )
            mainFragmentAdapter.fragmentHost?.notifyDataReady(
                mainFragmentAdapter
            )
        }
    }

    private fun createCardRow(cards: List<SettingsCard>): ListRow {
        val iconCardPresenter = SettingsIconPresenter(requireContext())
        val adapter = ArrayObjectAdapter(iconCardPresenter)
        adapter.addAll(0, cards)
        val headerItem = HeaderItem("Settings")
        return ListRow(headerItem, adapter)
    }
}