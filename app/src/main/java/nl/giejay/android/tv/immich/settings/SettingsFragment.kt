package nl.giejay.android.tv.immich.settings

import android.app.Activity
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.donate.DonateService


class SettingsFragment : RowsSupportFragment() {
    private val mRowsAdapter: ArrayObjectAdapter
    private lateinit var donateService: DonateService

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
        donateService = DonateService(activity)
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
                            findNavController().navigate(
                                HomeFragmentDirections.actionGlobalToSettingsDialog("view")
                            )
                        },
                        SettingsCard(
                            "Screensaver",
                            null,
                            "screensaver",
                            "screensaver",
                            "ic_settings_settings"
                        ) {
                            findNavController().navigate(
                                HomeFragmentDirections.actionGlobalToSettingsDialog("screensaver")
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
                                HomeFragmentDirections.actionGlobalToSettingsDialog("debug")
                            )
                        },
                        SettingsCard(
                            "Donate",
                            null,
                            "donate",
                            "donate",
                            "donate",
//                            donateService.isInitialized()
                        ) {
                            donateService.showDonationOptions(requireActivity())
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
        adapter.addAll(0, cards.filter { it.visible })
        val headerItem = HeaderItem("Settings")
        return ListRow(headerItem, adapter)
    }
}