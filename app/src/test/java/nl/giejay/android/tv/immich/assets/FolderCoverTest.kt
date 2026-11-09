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
        assertThat(folderCoverAssetId(listOf(asset("v", "VIDEO"), asset("a", "IMAGE"), asset("b", "IMAGE")))).isEqualTo("a")
    }

    @Test
    fun `a folder with only videos uses the first video, an empty one has no cover`() {
        assertThat(folderCoverAssetId(listOf(asset("v1", "VIDEO"), asset("v2", "VIDEO")))).isEqualTo("v1")
        assertThat(folderCoverAssetId(emptyList())).isNull()
    }
}
