package nl.giejay.android.tv.immich.card

interface ICard {
    val title: String
    val description: String?
    val id: String
    val thumbnailUrl: String?
    val backgroundUrl: String?
    val selected: Boolean
    /** Two to four picture urls shown as a small grid instead of [thumbnailUrl] (a folder's photos); empty = not a collage. */
    val collageUrls: List<String> get() = emptyList()
}