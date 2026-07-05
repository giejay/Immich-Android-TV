package nl.giejay.android.tv.immich.screensaver

import arrow.core.Either
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class ScreenSaverServiceLoggingTest {

    private val loggedMessages = mutableListOf<String>()

    @Before
    fun plantTestTree() {
        loggedMessages.clear()
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                loggedMessages.add(message)
            }
        })
    }

    @After
    fun uprootTestTree() {
        Timber.uprootAll()
    }

    @Test
    fun `Right passthrough returns value unchanged and logs nothing`() {
        val result: Either<String, List<String>> = Either.Right(listOf("a"))

        val actual = result.getOrElseLogged("ctx", emptyList())

        assertEquals(listOf("a"), actual)
        assertTrue(loggedMessages.isEmpty())
    }

    @Test
    fun `Left returns default and logs exactly one message containing the error and context`() {
        val result: Either<String, List<String>> = Either.Left("boom")

        val actual = result.getOrElseLogged("loadNextBuckets for album abc123", emptyList())

        assertEquals(emptyList<String>(), actual)
        assertEquals(1, loggedMessages.size)
        assertTrue(loggedMessages[0].contains("boom"))
        assertTrue(loggedMessages[0].contains("loadNextBuckets for album abc123"))
    }
}
