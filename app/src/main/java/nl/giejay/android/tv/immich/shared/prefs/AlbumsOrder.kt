package nl.giejay.android.tv.immich.shared.prefs

import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.shared.util.Utils.compareToNullSafe

enum class AlbumsOrder(val sort: Comparator<Album>, val title: String) {
    ALPHABETICALLY({ a1, a2 -> a1.albumName.compareToNullSafe(a2.albumName) }, "Alphabetically"),
    LAST_UPDATED(Sort@{ album, album2 ->
        // just testing with labeled returns here...
        return@Sort album.endDate?.compareToNullSafe(album2.endDate) ?: 1
    }, "Last Updated");
}