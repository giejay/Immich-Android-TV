package nl.giejay.android.tv.immich.people

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import arrow.core.Either
import nl.giejay.android.tv.immich.album.SelectionType
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Person
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment
import nl.giejay.android.tv.immich.shared.prefs.ALBUMS_SORTING
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_PEOPLE
import nl.giejay.android.tv.immich.shared.prefs.StringSetPref
import nl.giejay.android.tv.immich.shared.prefs.ViewPrefScreen
import timber.log.Timber

class PeopleFragment : VerticalCardGridFragment<Person>() {
    private lateinit var selectionTypeKey: StringSetPref
    private val selectionType: SelectionType
        get() = (arguments?.getString("selectionType")?.let { SelectionType.valueOf(it) } ?: SelectionType.SET_SCREENSAVER) as SelectionType

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectionTypeKey =
        when (selectionType) {
            SelectionType.SET_SCREENSAVER -> {
                SCREENSAVER_PEOPLE
            }
            else -> { SCREENSAVER_PEOPLE } //Other options not required at this time: EXCLUDED_ALBUMS, SET_CHANNEL_ALBUMS
        }

        PreferenceManager.subscribe(ALBUMS_SORTING) {
            resortItems()
        }
    }
    override fun sortItems(items: List<Person>): List<Person> {
        if(selectionMode) {
            val sorted = items.sortedBy { it.name }
            val selected = sorted.filter { PreferenceManager.get(selectionTypeKey).contains(it.id.toString()) }
            val unselected = sorted.filter { !selected.contains(it) }
            return selected + unselected
        } else {
            try {
                return items.sortedBy { it.name }
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Could not sort using sorting order: name")
                return items
            }
        }
    }

    override suspend fun loadItems(
        apiClient: ApiClient,
        page: Int,
        pageCount: Int
    ): Either<String, List<Person>> {
        if (page == startPage) {
            // no pagination possible yet!
            return apiClient.listPeople()
        }
        return Either.Right(emptyList())
    }

    override fun onItemSelected(card: Card, indexOf: Int) {
        val currentPeople = PreferenceManager.get(selectionTypeKey)
        if (card.selected) {
            PreferenceManager.save(selectionTypeKey, currentPeople + card.id)
        } else {
            PreferenceManager.save(selectionTypeKey, currentPeople - card.id)
        }
    }

    override fun onItemClicked(card: Card) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToPersonAssetsFragment(
                card.id,
                card.title
            )
        )
    }

    override fun getBackgroundPicture(it: Person): String? {
        return null
    }

    override fun createCard(a: Person): Card {
        return Card(
            a.name ?: "Unknown",
            "",
            a.id.toString(),
            ApiUtil.getPersonThumbnail(a.id),
            ApiUtil.getPersonThumbnail(a.id),
            if (selectionMode) PreferenceManager.get(selectionTypeKey).contains(a.id.toString()) else false
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if(this.selectionMode){
            val returnTo = arguments?.getString("returnTo")
            val target = if (returnTo.isNullOrBlank()) ViewPrefScreen.key else returnTo
            findNavController().navigate(
                HomeFragmentDirections.actionGlobalToSettingsDialog(target)
            )
        }
    }
}