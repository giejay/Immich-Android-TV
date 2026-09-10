package nl.giejay.android.tv.immich.settings

import android.app.Activity
import android.widget.Toast
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.screensaver.ScreenSaverType
import nl.giejay.android.tv.immich.shared.donate.DonateService
import nl.giejay.android.tv.immich.shared.prefs.DebugPrefScreen
import nl.giejay.android.tv.immich.shared.prefs.HomeScreenChannelsPrefScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ALBUMS
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_TYPE
import nl.giejay.android.tv.immich.shared.prefs.ScreensaverPrefScreen
import nl.giejay.android.tv.immich.shared.prefs.ViewPrefScreen


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
                            ImmichApplication.appContext!!.getString(R.string.server),
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
                            ImmichApplication.appContext!!.getString(R.string.view_settings),
                            null,
                            "view_settings",
                            "icon_view",
                            "icon_view"
                        ) {
                            findNavController().navigate(
                                HomeFragmentDirections.actionGlobalToSettingsDialog(ViewPrefScreen.key)
                            )
                        },
                        SettingsCard(
                            ImmichApplication.appContext!!.getString(R.string.screensaver),
                            null,
                            "screensaver",
                            "screensaver",
                            "ic_settings_settings"
                        ) {
                            findNavController().navigate(
                                HomeFragmentDirections.actionGlobalToSettingsDialog(ScreensaverPrefScreen.key)
                            )
                        },
                        SettingsCard(
                            ImmichApplication.appContext!!.getString(R.string.screensaver_preview),
                            null,
                            "screensaver_preview",
                            "view",
                            "view"
                        ) {
                            // Same guard as SCREENSAVER_SET: previewing albums without albums
                            // selected would only ever show a black screen.
                            if (PreferenceManager.get(SCREENSAVER_TYPE) == ScreenSaverType.ALBUMS &&
                                PreferenceManager.get(SCREENSAVER_ALBUMS).isEmpty()
                            ) {
                                Toast.makeText(
                                    requireContext(),
                                    getString(R.string.screensaver_set_select_albums_first),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                findNavController().navigate(
                                    HomeFragmentDirections.actionGlobalScreensaverPreview()
                                )
                            }
                        },
                        SettingsCard(
                            ImmichApplication.appContext!!.getString(R.string.home_screen_channels),
                            null,
                            "home_screen_channels",
                            "ic_settings_settings",
                            "ic_settings_settings"
                        ) {
                            findNavController().navigate(
                                HomeFragmentDirections.actionGlobalToSettingsDialog(HomeScreenChannelsPrefScreen.key)
                            )
                        },
                        SettingsCard(
                            ImmichApplication.appContext!!.getString(R.string.debug),
                            null,
                            "debug",
                            "bug",
                            "bug"
                        ) {
                            findNavController().navigate(
                                HomeFragmentDirections.actionGlobalToSettingsDialog(DebugPrefScreen.key)
                            )
                        },
                        SettingsCard(
                            ImmichApplication.appContext!!.getString(R.string.donate),
                            null,
                            "donate",
                            "donate",
                            "donate",
//                            donateService.isInitialized()
                        ) {
                            findNavController().navigate(
                                HomeFragmentDirections.actionHomeToDonate()
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
        adapter.addAll(0, cards.filter { it.visible })
        val headerItem = HeaderItem(ImmichApplication.appContext!!.getString(R.string.settings))
        return ListRow(headerItem, adapter)
    }
}
