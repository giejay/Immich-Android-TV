package nl.giejay.android.tv.immich.api.demo

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.InputStream
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Answers the Immich API calls of the app from [DemoCatalog], so the demo mode needs neither a server nor a key.
 * Only requests to [DemoCatalog.HOST] are handled; everything else goes to the network as usual.
 *
 * @param openAsset opens a bundled sample photo by file name (`assets/demo/<file>`).
 */
class DemoInterceptor(private val openAsset: (String) -> InputStream) : Interceptor {
    private val gson = Gson()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != DemoCatalog.HOST) return chain.proceed(request)
        return try {
            route(request)
        } catch (e: Exception) {
            respond(request, 500, mapOf("message" to "demo error: ${e.message}"))
        }
    }

    private fun route(request: Request): Response {
        // path: /api/<resource>/...
        val parts = request.url.pathSegments.dropWhile { it == "api" }.filter { it.isNotEmpty() }
        val method = request.method
        val query = { name: String -> request.url.queryParameter(name) }
        return when {
            parts == listOf("albums") -> {
                // with assetId only the albums that contain that photo (album-name metadata), else all albums
                val assetId = query("assetId")
                respond(request, 200, DemoCatalog.albums.filter { assetId == null || it.photos.any { photo -> photo.id == assetId } }.map(::albumJson))
            }
            parts.size == 2 && parts[0] == "albums" ->
                DemoCatalog.album(parts[1])?.let { respond(request, 200, albumJson(it)) } ?: notFound(request)
            parts == listOf("search", "metadata") -> respond(request, 200, searchResponse(metadataSearch(body(request))))
            parts == listOf("search", "smart") -> respond(request, 200, searchResponse(smartSearch(body(request))))
            parts == listOf("search", "cities") ->
                respond(request, 200, DemoCatalog.photos.distinctBy { DemoCatalog.place(it).city }.map(::assetJson))
            parts == listOf("tags") ->
                respond(request, 200, DemoCatalog.tags.map { mapOf("id" to it.id, "name" to it.name, "value" to it.value, "color" to null) })
            parts == listOf("search", "person") -> respond(request, 200, emptyList<Any>())
            parts == listOf("search", "random") -> respond(request, 200, randomAssets(body(request)).map(::assetJson))
            parts == listOf("people") -> respond(request, 200, mapOf("total" to 0, "hasNextPage" to false, "people" to emptyList<Any>()))
            parts == listOf("memories") -> respond(request, 200, emptyList<Any>())
            parts == listOf("timeline", "buckets") -> respond(request, 200, timeBuckets(query("order")))
            parts == listOf("timeline", "bucket") -> respond(request, 200, timeBucket(query("timeBucket"), query("order")))
            parts == listOf("view", "folder", "unique-paths") -> respond(request, 200, DemoCatalog.albums.map { it.name })
            parts == listOf("view", "folder") ->
                respond(request, 200, DemoCatalog.albums.firstOrNull { it.name == query("path")?.trim('/') }?.photos.orEmpty().map(::assetJson))
            parts.size == 2 && parts[0] == "assets" && method == "GET" ->
                DemoCatalog.photo(parts[1])?.let { respond(request, 200, assetJson(it)) } ?: notFound(request)
            parts.size == 2 && parts[0] == "assets" && method == "PUT" -> {
                val photo = DemoCatalog.photo(parts[1]) ?: return notFound(request)
                val favorite = body(request).get("isFavorite")?.asBoolean
                if (favorite == true) DemoCatalog.favorites.add(photo.id) else if (favorite == false) DemoCatalog.favorites.remove(photo.id)
                respond(request, 200, assetJson(photo))
            }
            parts.size == 3 && parts[0] == "assets" && (parts[2] == "thumbnail" || parts[2] == "original") ->
                DemoCatalog.photo(parts[1])?.let { image(request, it) } ?: notFound(request)
            else -> notFound(request)
        }
    }

    // ---- searches -----------------------------------------------------------------------------------------------

    private fun metadataSearch(body: JsonObject): SearchResult {
        var items = filtered(body)
        if (body.string("order") == "asc") items = items.reversed()
        return page(items, body)
    }

    /** The filters that metadata search and random search share: album, type, name, description and taken dates. */
    private fun filtered(body: JsonObject): List<DemoPhoto> {
        val albumIds = body.stringList("albumIds")
        val type = body.string("type")
        val name = body.string("originalFileName")?.lowercase()
        val description = body.string("description")?.lowercase()
        val city = body.string("city")
        // the demo library has no archived photos
        val archivedOnly = body.string("visibility") == "archive"
        val favoritesOnly = body.get("isFavorite")?.takeIf { !it.isJsonNull }?.asBoolean == true
        val tagIds = body.stringList("tagIds")
        val before = body.string("takenBefore")?.let(::parseDate)
        val after = body.string("takenAfter")?.let(::parseDate)
        return DemoCatalog.photos.filter { photo ->
            (albumIds.isEmpty() || albumIds.any { DemoCatalog.album(it)?.photos?.contains(photo) == true }) &&
                !archivedOnly &&
                (!favoritesOnly || photo.id in DemoCatalog.favorites) &&
                (city == null || DemoCatalog.place(photo).city.equals(city, ignoreCase = true)) &&
                (tagIds.isEmpty() || DemoCatalog.tags.any { it.id in tagIds && photo.theme in it.themes }) &&
                (type == null || type == "IMAGE") &&
                (name == null || photo.file.lowercase().contains(name)) &&
                (description == null || description(photo).lowercase().contains(description)) &&
                (before == null || photo.takenAt.isBefore(before)) &&
                (after == null || photo.takenAt.isAfter(after))
        }
    }

    private fun smartSearch(body: JsonObject): SearchResult {
        val query = body.string("query")?.lowercase().orEmpty().trim()
        val items = filtered(body).filter { query.isNotEmpty() && description(it).lowercase().contains(query) }
        return page(items, body)
    }

    private fun randomAssets(body: JsonObject): List<DemoPhoto> =
        filtered(body).shuffled().take(body.get("size")?.asInt ?: 100)

    private fun page(items: List<DemoPhoto>, body: JsonObject): SearchResult {
        val size = (body.get("size")?.asInt ?: 100).coerceAtLeast(1)
        val page = (body.get("page")?.asInt ?: 1).coerceAtLeast(1)
        val slice = items.drop((page - 1) * size).take(size)
        val hasMore = page * size < items.size
        return SearchResult(slice, items.size, if (hasMore) (page + 1).toString() else null)
    }

    private fun searchResponse(result: SearchResult) = mapOf(
        "albums" to mapOf("total" to 0, "count" to 0, "items" to emptyList<Any>()),
        "assets" to mapOf(
            "total" to result.total,
            "count" to result.items.size,
            "items" to result.items.map(::assetJson),
            "nextPage" to result.nextPage,
        ),
    )

    private class SearchResult(val items: List<DemoPhoto>, val total: Int, val nextPage: String?)

    // ---- timeline -----------------------------------------------------------------------------------------------

    private fun monthKey(photo: DemoPhoto) = String.format(Locale.ROOT, "%04d-%02d", photo.takenAt.year, photo.takenAt.monthValue)

    private fun timeBuckets(order: String?): List<Map<String, Any>> {
        val months = DemoCatalog.photos.groupBy(::monthKey).toSortedMap()
        val sorted = if (order == "asc") months.entries else months.entries.reversed()
        return sorted.map { (month, photos) -> mapOf("timeBucket" to "$month-01", "count" to photos.size) }
    }

    private fun timeBucket(bucket: String?, order: String?): Map<String, Any?> {
        val month = bucket.orEmpty().take(7)
        val photos = DemoCatalog.photos.filter { monthKey(it) == month }.let { if (order == "asc") it.reversed() else it }
        return mapOf(
            "id" to photos.map { it.id },
            "ownerId" to photos.map { OWNER_ID },
            "ratio" to photos.map { it.ratio },
            "isFavorite" to photos.map { it.id in DemoCatalog.favorites },
            "visibility" to photos.map { "timeline" },
            "isTrashed" to photos.map { false },
            "isImage" to photos.map { true },
            "thumbhash" to photos.map { null },
            "fileCreatedAt" to photos.map { iso(it.takenAt) },
            "localOffsetHours" to photos.map { 0.0 },
            "duration" to photos.map { null },
        )
    }

    // ---- json shapes --------------------------------------------------------------------------------------------

    private fun description(photo: DemoPhoto) = "${DemoCatalog.themeName(photo.theme)} photo"

    private fun albumJson(album: DemoAlbum) = mapOf(
        "albumName" to album.name,
        "description" to "",
        "id" to album.id,
        "albumThumbnailAssetId" to album.photos.first().id,
        "updatedAt" to iso(album.photos.first().takenAt),
        "endDate" to iso(album.photos.first().takenAt),
        "createdAt" to iso(album.photos.last().takenAt),
        "assetCount" to album.photos.size,
    )

    private fun assetJson(photo: DemoPhoto) = mapOf(
        "id" to photo.id,
        "type" to "IMAGE",
        "deviceAssetId" to photo.file,
        "exifInfo" to mapOf(
            "description" to description(photo),
            "orientation" to 1,
            "exifImageWidth" to photo.width,
            "exifImageHeight" to photo.height,
            "city" to DemoCatalog.place(photo).city,
            "country" to DemoCatalog.place(photo).country,
            "dateTimeOriginal" to iso(photo.takenAt),
            "make" to null,
            "model" to null,
        ),
        "fileCreatedAt" to iso(photo.takenAt),
        "fileModifiedAt" to iso(photo.takenAt),
        "albumName" to null,
        "people" to emptyList<Any>(),
        "tags" to emptyList<Any>(),
        "originalPath" to "/demo/${DemoCatalog.themeName(photo.theme)}/${photo.file}",
        "originalFileName" to photo.file,
        "isFavorite" to (photo.id in DemoCatalog.favorites),
    )

    // ---- plumbing -----------------------------------------------------------------------------------------------

    private fun image(request: Request, photo: DemoPhoto): Response {
        val bytes = openAsset(photo.file).use { it.readBytes() }
        return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(bytes.toResponseBody("image/jpeg".toMediaType())).build()
    }

    private fun respond(request: Request, code: Int, payload: Any): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message(if (code == 200) "OK" else "Error")
            .body(gson.toJson(payload).toResponseBody("application/json".toMediaType())).build()

    private fun notFound(request: Request) = respond(request, 404, mapOf("message" to "Not found in demo mode"))

    private fun body(request: Request): JsonObject {
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        val text = buffer.readUtf8()
        return if (text.isBlank()) JsonObject() else gson.fromJson(text, JsonObject::class.java)
    }

    private fun JsonObject.string(name: String): String? = get(name)?.takeIf { !it.isJsonNull }?.asString

    private fun JsonObject.stringList(name: String): List<String> =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }.orEmpty()

    private fun parseDate(value: String): OffsetDateTime =
        runCatching { OffsetDateTime.parse(value) }.getOrElse { OffsetDateTime.of(java.time.LocalDateTime.parse(value), ZoneOffset.UTC) }

    private fun iso(date: OffsetDateTime): String = date.withOffsetSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private companion object {
        const val OWNER_ID = "00000000-0000-4000-8000-0000000000aa"
    }
}
