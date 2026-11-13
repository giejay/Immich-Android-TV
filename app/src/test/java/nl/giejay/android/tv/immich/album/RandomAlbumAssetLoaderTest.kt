package nl.giejay.android.tv.immich.album

import arrow.core.Either
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import nl.giejay.android.tv.immich.api.model.Asset
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RandomAlbumAssetLoaderTest {

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

    private fun assets(range: IntRange) = range.map { asset("a$it") }

    /** Fake album: random calls replay the given batches, then keep returning the last one. */
    private class Fake(
        val randomBatches: List<Either<String, List<Asset>>>,
        val sweep: Either<String, List<Asset>> = Either.Right(emptyList()),
        val count: Either<String, Int> = Either.Right(0)
    ) {
        var randomCalls = 0
        var sweepCalls = 0
        var countCalls = 0

        fun loader(batchSize: Int = 150) = RandomAlbumAssetLoader(
            albumAssetCount = { countCalls++; count },
            randomFetch = { _ -> randomBatches[minOf(randomCalls++, randomBatches.size - 1)] },
            sweepFetch = { sweepCalls++; sweep },
            randomBatchSize = batchSize
        )
    }

    @Test
    fun `random batches are deduplicated across requests`() = runTest {
        val fake = Fake(
            randomBatches = listOf(
                Either.Right(assets(1..10)),
                Either.Right(assets(6..15)) // 6..10 are repeats
            ),
            count = Either.Right(1000)
        )
        val loader = fake.loader(batchSize = 10)

        val result = loader.next(15).getOrThrow()

        assertThat(result.map { it.id }).containsNoDuplicates()
        assertThat(result).hasSize(15)
        assertThat(fake.randomCalls).isEqualTo(2)
    }

    @Test
    fun `stops without a sweep once every asset of the album was seen`() = runTest {
        val fake = Fake(
            randomBatches = listOf(Either.Right(assets(1..10))),
            count = Either.Right(10)
        )
        val loader = fake.loader(batchSize = 150)

        val result = loader.next(50).getOrThrow()

        assertThat(result).hasSize(10)
        assertThat(loader.exhausted).isTrue()
        assertThat(fake.sweepCalls).isEqualTo(0)
        assertThat(fake.randomCalls).isEqualTo(1)
    }

    @Test
    fun `empty random result falls back to the sweep`() = runTest {
        val fake = Fake(
            randomBatches = listOf(Either.Right(emptyList())),
            sweep = Either.Right(assets(1..30)),
            count = Either.Right(30)
        )
        val loader = fake.loader()

        val first = loader.next(20).getOrThrow()
        assertThat(first).hasSize(20)
        assertThat(loader.exhausted).isFalse()

        val second = loader.next(20).getOrThrow()
        assertThat(second).hasSize(10)
        assertThat(loader.exhausted).isTrue()
        assertThat((first + second).map { it.id }.toSet()).hasSize(30)
        assertThat(fake.sweepCalls).isEqualTo(1)
    }

    @Test
    fun `sweep only adds assets that random did not return`() = runTest {
        val fake = Fake(
            randomBatches = listOf(
                Either.Right(assets(1..100)),
                Either.Right(assets(1..100)) // all repeats -> switch to sweep
            ),
            sweep = Either.Right(assets(1..120)),
            count = Either.Right(120)
        )
        val loader = fake.loader(batchSize = 100)

        val all = mutableListOf<Asset>()
        while (!loader.exhausted) {
            all += loader.next(50).getOrThrow()
        }

        assertThat(all.map { it.id }).containsNoDuplicates()
        assertThat(all).hasSize(120)
        assertThat(fake.sweepCalls).isEqualTo(1)
    }

    @Test
    fun `batch with too few new assets switches to the sweep`() = runTest {
        // 100 per batch, only 5 new in the third one (< 10%)
        val fake = Fake(
            randomBatches = listOf(
                Either.Right(assets(1..100)),
                Either.Right(assets(96..195)), // 96..100 seen, 101..195 new = 95 new -> keep going
                Either.Right(assets(1..95) + assets(196..200)) // only 5 new -> switch to sweep
            ),
            sweep = Either.Right(assets(1..210)),
            count = Either.Right(210)
        )
        val loader = fake.loader(batchSize = 100)

        val all = mutableListOf<Asset>()
        while (!loader.exhausted) {
            all += loader.next(50).getOrThrow()
        }

        assertThat(all).hasSize(210)
        assertThat(all.map { it.id }).containsNoDuplicates()
        assertThat(fake.randomCalls).isEqualTo(3)
        assertThat(fake.sweepCalls).isEqualTo(1)
    }

    @Test
    fun `random failure falls back to the sweep`() = runTest {
        val fake = Fake(
            randomBatches = listOf(Either.Left("boom")),
            sweep = Either.Right(assets(1..5)),
            count = Either.Right(5)
        )
        val loader = fake.loader()

        assertThat(loader.next(50).getOrThrow()).hasSize(5)
        assertThat(loader.exhausted).isTrue()
    }

    @Test
    fun `unknown album count does not break loading`() = runTest {
        val fake = Fake(
            randomBatches = listOf(Either.Right(assets(1..10)), Either.Right(assets(1..10))),
            sweep = Either.Right(assets(1..10)),
            count = Either.Left("no count")
        )
        val loader = fake.loader(batchSize = 10)

        assertThat(loader.next(50).getOrThrow()).hasSize(10)
        assertThat(loader.exhausted).isTrue()
    }

    @Test
    fun `failed sweep returns the error and is retried on the next call`() = runTest {
        var sweepAttempts = 0
        val loader = RandomAlbumAssetLoader(
            albumAssetCount = { Either.Right(3) },
            randomFetch = { Either.Right(emptyList()) },
            sweepFetch = {
                sweepAttempts++
                if (sweepAttempts == 1) Either.Left("network") else Either.Right(assets(1..3))
            }
        )

        assertThat(loader.next(50)).isEqualTo(Either.Left("network"))
        assertThat(loader.exhausted).isFalse()

        assertThat(loader.next(50).getOrThrow()).hasSize(3)
        assertThat(loader.exhausted).isTrue()
    }

    @Test
    fun `lazy - does not request more random batches than needed`() = runTest {
        val fake = Fake(
            randomBatches = listOf(Either.Right(assets(1..150)), Either.Right(assets(151..300))),
            count = Either.Right(5000)
        )
        val loader = fake.loader(batchSize = 150)

        assertThat(loader.next(50).getOrThrow()).hasSize(50)
        assertThat(fake.randomCalls).isEqualTo(1)
        assertThat(loader.next(50).getOrThrow()).hasSize(50)
        assertThat(loader.next(50).getOrThrow()).hasSize(50)
        assertThat(fake.randomCalls).isEqualTo(1) // 150 buffered served three pages
        assertThat(loader.exhausted).isFalse()
    }

    private fun <T> Either<String, T>.getOrThrow(): T = fold({ error("unexpected Left: $it") }, { it })
}
