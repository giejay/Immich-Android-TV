package nl.giejay.android.tv.immich.search

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.Person
import nl.giejay.android.tv.immich.assets.GenericAssetFragment
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SEARCH_HISTORY
import nl.giejay.android.tv.immich.ui.search.SearchHeader
import nl.giejay.android.tv.immich.ui.search.SearchStatus

/**
 * Search page: a text field on top, the hits as the usual photo grid below (opens the slider like every
 * other grid). Text is searched by image content when the server can, otherwise by file name,
 * description and place; a matching person name offers that person's photos.
 */
class SearchFragment : GenericAssetFragment() {
    private var submittedQuery by mutableStateOf("")
    private var status by mutableStateOf(SearchStatus.IDLE)
    private var people by mutableStateOf<List<Person>>(emptyList())
    private var selectedPerson by mutableStateOf<Person?>(null)
    private var recents by mutableStateOf<List<String>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        recents = SearchHistory.parse(PreferenceManager.get(SEARCH_HISTORY))
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressBar?.visibility = View.GONE
        setHeader {
            SearchHeader(
                submittedQuery = submittedQuery,
                status = status,
                people = people.mapNotNull { it.name },
                selectedPerson = selectedPerson?.name,
                recents = recents,
                onSubmit = ::submit,
                onPerson = { index -> people.getOrNull(index)?.let(::selectPerson) },
                onClearPerson = ::clearPerson,
                onRecent = ::submit,
            )
        }
    }

    override suspend fun loadItems(apiClient: ApiClient, page: Int, pageCount: Int): Either<String, List<Asset>> {
        selectedPerson?.let { person ->
            return apiClient.listAssets(
                page, pageCount, order = "desc", contentType = currentFilter, personIds = listOf(person.id),
            ).map { it.assets }
        }
        val query = submittedQuery
        if (query.isBlank()) {
            allPagesLoaded = true
            return Either.Right(emptyList())
        }
        return apiClient.searchAssets(query, page, pageCount, currentFilter).map { it.assets }
    }

    /** Keep the order of relevance (smart search) or recency (metadata search) instead of the grid sorting. */
    override fun sortItems(items: List<Asset>): List<Asset> = items

    override fun setTitle(response: List<Asset>) {
        status = when {
            submittedQuery.isBlank() && selectedPerson == null -> SearchStatus.IDLE
            response.isEmpty() -> SearchStatus.NO_RESULTS
            else -> SearchStatus.RESULTS
        }
    }

    /** Right on an empty page would open the grid settings; the search page has its own controls instead. */
    override fun openPopUpMenu() = Unit

    private fun submit(text: String) {
        val query = text.trim()
        if (query.isEmpty()) return
        recents = SearchHistory.add(recents, query)
        PreferenceManager.save(SEARCH_HISTORY, SearchHistory.serialize(recents))
        selectedPerson = null
        people = emptyList()
        submittedQuery = query
        startSearch()
        ioScope.launch {
            apiClient.searchPeople(query).fold({ }, { found ->
                withContext(Dispatchers.Main) { people = found.take(MAX_PEOPLE) }
            })
        }
    }

    private fun selectPerson(person: Person) {
        selectedPerson = person
        startSearch()
    }

    private fun clearPerson() {
        selectedPerson = null
        if (submittedQuery.isBlank()) {
            status = SearchStatus.IDLE
            clearState()
        } else {
            startSearch()
        }
    }

    private fun startSearch() {
        status = SearchStatus.LOADING
        clearState()
        allPagesLoaded = false
        fetchInitialItems()
    }

    private companion object {
        const val MAX_PEOPLE = 6
    }
}
