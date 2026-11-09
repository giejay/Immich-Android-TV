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
    fun `the first photo is the cover, even behind a video`() {
        assertThat(folderCoverAssetIds(listOf(asset("v", "VIDEO"), asset("a", "IMAGE"), asset("b", "IMAGE")))).containsExactly("a")
    }

    @Test
    fun `a folder with only videos uses the first video, an empty folder has no cover`() {
        assertThat(folderCoverAssetIds(listOf(asset("v1", "VIDEO"), asset("v2", "VIDEO")))).containsExactly("v1")
        assertThat(folderCoverAssetIds(emptyList())).isEmpty()
    }

    @Test
    fun `a different maximum is honored`() {
        assertThat(folderCoverAssetIds((1..5).map { asset("p$it", "IMAGE") }, max = 3)).hasSize(3)
    }

    @Test
    fun `the badge is the asset count, none for an empty folder`() {
        assertThat(FolderCover("url", 24).badge()).isEqualTo("24")
        assertThat(FolderCover(null, 0).badge()).isNull()
    }
}
