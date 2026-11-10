package nl.giejay.android.tv.immich.shared.prefs

import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.model.Album
import java.util.Date

private fun <T> byDate(newestFirst: Boolean, date: (T) -> Date?): Comparator<T> {
    // Albums without the date always go last, whatever the direction.
    val base: Comparator<Date> = if (newestFirst) compareByDescending { it } else compareBy { it }
    return Comparator { a, b ->
        val da = date(a)
        val db = date(b)
        when {
            da == null && db == null -> 0
            da == null -> 1
            db == null -> -1
            else -> base.compare(da, db)
        }
    }
}

enum class AlbumsOrder(val sort: Comparator<Album>, private val titleRes: Int) : EnumWithTitle {
    ALPHABETICALLY_A_Z(
        compareBy(String.CASE_INSENSITIVE_ORDER) { it.albumName },
        R.string.order_alphabetically_az
    ),
    ALPHABETICALLY_Z_A(
        compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.albumName },
        R.string.order_alphabetically_za
    ),

    /** Date of the newest photo in the album (capture date). Was "Last updated". */
    NEWEST_PHOTOS(byDate(true) { it.endDate }, R.string.albums_order_newest_photos),

    /** Date of the newest photo, oldest album first. Was "Least updated". */
    OLDEST_PHOTOS(byDate(false) { it.endDate }, R.string.albums_order_oldest_photos),

    /** Album changed on the server (photos added, renamed, ...). */
    RECENTLY_MODIFIED(byDate(true) { it.updatedAt }, R.string.albums_order_recently_modified),
    RECENTLY_CREATED(byDate(true) { it.createdAt ?: it.startDate }, R.string.albums_order_recently_created),
    MOST_ITEMS(compareByDescending { it.assetCount }, R.string.albums_order_asset_count),
    FEWEST_ITEMS(compareBy { it.assetCount }, R.string.albums_order_fewest_items);

    override fun getTitle(): String = ImmichApplication.appContext!!.getString(titleRes)

    companion object {
        /** Values stored before the options were renamed. */
        private val legacyNames = mapOf(
            "LAST_UPDATED" to NEWEST_PHOTOS,
            "LEAST_UPDATED" to OLDEST_PHOTOS,
            "ASSET_COUNT" to MOST_ITEMS
        )

        fun valueOfSafe(name: String, default: AlbumsOrder): AlbumsOrder {
            return entries.find { it.toString() == name } ?: legacyNames[name] ?: default
        }
    }
}
