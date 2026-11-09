package nl.giejay.android.tv.immich.places

import android.os.Bundle
import arrow.core.Either
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.assets.GenericAssetFragment
import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder
import nl.giejay.android.tv.immich.tags.TagsFragment

/** The photos of one place (city) or one tag; opened from [PlacesFragment] / [TagsFragment]. */
class FilteredAssetsFragment : GenericAssetFragment() {
    private lateinit var filterType: String
    private lateinit var filterValue: String
    private lateinit var filterTitle: String

    override fun onCreate(savedInstanceState: Bundle?) {
        val args = FilteredAssetsFragmentArgs.fromBundle(requireArguments())
        filterType = args.filterType
        filterValue = args.filterValue
        filterTitle = args.filterTitle
        super.onCreate(savedInstanceState)
    }

    override suspend fun loadItems(apiClient: ApiClient, page: Int, pageCount: Int): Either<String, List<Asset>> =
        apiClient.listAssetsByFilter(
            page = page,
            pageCount = pageCount,
            contentType = currentFilter,
            order = if (currentSort == PhotosOrder.NEWEST_OLDEST) "desc" else "asc",
            city = if (filterType == PlacesFragment.FILTER_CITY) filterValue else null,
            tagIds = if (filterType == TagsFragment.FILTER_TAG) listOf(filterValue) else emptyList(),
        ).map { it.assets }

    override fun showMediaCount(): Boolean = false

    override fun setTitle(response: List<Asset>) {
        title = filterTitle
    }
}
