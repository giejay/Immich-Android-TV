package nl.giejay.mediaslider.util

import kotlin.random.Random

/**
 * Random page order for a shuffled slideshow: every index is shown once per pass,
 * then a freshly shuffled pass starts. Indices that appear while a pass is running
 * (items loaded later) are mixed into the rest of that pass.
 */
class ShuffleOrder(private val random: Random = Random.Default) {
    private val upcoming = ArrayDeque<Int>()
    private val history = ArrayDeque<Int>()
    private var knownCount = 0

    /** Returns the index to show after [current], or null if there is nothing else to show. */
    fun next(current: Int, count: Int): Int? {
        peek(current, count) ?: return null
        history.addLast(current)
        if (history.size > MAX_HISTORY) history.removeFirst()
        return upcoming.removeFirst()
    }

    /** Returns what [next] will return for the same arguments, without moving on. */
    fun peek(current: Int, count: Int): Int? {
        if (count <= 1) return null
        sync(count)
        // The current page counts as shown, also when it was reached by manual navigation.
        upcoming.remove(current)
        if (upcoming.isEmpty()) {
            upcoming.addAll((0 until count).shuffled(random))
            // A new pass contains every index, but must not open with the one on screen.
            if (upcoming.first() == current) {
                val swapWith = 1 + random.nextInt(count - 1)
                upcoming[0] = upcoming[swapWith]
                upcoming[swapWith] = current
            }
        }
        return upcoming.first()
    }

    /** Returns the index shown before [current], or null if there is no shuffle history. */
    fun previous(current: Int, count: Int): Int? {
        sync(count)
        val target = history.removeLastOrNull() ?: return null
        upcoming.remove(current)
        upcoming.addFirst(current)
        return target
    }

    /** Forgets the current pass and history. */
    fun reset() {
        upcoming.clear()
        history.clear()
        knownCount = 0
    }

    private fun sync(count: Int) {
        if (count < knownCount) {
            upcoming.removeAll { it >= count }
            history.removeAll { it >= count }
        }
        if (count > knownCount) {
            upcoming.addAll(knownCount until count)
            upcoming.shuffle(random)
        }
        knownCount = count
    }

    private companion object {
        const val MAX_HISTORY = 500
    }
}
