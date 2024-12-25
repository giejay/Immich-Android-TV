package nl.giejay.android.tv.immich.assets

import androidx.navigation.fragment.findNavController
import arrow.core.Either
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Folder
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment

class FolderFragment : VerticalCardGridFragment<Folder>() {
    private var rootFolder: Folder? = null
    private var stack = mutableListOf<Folder>()

    override fun sortItems(items: List<Folder>): List<Folder> {
        return items.sortedBy { it.path }
    }

    override suspend fun loadItems(
        apiClient: ApiClient,
        page: Int,
        pageCount: Int
    ): Either<String, List<Folder>> {
        if (rootFolder == null) {
            val listFolders = apiClient.listFolders()
            return listFolders.map {
                rootFolder = it
                it.children
            }
        }
        return Either.Right(emptyList())
    }

    override fun onItemSelected(card: Card, indexOf: Int) {
        // no use case yet
    }

    override fun onItemClicked(card: Card) {
        if (card.title == "..") {
            updateState(stack.removeAt(stack.size - 1))
        } else {
            val clickedChild = rootFolder!!.children.find { it.path == card.title }
            if (clickedChild?.children?.isEmpty() == true) {
                findNavController().navigate(
                    HomeFragmentDirections.actionFolderAssetsFragment(clickedChild.getFullPath())
                )
            } else {
                stack.add(rootFolder!!)
                updateState(clickedChild)
            }
        }
    }

    private fun updateState(clickedChild: Folder?) {
        rootFolder = clickedChild
        clearState()
        setData((if (stack.isNotEmpty()) listOf(Folder("..", mutableListOf(), null)) else emptyList()) + rootFolder!!.children)
        updateManualPositionHandler(1)
    }

    override fun getBackgroundPicture(it: Folder): String? {
        return null
    }

    override fun createCard(a: Folder): Card {
        return Card(
            a.path,
            "",
            a.path,
            null,
            null,
            false
        )
    }
}