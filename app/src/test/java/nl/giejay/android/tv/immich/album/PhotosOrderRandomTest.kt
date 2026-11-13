package nl.giejay.android.tv.immich.album

import com.google.common.truth.Truth.assertThat
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder
import org.junit.Test
import java.util.Date

class PhotosOrderRandomTest {
    private fun asset(id: String, modified: Long) = Asset(
        id = id, type = "IMAGE", deviceAssetId = null, exifInfo = null, fileModifiedAt = Date(modified),
        albumName = null, people = null, tags = null, originalPath = null, originalFileName = null
    )

    @Test
    fun `random sort keeps arrival order`() {
        val items = listOf(asset("c", 3), asset("a", 1), asset("b", 2))
        assertThat(items.sortedWith(PhotosOrder.RANDOM.sort).map { it.id }).containsExactly("c", "a", "b").inOrder()
    }

    @Test
    fun `random can be restored from its stored name`() {
        assertThat(PhotosOrder.valueOfSafe("RANDOM", PhotosOrder.OLDEST_NEWEST)).isEqualTo(PhotosOrder.RANDOM)
    }
}
