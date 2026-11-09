package nl.giejay.android.tv.immich.api.demo

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale

/** One bundled sample photo (file under `assets/demo/`). */
data class DemoPhoto(
    val id: String,
    val file: String,
    val theme: String,
    val width: Int,
    val height: Int,
    val takenAt: OffsetDateTime,
) {
    val ratio: Double get() = width.toDouble() / height
}

data class DemoAlbum(val id: String, val name: String, val photos: List<DemoPhoto>)

/**
 * Offline sample data for the demo mode: a handful of stock photos (see README for the credits) grouped into
 * albums by theme. Served by [DemoInterceptor] in place of an Immich server.
 */
object DemoCatalog {
    /** Reserved TLD: this host can never resolve, so the demo never talks to a real server. */
    const val HOST = "demo.immich-tv-next.invalid"
    const val HOST_URL = "http://$HOST"
    const val API_KEY = "demo"

    private val files = listOf(
        Triple("mountains-12393620.jpg", 954, 1280), Triple("mountains-26311729.jpg", 1280, 848),
        Triple("mountains-27413514.jpg", 1280, 853), Triple("mountains-30666562.jpg", 1280, 848),
        Triple("beach-11006267.jpg", 853, 1280), Triple("beach-12831944.jpg", 1280, 853),
        Triple("beach-188014.jpg", 1280, 853), Triple("beach-22480990.jpg", 853, 1280),
        Triple("city-18088744.jpg", 1280, 754), Triple("city-18834566.jpg", 1280, 779),
        Triple("city-24345783.jpg", 1280, 720), Triple("city-286720.jpg", 1280, 852),
        Triple("forest-10173742.jpg", 1280, 960), Triple("forest-34427614.jpg", 853, 1280),
        Triple("forest-34574533.jpg", 1280, 960), Triple("forest-34693090.jpg", 1280, 848),
        Triple("food-12737797.jpg", 853, 1280), Triple("food-12737803.jpg", 853, 1280),
        Triple("food-31771054.jpg", 853, 1280), Triple("food-34472822.jpg", 853, 1280),
    )

    private val newest = OffsetDateTime.of(2026, 10, 21, 10, 0, 0, 0, ZoneOffset.UTC)

    /** Days before [newest] per photo (in [files] order): several photos share a day so the timeline shows rows. */
    private val dayOffsets = listOf(0, 0, 0, 0, 9, 9, 9, 21, 21, 34, 48, 48, 48, 63, 80, 80, 101, 101, 101, 130)

    /** Newest first; the offsets span about four months. */
    val photos: List<DemoPhoto> = files.mapIndexed { index, (file, width, height) ->
        DemoPhoto(
            id = String.format(Locale.ROOT, "00000000-0000-4000-8000-%012d", index + 1),
            file = file,
            theme = file.substringBefore('-'),
            width = width,
            height = height,
            takenAt = newest.minusDays(dayOffsets[index].toLong()).minusMinutes((index % 4) * 25L),
        )
    }.sortedByDescending { it.takenAt }

    val albums: List<DemoAlbum> = photos.groupBy { it.theme }.entries.mapIndexed { index, (theme, items) ->
        DemoAlbum(String.format(Locale.ROOT, "10000000-0000-4000-8000-%012d", index + 1), themeName(theme), items)
    }.sortedByDescending { it.photos.first().takenAt }

    /** Favorites toggled in the demo live only in memory. */
    val favorites: MutableSet<String> = mutableSetOf()

    fun themeName(theme: String): String = theme.replaceFirstChar { it.titlecase(Locale.ROOT) }

    fun photo(id: String): DemoPhoto? = photos.firstOrNull { it.id == id }
    fun album(id: String): DemoAlbum? = albums.firstOrNull { it.id == id }
}
