package nl.giejay.android.tv.immich.memories

import androidx.navigation.fragment.findNavController
import arrow.core.Either
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Memory
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment
import java.time.LocalDate
import java.time.ZoneId

class MemoriesFragment : VerticalCardGridFragment<Memory>() {

    override fun sortItems(items: List<Memory>): List<Memory> {
        return items
    }

    override suspend fun loadItems(
        apiClient: ApiClient,
        page: Int,
        pageCount: Int
    ): Either<String, List<Memory>> {
        if (page == startPage) {
            return apiClient.listMemories()
        }
        return Either.Right(emptyList())
    }

    override fun onItemSelected(card: Card, indexOf: Int) {
        // no use case yet
    }

    override fun onItemClicked(card: Card) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToMemoryAssetsFragment(
                card.id,
                card.title
            )
        )
    }

    override fun getBackgroundPicture(it: Memory): String? {
        return it.assets.firstOrNull()?.let { asset -> ApiUtil.getFileUrl(asset.id, "IMAGE") }
    }

    override fun createCard(a: Memory): Card {
        val title = formatMemoryTitle(a)
        val thumbnailAssetId = a.assets.firstOrNull()?.id
        val description = resources.getQuantityString(R.plurals.memory_photo_count, a.assets.size, a.assets.size)
        return Card(
            title,
            description,
            a.id,
            ApiUtil.getThumbnailUrl(thumbnailAssetId, "thumbnail"),
            ApiUtil.getFileUrl(thumbnailAssetId, "IMAGE"),
            false
        )
    }

    private fun formatMemoryTitle(memory: Memory): String {
        val year = memory.data?.year
        if (year != null) {
            val currentYear = LocalDate.now().year
            val yearsAgo = currentYear - year
            if (yearsAgo > 0) {
                val yearsAgoStr = resources.getQuantityString(R.plurals.memory_years_ago, yearsAgo, yearsAgo)
                return "$year ($yearsAgoStr)"
            }
            return year.toString()
        }
        return memory.memoryAt?.toInstant()
            ?.atZone(ZoneId.systemDefault())
            ?.year
            ?.toString()
            ?: memory.type
    }
}
