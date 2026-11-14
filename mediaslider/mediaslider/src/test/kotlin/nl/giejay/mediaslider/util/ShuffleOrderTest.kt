package nl.giejay.mediaslider.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class ShuffleOrderTest {

    private fun play(order: ShuffleOrder, start: Int, count: Int, steps: Int): List<Int> {
        val shown = mutableListOf(start)
        repeat(steps) { shown.add(order.next(shown.last(), count)!!) }
        return shown
    }

    @Test
    fun everyPassShowsEachIndexExactlyOnce() {
        val count = 7
        val shown = play(ShuffleOrder(Random(1)), start = 3, count = count, steps = count * 5 - 1)
        shown.chunked(count).forEach { pass -> assertEquals((0 until count).toSet(), pass.toSet()) }
    }

    @Test
    fun neverRepeatsBackToBack() {
        val shown = play(ShuffleOrder(Random(2)), start = 0, count = 5, steps = 200)
        shown.zipWithNext().forEach { (a, b) -> assertNotEquals(a, b) }
    }

    @Test
    fun itemsLoadedLaterJoinTheCurrentPass() {
        val order = ShuffleOrder(Random(3))
        val shown = mutableListOf(0)
        repeat(2) { shown.add(order.next(shown.last(), 5)!!) }
        repeat(7) { shown.add(order.next(shown.last(), 10)!!) }
        assertEquals((0 until 10).toSet(), shown.toSet())
    }

    @Test
    fun previousWalksBackThroughHistory() {
        val order = ShuffleOrder(Random(4))
        val shown = play(order, start = 2, count = 6, steps = 3)
        assertEquals(shown[2], order.previous(shown[3], 6))
        assertEquals(shown[1], order.previous(shown[2], 6))
        assertEquals(shown[2], order.next(shown[1], 6))
        assertEquals(shown[3], order.next(shown[2], 6))
    }

    @Test
    fun previousFromManuallyReachedPageDoesNotShowItTwice() {
        val count = 6
        val order = ShuffleOrder(Random(5))
        val (first, second) = play(order, start = 0, count = count, steps = 1)
        val manual = (0 until count).first { it != first && it != second }
        assertEquals(first, order.previous(manual, count))
        val restOfPass = mutableListOf(first)
        repeat(count - 2) { restOfPass.add(order.next(restOfPass.last(), count)!!) }
        val upcoming = restOfPass.drop(1)
        assertEquals(manual, upcoming.first())
        assertEquals(upcoming.size, upcoming.toSet().size)
        assertEquals(((0 until count).toSet() - first - second), upcoming.toSet())
    }

    @Test
    fun peekAnnouncesWhatNextReturns() {
        val order = ShuffleOrder(Random(6))
        var current = 0
        repeat(30) {
            val peeked = order.peek(current, 4)
            assertEquals(peeked, order.peek(current, 4))
            current = order.next(current, 4)!!
            assertEquals(peeked, current)
        }
    }

    @Test
    fun singleItemHasNothingToShuffle() {
        assertNull(ShuffleOrder().next(0, 1))
        assertNull(ShuffleOrder().previous(0, 1))
    }
}
