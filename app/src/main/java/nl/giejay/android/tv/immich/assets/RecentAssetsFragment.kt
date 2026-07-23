package nl.giejay.android.tv.immich.assets

import arrow.core.Either
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.home.HomeFragmentDirections

class RecentAssetsFragment : GenericAssetFragment() {

    override suspend fun loadItems(
        apiClient: ApiClient,
        page: Int,
        pageCount: Int
    ): Either<String, List<Asset>> {
        return apiClient.recentAssets(page, pageCount, currentFilter)
    }

    override fun openPopUpMenu() {
        findNavController().navigate(
            HomeFragmentDirections.actionGlobalToSettingsDialog("generic_asset_settings")
        )
    }
}
