package nl.giejay.android.tv.immich.search

import com.google.common.truth.Truth.assertThat
import nl.giejay.android.tv.immich.api.buildMetadataSearchRequests
import nl.giejay.android.tv.immich.api.filterAlbumsByName
import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.mergeSearchResults
import nl.giejay.android.tv.immich.api.sliceSearchPage
import nl.giejay.android.tv.immich.api.model.Asset
import org.junit.Test
import java.util.Date

class SearchTest {
    private fun asset(id: String, created: Long?) = Asset(
        id = id, type = "IMAGE", deviceAssetId = null, exifInfo = null,
        fileCreatedAt = created?.let { Date(it) }, fileModifiedAt = null, albumName = null,
        people = null, tags = null, originalPath = null, originalFileName = null,
    )

    @Test
    fun `without machine learning the text is looked up in four fields`() {
        val requests = buildMetadataSearchRequests("beach", page = 2, size = 50, type = "VIDEO")
        assertThat(requests).hasSize(4)
        assertThat(requests.map { it.originalFileName }).containsExactly("beach", null, null, null).inOrder()
        assertThat(requests.map { it.description }).containsExactly(null, "beach", null, null).inOrder()
        assertThat(requests.map { it.city }).containsExactly(null, null, "beach", null).inOrder()
        assertThat(requests.map { it.country }).containsExactly(null, null, null, "beach").inOrder()
        requests.forEach {
            assertThat(it.page).isEqualTo(2)
            assertThat(it.size).isEqualTo(50)
            assertThat(it.type).isEqualTo("VIDEO")
            assertThat(it.visibility).isEqualTo("timeline")
        }
    }

    @Test
    fun `merged results hold every asset once, newest first`() {
        val merged = mergeSearchResults(
            listOf(
                listOf(asset("a", 100), asset("b", 300)),
                listOf(asset("b", 300), asset("c", 200)),
            ),
        )
        assertThat(merged.map { it.id }).containsExactly("b", "c", "a").inOrder()
    }

    @Test
    fun `the search history keeps the newest first without duplicates and stays short`() {
        var history = emptyList<String>()
        history = SearchHistory.add(history, " Strand ")
        history = SearchHistory.add(history, "Berge")
        history = SearchHistory.add(history, "strand")
        assertThat(history).containsExactly("strand", "Berge").inOrder()
        repeat(20) { history = SearchHistory.add(history, "q$it") }
        assertThat(history).hasSize(SearchHistory.MAX_ENTRIES)
        assertThat(history.first()).isEqualTo("q19")
    }

    @Test
    fun `blank searches are not stored and the history survives a round trip`() {
        assertThat(SearchHistory.add(listOf("a"), "   ")).containsExactly("a")
        val stored = SearchHistory.serialize(listOf("Strand", "Berge am See"))
        assertThat(SearchHistory.parse(stored)).containsExactly("Strand", "Berge am See").inOrder()
        assertThat(SearchHistory.parse("")).isEmpty()
    }

    @Test
    fun `the merged list is cut into pages and says whether more follow`() {
        val all = (1..7).map { asset("a$it", it.toLong()) }
        val first = sliceSearchPage(all, page = 1, size = 3)
        assertThat(first.assets.map { it.id }).containsExactly("a1", "a2", "a3").inOrder()
        assertThat(first.hasMore).isTrue()
        val last = sliceSearchPage(all, page = 3, size = 3)
        assertThat(last.assets.map { it.id }).containsExactly("a7")
        assertThat(last.hasMore).isFalse()
        assertThat(sliceSearchPage(all, page = 4, size = 3).assets).isEmpty()
    }

    @Test
    fun `metadata requests carry the album scope`() {
        val requests = buildMetadataSearchRequests("beach", 1, 50, null, albumIds = listOf("alb"))
        requests.forEach { assertThat(it.albumIds).containsExactly("alb") }
        buildMetadataSearchRequests("beach", 1, 50, null).forEach { assertThat(it.albumIds).isEmpty() }
    }

    private fun album(name: String) = Album(name, "", name, null, Date(0), null, 0)

    @Test
    fun `album names match by part and ignore case, blank matches none`() {
        val all = listOf(album("Zoo 2024"), album("beach trip"), album("Beach party"), album("Food"))
        assertThat(filterAlbumsByName(all, " BEACH ").map { it.albumName }).containsExactly("Beach party", "beach trip").inOrder()
        assertThat(filterAlbumsByName(all, "  ")).isEmpty()
        assertThat(filterAlbumsByName(all, "xyz")).isEmpty()
    }
}
