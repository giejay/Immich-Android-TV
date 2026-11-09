package nl.giejay.android.tv.immich.api.demo

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.PeopleResponse
import nl.giejay.android.tv.immich.api.model.SearchResponse
import nl.giejay.android.tv.immich.api.model.TagInfo
import nl.giejay.android.tv.immich.api.model.TimeBucketAssetsResponse
import nl.giejay.android.tv.immich.api.model.TimeBucketSummary
import nl.giejay.android.tv.immich.api.model.toTimelineAssets
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test

/** The demo answers are parsed with the app's real API models, so a shape the app cannot read fails here. */
class DemoInterceptorTest {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .addInterceptor(DemoInterceptor { file -> file.byteInputStream() })
        .build()

    @Before
    fun resetFavorites() = DemoCatalog.favorites.clear()

    private fun get(path: String) = client.newCall(Request.Builder().url("${DemoCatalog.HOST_URL}/api/$path").build()).execute()

    private fun post(path: String, json: String) = client.newCall(
        Request.Builder().url("${DemoCatalog.HOST_URL}/api/$path").post(json.toRequestBody("application/json".toMediaType())).build()
    ).execute()

    private inline fun <reified T> parse(body: String?): T = gson.fromJson(body, T::class.java)

    @Test
    fun `albums list has one album per theme with a thumbnail`() {
        val albums = gson.fromJson(get("albums").body!!.string(), Array<Album>::class.java).toList()

        assertThat(albums.map { it.albumName }).containsExactly("Mountains", "Beach", "City", "Forest", "Food")
        assertThat(albums.all { it.albumThumbnailAssetId != null && it.assetCount == 4 }).isTrue()
    }

    @Test
    fun `albums can be filtered to the albums containing one photo`() {
        val photo = DemoCatalog.albums.first { it.name == "City" }.photos.first()
        val albums = gson.fromJson(get("albums?assetId=${photo.id}").body!!.string(), Array<Album>::class.java).toList()

        assertThat(albums.map { it.albumName }).containsExactly("City")
        assertThat(gson.fromJson(get("albums?assetId=unknown").body!!.string(), Array<Album>::class.java)).isEmpty()
    }

    @Test
    fun `album search returns exactly the photos of that album`() {
        val album = DemoCatalog.albums.first { it.name == "Beach" }
        val result = parse<SearchResponse>(post("search/metadata", """{"page":1,"size":100,"albumIds":["${album.id}"]}""").body!!.string())

        assertThat(result.assets.items.map { it.id }).containsExactlyElementsIn(album.photos.map { it.id })
        assertThat(result.assets.nextPage).isNull()
        assertThat(result.assets.items.first().exifInfo?.exifImageWidth).isGreaterThan(0)
    }

    @Test
    fun `search pages through the results`() {
        val first = parse<SearchResponse>(post("search/metadata", """{"page":1,"size":8}""").body!!.string())
        val second = parse<SearchResponse>(post("search/metadata", """{"page":2,"size":8}""").body!!.string())

        assertThat(first.assets.items).hasSize(8)
        assertThat(first.assets.nextPage).isEqualTo("2")
        assertThat(second.assets.items).hasSize(8)
        assertThat(second.assets.items.map { it.id }).containsNoneIn(first.assets.items.map { it.id })
    }

    @Test
    fun `taken filters and order are applied`() {
        val result = parse<SearchResponse>(
            post("search/metadata", """{"page":1,"size":100,"order":"asc","takenAfter":"2026-01-01T00:00:00Z"}""").body!!.string()
        )
        val dates = result.assets.items.map { it.fileCreatedAt!!.time }

        assertThat(dates).isNotEmpty()
        assertThat(dates).isInOrder()
        assertThat(dates.min()).isAtLeast(java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z").toInstant().toEpochMilli())
    }

    @Test
    fun `smart search matches the theme words and unknown words give nothing`() {
        val beach = parse<SearchResponse>(post("search/smart", """{"query":"beach"}""").body!!.string())
        val none = parse<SearchResponse>(post("search/smart", """{"query":"spaceship"}""").body!!.string())

        assertThat(beach.assets.items).hasSize(4)
        assertThat(none.assets.items).isEmpty()
    }

    @Test
    fun `smart search stays inside the requested album`() {
        val food = DemoCatalog.albums.first { it.photos.size < DemoCatalog.photos.size }
        val word = DemoCatalog.themeName(food.photos.first().theme).lowercase()
        val scoped = parse<SearchResponse>(post("search/smart", """{"query":"$word","albumIds":["${food.id}"]}""").body!!.string())
        val unscoped = parse<SearchResponse>(post("search/smart", """{"query":"$word"}""").body!!.string())

        assertThat(scoped.assets.items.map { it.id }).isNotEmpty()
        assertThat(food.photos.map { it.id }).containsAtLeastElementsIn(scoped.assets.items.map { it.id })
        assertThat(scoped.assets.items.size).isAtMost(unscoped.assets.items.size)
    }

    @Test
    fun `places give one photo per city and a city search finds its photos`() {
        val places = parse<Array<Asset>>(get("search/cities").body!!.string()).toList()
        val cities = places.map { it.exifInfo?.city }

        assertThat(cities).containsNoDuplicates()
        assertThat(cities).containsExactly("Innsbruck", "Lisbon", "Berlin", "Freiburg", "Rome")
        val berlin = parse<SearchResponse>(post("search/metadata", """{"city":"berlin"}""").body!!.string())
        assertThat(berlin.assets.items).hasSize(4)
        assertThat(berlin.assets.items.map { it.exifInfo?.city }.distinct()).containsExactly("Berlin")
    }

    @Test
    fun `tags are listed and a tag filter returns only its photos`() {
        val tags = parse<Array<TagInfo>>(get("tags").body!!.string()).toList()
        assertThat(tags.map { it.value }).containsExactly("Holidays", "Trips/City life")

        val holidays = parse<SearchResponse>(post("search/metadata", """{"tagIds":["${tags.first { it.name == "Holidays" }.id}"]}""").body!!.string())
        assertThat(holidays.assets.items).hasSize(8)
        assertThat(holidays.assets.items.all { it.originalFileName!!.startsWith("beach") || it.originalFileName!!.startsWith("mountains") }).isTrue()
    }

    @Test
    fun `favorite filter returns only the favorites`() {
        DemoCatalog.favorites.clear()
        val none = parse<SearchResponse>(post("search/metadata", """{"isFavorite":true}""").body!!.string())
        DemoCatalog.favorites.add(DemoCatalog.photos[2].id)
        val one = parse<SearchResponse>(post("search/metadata", """{"isFavorite":true}""").body!!.string())
        DemoCatalog.favorites.clear()

        assertThat(none.assets.items).isEmpty()
        assertThat(one.assets.items.map { it.id }).containsExactly(DemoCatalog.photos[2].id)
    }

    @Test
    fun `timeline buckets add up and each bucket parses into timeline assets`() {
        val buckets = gson.fromJson(get("timeline/buckets?order=desc").body!!.string(), Array<TimeBucketSummary>::class.java).toList()

        assertThat(buckets.sumOf { it.count }).isEqualTo(DemoCatalog.photos.size)
        val assets = buckets.flatMap {
            parse<TimeBucketAssetsResponse>(get("timeline/bucket?timeBucket=${it.timeBucket}&order=desc").body!!.string()).toTimelineAssets()
        }
        assertThat(assets.map { it.id }).containsExactlyElementsIn(DemoCatalog.photos.map { it.id })
        assertThat(assets.all { it.isImage && it.ratio > 0 }).isTrue()
    }

    @Test
    fun `thumbnail and original are served from the bundled files`() {
        val photo = DemoCatalog.photos.first()

        assertThat(get("assets/${photo.id}/thumbnail?size=preview").body!!.string()).isEqualTo(photo.file)
        assertThat(get("assets/${photo.id}/original").body!!.contentType().toString()).isEqualTo("image/jpeg")
    }

    @Test
    fun `favorite toggles stay in memory`() {
        val photo = DemoCatalog.photos.first()
        val updated = client.newCall(
            Request.Builder().url("${DemoCatalog.HOST_URL}/api/assets/${photo.id}")
                .put("""{"isFavorite":true}""".toRequestBody("application/json".toMediaType())).build()
        ).execute()

        assertThat(parse<Asset>(updated.body!!.string()).isFavorite).isTrue()
        assertThat(parse<Asset>(get("assets/${photo.id}").body!!.string()).isFavorite).isTrue()
    }

    @Test
    fun `people memories and folders are served`() {
        val people = parse<PeopleResponse>(get("people").body!!.string())
        val folders = gson.fromJson(get("view/folder/unique-paths").body!!.string(), Array<String>::class.java).toList()
        val inFolder = gson.fromJson(get("view/folder?path=Food").body!!.string(), Array<Asset>::class.java).toList()

        assertThat(people.people).isEmpty()
        assertThat(get("memories?for=2026-10-21T00:00:00Z").body!!.string()).isEqualTo("[]")
        assertThat(folders).contains("Food")
        assertThat(inFolder).hasSize(4)
    }

    @Test
    fun `folder paths work with the leading slash the folder screen sends`() {
        val inFolder = gson.fromJson(get("view/folder?path=%2FFood").body!!.string(), Array<Asset>::class.java).toList()

        assertThat(inFolder).hasSize(4)
    }

    @Test
    fun `random search applies the type and date filters`() {
        val videos = gson.fromJson(post("search/random", """{"size":50,"type":"VIDEO"}""").body!!.string(), Array<Asset>::class.java).toList()
        val recent = gson.fromJson(
            post("search/random", """{"size":50,"takenAfter":"2026-10-01T00:00:00Z"}""").body!!.string(), Array<Asset>::class.java
        ).toList()
        val limit = java.time.OffsetDateTime.parse("2026-10-01T00:00:00Z").toInstant().toEpochMilli()

        assertThat(videos).isEmpty()
        assertThat(recent).isNotEmpty()
        assertThat(recent.size).isLessThan(DemoCatalog.photos.size)
        assertThat(recent.all { it.fileCreatedAt!!.time > limit }).isTrue()
    }

    @Test
    fun `protocol keys stay ASCII with a locale that has its own digits`() {
        val previous = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
        try {
            val buckets = gson.fromJson(get("timeline/buckets").body!!.string(), Array<TimeBucketSummary>::class.java).toList()
            val bucket = parse<TimeBucketAssetsResponse>(get("timeline/bucket?timeBucket=${buckets.first().timeBucket}").body!!.string())

            assertThat(buckets.all { Regex("""\d{4}-\d{2}-\d{2}""").matches(it.timeBucket) }).isTrue()
            assertThat(bucket.id).isNotEmpty()
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `unknown calls are 404 and other hosts are not touched`() {
        assertThat(get("server/about").code).isEqualTo(404)
        val other = OkHttpClient.Builder()
            .addInterceptor(DemoInterceptor { error("must not be read") })
            .addInterceptor { chain -> okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(204).message("x").body("".toResponseBody()).build() }
            .build()
        val response = other.newCall(Request.Builder().url("http://photos.example.com/api/albums").build()).execute()

        assertThat(response.code).isEqualTo(204)
    }
}
