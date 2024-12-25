package nl.giejay.android.tv.immich.assets

import android.os.Bundle
import arrow.core.Either
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.card.Card

class FolderAssetsFragment : GenericAssetFragment() {
    private lateinit var path: String

    override fun onCreate(savedInstanceState: Bundle?) {
        path = FolderAssetsFragmentArgs.fromBundle(requireArguments()).path
        super.onCreate(savedInstanceState)
    }

    override suspend fun loadItems(
        apiClient: ApiClient,
        page: Int,
        pageCount: Int
    ): Either<String, List<Asset>> {
        return apiClient.listAssetsForFolder(path)
    }

    override fun onItemSelected(card: Card, indexOf: Int) {
       // no use case yet
    }
}