package nl.giejay.android.tv.immich.assets

import com.google.common.truth.Truth.assertThat
import nl.giejay.android.tv.immich.api.model.Asset
import org.junit.Test

class FolderCoverTest {
    private fun asset(id: String, type: String) = Asset(
        id = id, type = type, deviceAssetId = null, exifInfo = null, fileModifiedAt = null,
        albumName = null, people = null, tags = null, originalPath = null, originalFileName = null,
    )

    @Test
    fun `two photos peek out of a folder, photos before videos`() {
        val assets = listOf(asset("v", "VIDEO")) + (1..6).map { asset("p$it", "IMAGE") }

        assertThat(folderCoverAssetIds(assets)).containsExactly("p1", "p2").inOrder()
    }

    @Test
    fun `videos fill up when there are few photos, an empty folder has no photos`() {
        assertThat(folderCoverAssetIds(listOf(asset("v1", "VIDEO"), asset("p", "IMAGE"), asset("v2", "VIDEO"))))
            .containsExactly("p", "v1").inOrder()
        assertThat(folderCoverAssetIds(emptyList())).isEmpty()
    }

    @Test
    fun `a different maximum is honored`() {
        assertThat(folderCoverAssetIds((1..5).map { asset("p$it", "IMAGE") }, max = 3)).hasSize(3)
    }
}
