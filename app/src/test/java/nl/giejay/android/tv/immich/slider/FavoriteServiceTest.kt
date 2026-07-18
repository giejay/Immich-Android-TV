package nl.giejay.android.tv.immich.slider

import arrow.core.Either
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.coroutines.CoroutinesTestRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteServiceTest {

    @get:Rule
    val coroutinesRule = CoroutinesTestRule()

    @Test
    fun `toggleFavorite success logs info and shows added toast when favorite is enabled`() = runBlocking {
        val toasts = mutableListOf<Int>()
        val infoLogs = mutableListOf<String>()
        val errorLogs = mutableListOf<String>()
        val service = FavoriteService(
            updateFavorite = { _, _ -> Either.Right(Unit) },
            showToast = { toasts.add(it) },
            logInfo = { infoLogs.add(it) },
            logError = { errorLogs.add(it) }
        )

        service.toggleFavorite("asset-1", true)

        assertEquals(listOf(R.string.favorite_added), toasts)
        assertEquals(1, infoLogs.size)
        assertEquals(0, errorLogs.size)
    }

    @Test
    fun `toggleFavorite failure logs error and shows remove failed toast when unfavorite fails`() = runBlocking {
        val toasts = mutableListOf<Int>()
        val infoLogs = mutableListOf<String>()
        val errorLogs = mutableListOf<String>()
        val service = FavoriteService(
            updateFavorite = { _, _ -> Either.Left("boom") },
            showToast = { toasts.add(it) },
            logInfo = { infoLogs.add(it) },
            logError = { errorLogs.add(it) }
        )

        service.toggleFavorite("asset-1", false)

        assertEquals(listOf(R.string.favorite_remove_failed), toasts)
        assertEquals(0, infoLogs.size)
        assertEquals(1, errorLogs.size)
    }
}
