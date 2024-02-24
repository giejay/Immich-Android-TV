package nl.giejay.android.tv.immich.shared.prefs

import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.shared.util.Utils.compareToNullSafe

enum class AlbumsOrder(val sort: Comparator<Album>) {
    ALPHABETICALLY_A_Z({ a1, a2 -> a1.albumName.compareToNullSafe(a2.albumName) }),
    ALPHABETICALLY_Z_A({ a2, a1 -> a1.albumName.compareToNullSafe(a2.albumName) }),
    LAST_UPDATED(Sort@{ album, album2 ->
        // just testing with labeled returns here...
        return@Sort album2.endDate?.compareToNullSafe(album.endDate) ?: -1
    }),
    LEAST_UPDATED({ album2, album ->
        album2.endDate?.compareToNullSafe(album.endDate) ?: -1
    });

    companion object {
        fun valueOfSafe(name: String, default: AlbumsOrder): AlbumsOrder{
            return values().find { it.toString() == name } ?: default
        }
    }
}