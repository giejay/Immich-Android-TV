package nl.giejay.android.tv.immich.shared.prefs

import com.google.common.truth.Truth.assertThat
import nl.giejay.android.tv.immich.api.model.Album
import org.junit.Test
import java.util.Date

class AlbumsOrderTest {
    private fun album(name: String, updated: Long = 0, end: Long? = null, created: Long? = null, count: Int = 0) =
        Album(name, "", name, null, Date(updated), end?.let { Date(it) }, count, createdAt = created?.let { Date(it) })

    private val a = album("beta", updated = 3, end = 10, created = 1, count = 5)
    private val b = album("Alpha", updated = 1, end = 30, created = 3, count = 9)
    private val c = album("gamma", updated = 2, end = null, created = null, count = 1)
    private val all = listOf(a, b, c)

    private fun names(o: AlbumsOrder) = all.sortedWith(o.sort).map { it.albumName }

    @Test fun alphabeticalIgnoresCase() {
        assertThat(names(AlbumsOrder.ALPHABETICALLY_A_Z)).containsExactly("Alpha", "beta", "gamma").inOrder()
        assertThat(names(AlbumsOrder.ALPHABETICALLY_Z_A)).containsExactly("gamma", "beta", "Alpha").inOrder()
    }

    @Test fun photoDatesKeepAlbumsWithoutDateLast() {
        assertThat(names(AlbumsOrder.NEWEST_PHOTOS)).containsExactly("Alpha", "beta", "gamma").inOrder()
        assertThat(names(AlbumsOrder.OLDEST_PHOTOS)).containsExactly("beta", "Alpha", "gamma").inOrder()
    }

    @Test fun modifiedAndCreated() {
        assertThat(names(AlbumsOrder.RECENTLY_MODIFIED)).containsExactly("beta", "gamma", "Alpha").inOrder()
        assertThat(names(AlbumsOrder.RECENTLY_CREATED)).containsExactly("Alpha", "beta", "gamma").inOrder()
    }

    @Test fun itemCount() {
        assertThat(names(AlbumsOrder.MOST_ITEMS)).containsExactly("Alpha", "beta", "gamma").inOrder()
        assertThat(names(AlbumsOrder.FEWEST_ITEMS)).containsExactly("gamma", "beta", "Alpha").inOrder()
    }

    @Test fun legacyValuesAreMapped() {
        assertThat(AlbumsOrder.valueOfSafe("LAST_UPDATED", AlbumsOrder.MOST_ITEMS)).isEqualTo(AlbumsOrder.NEWEST_PHOTOS)
        assertThat(AlbumsOrder.valueOfSafe("LEAST_UPDATED", AlbumsOrder.MOST_ITEMS)).isEqualTo(AlbumsOrder.OLDEST_PHOTOS)
        assertThat(AlbumsOrder.valueOfSafe("ASSET_COUNT", AlbumsOrder.NEWEST_PHOTOS)).isEqualTo(AlbumsOrder.MOST_ITEMS)
        assertThat(AlbumsOrder.valueOfSafe("???", AlbumsOrder.FEWEST_ITEMS)).isEqualTo(AlbumsOrder.FEWEST_ITEMS)
    }
}
