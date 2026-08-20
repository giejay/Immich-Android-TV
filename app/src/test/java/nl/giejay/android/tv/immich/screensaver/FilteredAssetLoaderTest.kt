package nl.giejay.android.tv.immich.screensaver

import arrow.core.Either
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.AssetResponse
import nl.giejay.android.tv.immich.api.model.Tag
import org.junit.Test
import java.util.Date

class FilteredAssetLoaderTest {
    @Test
    fun `backoff returns the configured delay sequence capped at one hour`() {
        val backoff = EmptyFilteredBatchBackoff()
        val delays = buildList {
            repeat(15) {
                add(backoff.delayBeforeNextRequestMillis())
                backoff.recordEmptyBatch()
            }
        }

        assertThat(delays).containsExactly(
            0L,
            1_000L,
            2_000L,
            4_000L,
            8_000L,
            16_000L,
            32_000L,
            60_000L,
            120_000L,
            240_000L,
            480_000L,
            960_000L,
            1_920_000L,
            3_600_000L,
            3_600_000L
        ).inOrder()
    }

    @Test
    fun `load retries empty filtered batches until filtering returns an asset`() = runTest {
        val waits = mutableListOf<Long>()
        val responses = ArrayDeque(listOf(
            AssetResponse(listOf(asset("excluded-1")), true),
            AssetResponse(listOf(asset("excluded-2")), true),
            AssetResponse(listOf(asset("included")), true)
        ))
        val loader = FilteredAssetLoader(
            filterAssets = { assets -> assets.filter { it.id == "included" } },
            wait = { waits.add(it) }
        )

        val result = loader.load({ Either.Right(responses.removeFirst()) })

        assertThat(result.getOrNull()!!.assets.map { it.id }).containsExactly("included")
        assertThat(waits).containsExactly(1_000L, 2_000L).inOrder()
    }

    @Test
    fun `load returns an empty result when its source cannot load more`() = runTest {
        var fetchCount = 0
        val waits = mutableListOf<Long>()
        val loader = FilteredAssetLoader(
            filterAssets = { emptyList() },
            wait = { waits.add(it) }
        )

        val result = loader.load ({
            fetchCount += 1
            Either.Right(AssetResponse(listOf(asset("excluded")), false))
        })

        assertThat(result.getOrNull()!!.assets).isEmpty()
        assertThat(result.getOrNull()!!.canLoadMore).isFalse()
        assertThat(fetchCount).isEqualTo(1)
        assertThat(waits).isEmpty()
    }

    @Test
    fun `non-empty filtered result resets the delay before the next load`() = runTest {
        val waits = mutableListOf<Long>()
        val loader = FilteredAssetLoader(
            filterAssets = { assets -> assets.filter { it.id == "included" } },
            wait = { waits.add(it) }
        )

        suspend fun loadAfterOneExcludedBatch() {
            var firstRequest = true
            loader.load ({
                if (firstRequest) {
                    firstRequest = false
                    Either.Right(AssetResponse(listOf(asset("excluded")), true))
                } else {
                    Either.Right(AssetResponse(listOf(asset("included")), true))
                }
            })
        }

        loadAfterOneExcludedBatch()
        loadAfterOneExcludedBatch()

        assertThat(waits).containsExactly(1_000L, 1_000L).inOrder()
    }

    @Test
    fun `load returns an API failure without waiting`() = runTest {
        val waits = mutableListOf<Long>()
        val loader = FilteredAssetLoader(
            filterAssets = { it },
            wait = { waits.add(it) }
        )

        val result = loader.load ({ Either.Left("request failed") })

        assertThat(result.leftOrNull()).isEqualTo("request failed")
        assertThat(waits).isEmpty()
    }

    @Test
    fun `filter removes assets with the exclusion tag or an excluded album ID`() {
        val included = asset("included")
        val tagged = asset("tagged").copy(
            tags = listOf(Tag(null, Date(0), "exclude_immich_tv", ""))
        )
        val inExcludedAlbum = asset("in-excluded-album")

        val result = filterExcludedAssets(
            listOf(tagged, inExcludedAlbum, included),
            setOf(inExcludedAlbum.id)
        )

        assertThat(result).containsExactly(included)
    }

    private fun asset(id: String) = Asset(
        id = id,
        type = "IMAGE",
        deviceAssetId = null,
        exifInfo = null,
        fileModifiedAt = null,
        albumName = null,
        people = null,
        tags = null,
        originalPath = null,
        originalFileName = null
    )
}
