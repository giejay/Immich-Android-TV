package nl.giejay.android.tv.immich.screensaver

import com.google.common.truth.Truth.assertThat
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.Tag
import org.junit.Test
import java.util.Date

/**
 * Covers the asset filtering shared by the DreamService and the in-app screensaver preview.
 * Pure JVM on purpose: no Android, no PreferenceManager, no Robolectric.
 */
class ScreenSaverAssetFilterTest {

    private fun tag(name: String) = Tag(null, Date(0), name, name)

    private fun asset(id: String, tags: List<Tag>?) = Asset(
        id = id,
        type = "IMAGE",
        deviceAssetId = null,
        exifInfo = null,
        fileModifiedAt = null,
        albumName = null,
        people = null,
        tags = tags,
        originalPath = null,
        originalFileName = null
    )

    @Test
    fun `asset tagged exclude_immich_tv is excluded`() {
        val tagged = asset("a", listOf(tag("exclude_immich_tv")))

        assertThat(filterExcludedAssets(listOf(tagged), emptySet())).isEmpty()
    }

    @Test
    fun `asset with null tags is kept`() {
        val untagged = asset("a", null)

        assertThat(filterExcludedAssets(listOf(untagged), emptySet())).containsExactly(untagged)
    }

    @Test
    fun `asset with empty tag list is kept`() {
        val untagged = asset("a", emptyList())

        assertThat(filterExcludedAssets(listOf(untagged), emptySet())).containsExactly(untagged)
    }

    @Test
    fun `asset with unrelated tags is kept`() {
        val holiday = asset("a", listOf(tag("holiday"), tag("2024")))

        assertThat(filterExcludedAssets(listOf(holiday), emptySet())).containsExactly(holiday)
    }

    @Test
    fun `asset in an excluded album is excluded`() {
        val kept = asset("keep", null)
        val excluded = asset("drop", null)

        assertThat(filterExcludedAssets(listOf(kept, excluded), setOf("drop")))
            .containsExactly(kept)
    }

    @Test
    fun `asset excluded by both rules is dropped once and survivor order is preserved`() {
        val first = asset("1", null)
        val both = asset("2", listOf(tag("exclude_immich_tv")))
        val second = asset("3", emptyList())

        val result = filterExcludedAssets(listOf(first, both, second), setOf("2"))

        assertThat(result).containsExactly(first, second).inOrder()
    }
}
