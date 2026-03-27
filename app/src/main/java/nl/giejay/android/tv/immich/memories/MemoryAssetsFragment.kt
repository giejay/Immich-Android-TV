package nl.giejay.android.tv.immich.memories

import android.os.Bundle
import arrow.core.Either
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.assets.GenericAssetFragment

class MemoryAssetsFragment : GenericAssetFragment() {
    private lateinit var memoryId: String
    private lateinit var memoryTitle: String

    override fun onCreate(savedInstanceState: Bundle?) {
        memoryId = MemoryAssetsFragmentArgs.fromBundle(requireArguments()).memoryId
        memoryTitle = MemoryAssetsFragmentArgs.fromBundle(requireArguments()).memoryTitle
        super.onCreate(savedInstanceState)
    }

    override suspend fun loadItems(
        apiClient: ApiClient,
        page: Int,
        pageCount: Int
    ): Either<String, List<Asset>> {
        if (page == startPage) {
            return apiClient.getMemory(memoryId).map { it.assets }
        }
        return Either.Right(emptyList())
    }

    override fun setTitle(response: List<Asset>) {
        title = memoryTitle
    }
}
