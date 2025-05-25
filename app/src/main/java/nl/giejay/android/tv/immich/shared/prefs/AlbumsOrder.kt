package nl.giejay.android.tv.immich.shared.prefs

import nl.giejay.android.tv.immich.api.model.Album

enum class AlbumsOrder(val sort: Comparator<Album>) : EnumWithTitle {
    ALPHABETICALLY_A_Z(compareBy { it.albumName }) {
        override fun getTitle(): String {
            return "Alphabetically (A-Z)";
        }
    },
    ALPHABETICALLY_Z_A(ALPHABETICALLY_A_Z.sort.reversed()) {
        override fun getTitle(): String {
            return "Alphabetically (Z-A)"
        }
    },
    LAST_UPDATED(compareByDescending { it.endDate }) {
        override fun getTitle(): String {
            return "Last updated"
        }
    },
    LEAST_UPDATED(LAST_UPDATED.sort.reversed()) {
        override fun getTitle(): String {
            return "Least updated"
        }
    },
    ASSET_COUNT(compareBy<Album> { it.assetCount }.reversed()) {
        override fun getTitle(): String {
            return "Asset count"
        }
    };

    companion object {
        fun valueOfSafe(name: String, default: AlbumsOrder): AlbumsOrder {
            return entries.find { it.toString() == name } ?: default
        }
    }
}