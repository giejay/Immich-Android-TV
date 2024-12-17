package nl.giejay.android.tv.immich.assets

import arrow.core.Either
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset

class SimilarTimeAssetsFragment : GenericAssetFragment() {

    override suspend fun loadItems(
        apiClient: ApiClient,
        page: Int,
        pageCount: Int
    ): Either<String, List<Asset>> {
        return apiClient.similarAssets(page, pageCount, true)
    }
}