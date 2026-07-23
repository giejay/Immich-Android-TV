package nl.giejay.android.tv.immich.assets

import arrow.core.Either
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.TimeBucketSummary
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_SHOW_MEDIA_COUNT

class AllAssetFragment : GenericAssetFragment() {

    override suspend fun loadItems(
        apiClient: ApiClient,
        page: Int,
        pageCount: Int
    ): Either<String, List<Asset>> {
        val order = if (currentSort == PhotosOrder.NEWEST_OLDEST) "desc" else "asc"
        return apiClient.listAssets(
            page = page,
            pageCount = pageCount,
            random = false,
            order = order,
            contentType = currentFilter,
            endDate = if (order == "desc") initialJumpDate else null,
            fromDate = if (order == "asc") initialJumpDate else null
        )
    }

    override suspend fun fetchBuckets(apiClient: ApiClient): Either<String, List<TimeBucketSummary>> {
        val order = if (currentSort == PhotosOrder.NEWEST_OLDEST) "desc" else "asc"
        return apiClient.getTimeBuckets(order = order)
    }

    override fun openPopUpMenu() {
        findNavController().navigate(
            HomeFragmentDirections.actionGlobalToSettingsDialog("generic_asset_settings")
        )
    }

    override fun showMediaCount(): Boolean {
        return PreferenceManager.get(SLIDER_SHOW_MEDIA_COUNT)
    }
}
