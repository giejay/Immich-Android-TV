package nl.giejay.android.tv.immich.people

import android.os.Bundle
import arrow.core.Either
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.assets.GenericAssetFragment
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import java.util.UUID

class PersonAssetsFragment : GenericAssetFragment() {
    private lateinit var personId: String
    private lateinit var personName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        personId = PersonAssetsFragmentArgs.fromBundle(requireArguments()).personId
        personName = PersonAssetsFragmentArgs.fromBundle(requireArguments()).personName
        super.onCreate(savedInstanceState)
    }

    override suspend fun loadItems(
        apiClient: ApiClient,
        page: Int,
        pageCount: Int
    ): Either<String, List<Asset>> {
        return apiClient.listAssets(page,
            pageCount,
            random = true,
            order = "desc",
            contentType = currentFilter,
            personIds = listOf(UUID.fromString(personId))).map { it.shuffled() }
    }

    override fun showMediaCount(): Boolean {
        return false
    }

    override fun openPopUpMenu() {
        findNavController().navigate(
            HomeFragmentDirections.actionGlobalToSettingsDialog("generic_asset_settings")
        )
    }

    override fun setTitle(response: List<Asset>) {
        title = personName
    }
}