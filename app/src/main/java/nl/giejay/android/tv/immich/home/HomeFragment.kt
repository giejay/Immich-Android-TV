package nl.giejay.android.tv.immich.home

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.album.AlbumFragment
import nl.giejay.android.tv.immich.assets.AllAssetFragment
import nl.giejay.android.tv.immich.assets.FolderFragment
import nl.giejay.android.tv.immich.assets.RandomAssetsFragment
import nl.giejay.android.tv.immich.assets.RecentAssetsFragment
import nl.giejay.android.tv.immich.assets.SimilarTimeAssetsFragment
import nl.giejay.android.tv.immich.people.PeopleFragment
import nl.giejay.android.tv.immich.settings.SettingsFragment
import nl.giejay.android.tv.immich.shared.fragment.GridFragment
import nl.giejay.android.tv.immich.search.SearchFragment
import nl.giejay.android.tv.immich.shared.prefs.CLASSIC_TIMELINE
import nl.giejay.android.tv.immich.shared.prefs.HIDDEN_HOME_ITEMS
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.timeline.TimelineComposeFragment
import nl.giejay.android.tv.immich.timeline.TimelineFragment
import nl.giejay.android.tv.immich.ui.immichComposeView
import nl.giejay.android.tv.immich.ui.nav.HomeNavRail
import nl.giejay.android.tv.immich.ui.nav.NavRailItem
import nl.giejay.android.tv.immich.ui.theme.ImmichIcons
import timber.log.Timber

/**
 * Home screen: a Compose side navigation rail plus the selected page hosted as a child fragment.
 *
 * Rail and content are siblings in the View hierarchy (not nested in one Compose tree) so D-Pad
 * focus moves between them with the regular View focus search, and the page content (Timeline,
 * grids, settings) is never relaid out when the rail expands.
 */
class HomeFragment : Fragment(), HomeNavHost {

    private class Page(val key: String, val icon: ImageVector, val separated: Boolean = false, val factory: () -> Fragment)

    private val pages: List<Page> by lazy {
        listOf(
            Page(getString(R.string.timeline), ImmichIcons.Timeline) { if (PreferenceManager.get(CLASSIC_TIMELINE)) TimelineFragment() else TimelineComposeFragment() },
            Page(getString(R.string.search), ImmichIcons.Search) { SearchFragment() },
            Page(getString(R.string.albums), ImmichIcons.Albums) {
                AlbumFragment().apply { arguments = bundleOf("selectionMode" to false) }
            },
            Page(getString(R.string.photos), ImmichIcons.Photos) { AllAssetFragment() },
            Page(getString(R.string.random), ImmichIcons.Random) { RandomAssetsFragment() },
            Page(getString(R.string.people), ImmichIcons.People) { PeopleFragment() },
            Page(getString(R.string.recent), ImmichIcons.Recent) { RecentAssetsFragment() },
            Page(getString(R.string.seasonal), ImmichIcons.Seasonal) { SimilarTimeAssetsFragment() },
            Page(getString(R.string.folders), ImmichIcons.Folders) { FolderFragment() },
            Page(getString(R.string.edit), ImmichIcons.Edit, separated = true) { GridFragment(hideProgressBar = true) },
            Page(getString(R.string.settings), ImmichIcons.Settings) { SettingsFragment() },
        )
    }

    private val editKey get() = getString(R.string.edit)
    private val settingsKey get() = getString(R.string.settings)

    private var selectedKey by mutableStateOf<String?>(null)
    private var editMode by mutableStateOf(false)
    /** Bumped when hidden items change so the rail re-reads the preference. */
    private var hiddenVersion by mutableIntStateOf(0)
    private var railFocused by mutableStateOf(true)

    /** True when the user was in the page content when the view was last destroyed (e.g. opened an album). */
    private var contentHadFocus = false
    private var navListener: NavTransitionListener? = null
    private var railView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingSelect: Runnable? = null

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            // Back from a page goes to the navigation first; back from the navigation leaves the app.
            railView?.requestFocus()
        }
    }

    override val isNavFocused: Boolean get() = railFocused

    override fun setNavTransitionListener(listener: NavTransitionListener?) {
        navListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("Loaded Home")
        selectedKey = savedInstanceState?.getString(STATE_SELECTED)
        // A fresh start (or a returning user) begins with the navigation focused.
        railFocused = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val root = view as FrameLayout
        val resources = resources
        val collapsedPx = resources.getDimensionPixelSize(R.dimen.home_nav_collapsed_width)
        val expandedPx = resources.getDimensionPixelSize(R.dimen.home_nav_expanded_width)

        val returningFromPage = contentHadFocus
        railFocused = !returningFromPage

        if (selectedKey == null || visiblePages().none { it.key == selectedKey }) {
            selectedKey = visiblePages().first().key
        }
        if (childFragmentManager.findFragmentById(R.id.home_content) == null) {
            showPage(pages.first { it.key == selectedKey })
        }

        val rail = immichComposeView(requireContext()) {
            val visible = visiblePages()
            @Suppress("UNUSED_EXPRESSION") hiddenVersion
            HomeNavRail(
                items = visible.map { NavRailItem(it.key, it.key, it.icon, it.separated) },
                selectedKey = selectedKey,
                expanded = railFocused,
                editMode = editMode,
                isHidden = { PreferenceManager.itemInStringSet(it, HIDDEN_HOME_ITEMS) },
                onItemFocused = ::onItemFocused,
                onItemClick = ::onItemClick,
                onEnterContent = ::onEnterContent,
                onRailFocusChanged = { onRailFocusChanged(it, collapsedPx, expandedPx) },
                editKey = editKey,
                doneLabel = getString(R.string.done),
            )
        }
        rail.layoutParams = FrameLayout.LayoutParams(if (railFocused) expandedPx else collapsedPx, ViewGroup.LayoutParams.MATCH_PARENT)
        railView = rail
        root.addView(rail)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        backCallback.isEnabled = !railFocused

        if (railFocused) {
            rail.post { rail.requestFocus() }
        } else {
            view.post { requestContentFocus() }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTED, selectedKey)
    }

    override fun onDestroyView() {
        contentHadFocus = !railFocused
        pendingSelect?.let { handler.removeCallbacks(it) }
        pendingSelect = null
        railView = null
        super.onDestroyView()
    }

    private fun visiblePages(): List<Page> =
        if (editMode) {
            pages.filter { it.key != settingsKey }
        } else {
            pages.filter { !PreferenceManager.itemInStringSet(it.key, HIDDEN_HOME_ITEMS) }
        }

    private fun showPage(page: Page) {
        selectedKey = page.key
        Timber.i("Going to show page: ${page.key}")
        childFragmentManager.beginTransaction()
            .replace(R.id.home_content, page.factory())
            .commitAllowingStateLoss()
    }

    private fun onItemFocused(key: String) {
        if (editMode) return
        // Moving over items must not build heavy pages (Timeline) for every item passed.
        // Always drop an older pending switch, also when focus came back to the shown page.
        pendingSelect?.let { handler.removeCallbacks(it) }
        pendingSelect = null
        if (key == selectedKey) return
        pendingSelect = Runnable { selectNow(key) }.also { handler.postDelayed(it, SELECT_DELAY_MS) }
    }

    private fun selectNow(key: String) {
        pendingSelect?.let { handler.removeCallbacks(it) }
        pendingSelect = null
        if (key == selectedKey || !isAdded) return
        pages.firstOrNull { it.key == key }?.let { showPage(it) }
    }

    private fun onItemClick(key: String) {
        when {
            key == editKey -> {
                editMode = !editMode
                if (!editMode) {
                    // leaving edit mode: hidden items disappear from the list
                    hiddenVersion++
                }
            }
            editMode -> {
                PreferenceManager.toggleStringSetItem(key, HIDDEN_HOME_ITEMS)
                hiddenVersion++
            }
            else -> {
                selectNow(key)
                onEnterContent(key)
            }
        }
    }

    private fun onEnterContent(key: String) {
        selectNow(key)
        childFragmentManager.executePendingTransactions()
        view?.post { requestContentFocus() }
    }

    private fun requestContentFocus() {
        view?.findViewById<ViewGroup>(R.id.home_content)?.requestFocus(View.FOCUS_RIGHT)
    }

    private fun onRailFocusChanged(focused: Boolean, collapsedPx: Int, expandedPx: Int) {
        if (focused == railFocused) return
        navListener?.onHeadersTransitionStart(focused)
        railFocused = focused
        backCallback.isEnabled = !focused
        railView?.let {
            it.layoutParams = it.layoutParams.apply { width = if (focused) expandedPx else collapsedPx }
        }
        navListener?.onHeadersTransitionStop(focused)
    }

    companion object {
        private const val STATE_SELECTED = "selected_page"
        private const val SELECT_DELAY_MS = 400L
    }
}
