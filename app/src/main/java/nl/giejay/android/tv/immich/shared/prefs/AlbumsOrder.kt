package nl.giejay.android.tv.immich.shared.prefs

import nl.giejay.android.tv.immich.api.model.Album

enum class AlbumsOrder(val sort: Comparator<Album>) {
    ALPHABETICALLY_A_Z(compareBy{it.albumName}),
    ALPHABETICALLY_Z_A(ALPHABETICALLY_A_Z.sort.reversed()),
    LAST_UPDATED(compareByDescending{it.endDate}),
    LEAST_UPDATED(LAST_UPDATED.sort.reversed());

    companion object {
        fun valueOfSafe(name: String, default: AlbumsOrder): AlbumsOrder{
            return entries.find { it.toString() == name } ?: default
        }
    }
}