package nl.giejay.android.tv.immich.assets

import arrow.core.Either
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager

class AllAssetFragment : GenericAssetFragment() {

    override suspend fun loadItems(
        apiClient: ApiClient,
        page: Int,
        pageCount: Int
    ): Either<String, List<Asset>> {
        return apiClient.listAssets(page,
            pageCount,
            false,
            if (PreferenceManager.allAssetsOrder() == PhotosOrder.NEWEST_OLDEST) "desc" else "asc")
    }

    override fun showMediaCount(): Boolean {
        return PreferenceManager.sliderShowMediaCount()
    }
}