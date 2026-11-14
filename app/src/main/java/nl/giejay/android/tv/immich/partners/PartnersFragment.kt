package nl.giejay.android.tv.immich.partners

import android.widget.Toast
import androidx.navigation.fragment.findNavController
import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Partner
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment

/** Users who share their library with the signed-in user; selecting one opens their timeline. */
class PartnersFragment : VerticalCardGridFragment<Partner>() {

    override fun sortItems(items: List<Partner>): List<Partner> {
        return items
    }

    override suspend fun loadItems(
        apiClient: ApiClient,
        page: Int,
        pageCount: Int
    ): Either<String, List<Partner>> {
        if (page != startPage) {
            // the partners endpoint is not paginated
            return Either.Right(emptyList())
        }
        return apiClient.listPartners().onRight { partners ->
            if (partners.isEmpty()) showNoPartnersMessage()
        }
    }

    private suspend fun showNoPartnersMessage() = withContext(Dispatchers.Main) {
        if (isAdded) {
            Toast.makeText(requireContext(), R.string.partners_empty, Toast.LENGTH_LONG).show()
        }
    }

    override fun onItemSelected(card: Card, indexOf: Int) {
        // no use case yet
    }

    override fun onItemClicked(card: Card) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToPartnerTimelineFragment(card.id, card.title)
        )
    }

    override fun getBackgroundPicture(it: Partner): String? {
        return null
    }

    // The avatar endpoint needs userProfileImage.read, which isn't among the required API key
    // permissions, so partners get a placeholder image.
    override fun createCard(a: Partner): Card {
        return Card(a.name, "", a.id, "ic_partner", null, false)
    }
}
