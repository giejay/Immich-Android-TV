package nl.giejay.android.tv.immich.shared.prefs

import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.shared.util.Utils.compareToNullSafe

enum class AlbumsOrder(val sort: Comparator<Album>) {
    ALPHABETICALLY_A_Z({ a1, a2 -> a1.albumName.compareToNullSafe(a2.albumName) }),
    ALPHABETICALLY_Z_A({ a1, a2 -> ALPHABETICALLY_A_Z.sort.reversed().compare(a1, a2) }),
    LAST_UPDATED(Sort@{ album, album2 ->
        // just testing with labeled returns here...
        if(album2.endDate == null && album.endDate == null){
            return@Sort 0;
        } else if(album2.endDate == null){
            return@Sort 1
        }
        return@Sort album2.endDate.compareToNullSafe(album.endDate);
    }),
    LEAST_UPDATED({ album, album2 ->
        LAST_UPDATED.sort.reversed().compare(album, album2)
    });

    companion object {
        fun valueOfSafe(name: String, default: AlbumsOrder): AlbumsOrder{
            return entries.find { it.toString() == name } ?: default
        }
    }
}