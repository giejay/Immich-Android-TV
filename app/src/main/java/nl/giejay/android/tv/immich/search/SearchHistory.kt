package nl.giejay.android.tv.immich.search

/** The last searches, newest first, stored as one preference string (one query per line). */
object SearchHistory {
    const val MAX_ENTRIES = 8

    fun parse(stored: String): List<String> =
        stored.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_ENTRIES)

    fun serialize(entries: List<String>): String = entries.joinToString("\n")

    /** [query] moves to the front; the same text in another case does not appear twice. */
    fun add(entries: List<String>, query: String): List<String> {
        val cleaned = query.trim().replace('\n', ' ')
        if (cleaned.isEmpty()) return entries
        return (listOf(cleaned) + entries.filterNot { it.equals(cleaned, ignoreCase = true) }).take(MAX_ENTRIES)
    }
}
