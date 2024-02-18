package nl.giejay.android.tv.immich.shared.prefs

import nl.giejay.android.tv.immich.api.model.Album

enum class AlbumsOrder(val sort: Comparator<Album>, val title: String) {
    ALPHABETICALLY({ a1, a2 -> a1.albumName.compareTo(a2.albumName) }, "Alphabetically"),
    LAST_UPDATED({ album, album2 -> album.endDate.compareTo(album2.endDate) }, "Last Updated");
}