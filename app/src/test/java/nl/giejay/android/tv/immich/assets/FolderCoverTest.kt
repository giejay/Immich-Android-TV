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
    fun `up to four photos stand for a folder, photos before videos`() {
        val assets = listOf(asset("v", "VIDEO")) + (1..6).map { asset("p$it", "IMAGE") }

        assertThat(folderCoverAssetIds(assets)).containsExactly("p1", "p2", "p3", "p4").inOrder()
    }

    @Test
    fun `videos fill up when there are few photos, an empty folder has no cover`() {
        assertThat(folderCoverAssetIds(listOf(asset("v1", "VIDEO"), asset("p", "IMAGE"), asset("v2", "VIDEO"))))
            .containsExactly("p", "v1", "v2").inOrder()
        assertThat(folderCoverAssetIds(emptyList())).isEmpty()
    }

    @Test
    fun `a single picture is a plain cover, two or more make a collage`() {
        assertThat(listOf("a").collageOrEmpty()).isEmpty()
        assertThat(listOf("a", "b").collageOrEmpty()).containsExactly("a", "b").inOrder()
        assertThat(emptyList<String>().collageOrEmpty()).isEmpty()
    }
}
