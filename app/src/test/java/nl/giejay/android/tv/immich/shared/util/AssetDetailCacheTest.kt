package nl.giejay.android.tv.immich.shared.util

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import nl.giejay.android.tv.immich.api.model.Asset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AssetDetailCacheTest {

    @After
    fun tearDown() {
        AssetDetailCache.clear()
    }

    @Test
    fun `concurrent get coalesces to a single fetch`() = runBlocking {
        val fetches = AtomicInteger(0)
        val asset = sampleAsset("a1")
        AssetDetailCache.fetchAsset = {
            fetches.incrementAndGet()
            delay(50)
            asset
        }

        val first = async { AssetDetailCache.get("a1") }
        val second = async { AssetDetailCache.get("a1") }

        assertSame(first.await(), second.await())
        assertEquals(1, fetches.get())
    }

    @Test
    fun `second get after completion hits LRU without refetch`() = runBlocking {
        val fetches = AtomicInteger(0)
        AssetDetailCache.fetchAsset = {
            fetches.incrementAndGet()
            sampleAsset(it)
        }

        AssetDetailCache.get("b1")
        AssetDetailCache.get("b1")

        assertEquals(1, fetches.get())
    }

    private fun sampleAsset(id: String) = Asset(
        id = id,
        type = "IMAGE",
        deviceAssetId = null,
        exifInfo = null,
        fileCreatedAt = null,
        fileModifiedAt = null,
        albumName = null,
        people = null,
        tags = null,
        originalPath = null,
        originalFileName = null,
        isFavorite = false
    )
}
