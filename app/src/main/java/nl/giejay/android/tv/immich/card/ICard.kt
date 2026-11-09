package nl.giejay.android.tv.immich.card

interface ICard {
    val title: String
    val description: String?
    val id: String
    val thumbnailUrl: String?
    val backgroundUrl: String?
    val selected: Boolean
    /** A folder card is drawn as a folder; [peekUrls] are the photos that peek out of it. */
    val isFolder: Boolean get() = false
    val peekUrls: List<String> get() = emptyList()
}