package nl.giejay.android.tv.immich.api

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.flatMap
import arrow.core.getOrElse
import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.AssetResponse
import nl.giejay.android.tv.immich.api.model.Folder
import nl.giejay.android.tv.immich.api.model.Memory
import nl.giejay.android.tv.immich.api.model.Person
import nl.giejay.android.tv.immich.api.model.TagInfo
import nl.giejay.android.tv.immich.api.model.SearchRequest
import nl.giejay.android.tv.immich.api.model.UpdateAssetRequest
import nl.giejay.android.tv.immich.api.model.SearchResponse
import nl.giejay.android.tv.immich.api.model.SmartSearchRequest
import nl.giejay.android.tv.immich.api.model.TimeBucketSummary
import nl.giejay.android.tv.immich.api.model.TimelineAsset
import nl.giejay.android.tv.immich.api.model.toTimelineAssets
import nl.giejay.android.tv.immich.api.service.ApiService
import nl.giejay.android.tv.immich.api.util.ApiUtil.executeAPICall
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.ContentType
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.DISABLE_SSL_VERIFICATION
import nl.giejay.android.tv.immich.shared.prefs.EXCLUDE_ASSETS_IN_ALBUM
import nl.giejay.android.tv.immich.shared.prefs.PROXY_PASSWORD
import nl.giejay.android.tv.immich.shared.prefs.PROXY_USER
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.RECENT_ASSETS_MONTHS_BACK
import nl.giejay.android.tv.immich.shared.prefs.SHOW_PARTNER_PHOTOS_IN_TIMELINE
import nl.giejay.android.tv.immich.shared.prefs.SIMILAR_ASSETS_PERIOD_DAYS
import nl.giejay.android.tv.immich.shared.prefs.SIMILAR_ASSETS_YEARS_BACK
import nl.giejay.android.tv.immich.shared.util.Utils.pmap
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ApiClientConfig(
    val hostName: String,
    val apiKey: String,
    val disableSslVerification: Boolean,
    val debugMode: Boolean,
    // read from the preferences by default, so every construction site carries the current proxy login
    val proxyUser: String = PreferenceManager.get(PROXY_USER),
    val proxyPassword: String = PreferenceManager.get(PROXY_PASSWORD)
) {
    companion object {
        fun fromPrefs(): ApiClientConfig = ApiClientConfig(
            PreferenceManager.hostName,
            PreferenceManager.get(API_KEY),
            PreferenceManager.get(DISABLE_SSL_VERIFICATION),
            PreferenceManager.get(DEBUG_MODE)
        )
    }
}

// Immich rejects /search/metadata and /search/random requests with a size above 1000 (400 Bad Request)
const val MAX_SEARCH_PAGE_SIZE = 1000

// internal so app/src/test can call it directly without instantiating ApiClient/Retrofit
internal fun buildListAssetsSearchRequest(
    page: Int,
    pageCount: Int,
    albumIds: List<String>,
    order: String,
    type: String?,
    personIds: List<UUID>,
    endDate: String?,
    fromDate: String?,
    city: String? = null,
    tagIds: List<String> = emptyList(),
    isFavorite: Boolean? = null,
    visibility: String? = null
): SearchRequest = SearchRequest(
    page = page,
    size = pageCount,
    albumIds = albumIds,
    order = order,
    type = type,
    personIds = personIds,
    takenBefore = endDate,
    takenAfter = fromDate,
    city = city,
    tagIds = tagIds.ifEmpty { null },
    isFavorite = isFavorite,
    // "timeline" reproduces pre-v3 default for normal browsing; album-scoped search
    // omits it so archived-in-album assets keep showing (VIS-02)
    // an explicit visibility (the archive) wins over the browsing default
    visibility = visibility ?: if (albumIds.isEmpty()) "timeline" else null
)

// internal so app/src/test can call it directly without instantiating ApiClient/Retrofit.
// Server default for withPartners is false/omitted, so only send true when the user opted in -
// issue #157: partner-shared photos never showed up in the Timeline because this was never wired up.
internal fun resolveWithPartners(showPartnerPhotosInTimeline: Boolean): Boolean? {
    return if (showPartnerPhotosInTimeline) true else null
}

internal fun SearchResponse.toAssetResponse(): AssetResponse =
    AssetResponse(assets.items, assets.nextPage != null)

internal fun List<Asset>.toRandomAssetResponse(): AssetResponse =
    AssetResponse(this, true)

class ApiClient(private val config: ApiClientConfig) {
    companion object ApiClient {
        private const val SMART_SEARCH_RETRY_MILLIS = 5 * 60 * 1000L
        private const val MAX_FALLBACK_HITS_PER_FIELD = 500
        private const val MAX_TAGS = 100
        private const val COVER_PAGE_SIZE = 100
        private const val COVER_SEARCH_PAGES = 5

        private var apiClient: nl.giejay.android.tv.immich.api.ApiClient? = null
        fun getClient(config: ApiClientConfig): nl.giejay.android.tv.immich.api.ApiClient {
            if (config != apiClient?.config) {
                apiClient = ApiClient(config)
            }
            return apiClient!!
        }

        // ISO_DATE_TIME on a zone-less LocalDateTime omits the offset entirely (e.g. "2026-07-04T14:30:00"),
        // but Immich v3's takenBefore/takenAfter schema requires a mandatory trailing Z/offset
        // (^...T...(?:Z|[+-]HH:mm)$) - v2 tolerated the offset-less form, v3's stricter validation
        // rejects it with a 400. Attach the device's zone before formatting so the offset is always present.
        val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    }

    private val retrofit = Retrofit.Builder()
        .client(ApiClientFactory.getClient(config.disableSslVerification, config.apiKey, config.debugMode, config.proxyUser, config.proxyPassword))
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl("${config.hostName}/api/")
        .build()

    private val service: ApiService = retrofit.create(ApiService::class.java)

    private suspend fun search(searchRequest: SearchRequest): Either<String, SearchResponse> {
        return executeAPICall(200) { service.listAssets(searchRequest) }
    }

    /** Until when smart search is assumed to be unavailable (no machine learning on the server); 0 = not known to fail. */
    @Volatile private var smartSearchUnavailableUntil = 0L

    /**
     * Search by text: by image content when the server has machine learning (smart search), otherwise by
     * file name, description, city and country. A failed smart search falls back for a while so the
     * slower double request does not happen on every query.
     */
    /** Which backend serves the pages of the search that is running: it must not change between its pages. */
    private enum class SearchBackend { SMART, METADATA }
    private var searchBackend = SearchBackend.METADATA

    suspend fun searchAssets(query: String, page: Int, pageCount: Int, contentType: ContentType, albumIds: List<String> = emptyList()): Either<String, AssetResponse> {
        val type = if (contentType == ContentType.ALL) null else contentType.toString()
        val size = pageCount.coerceAtMost(MAX_SEARCH_PAGE_SIZE)
        val isFirstPage = page <= 1
        // only the first page of a search chooses; later pages stay with that choice (or fail, never mix)
        val smartAllowed = if (isFirstPage) System.currentTimeMillis() >= smartSearchUnavailableUntil else searchBackend == SearchBackend.SMART
        if (smartAllowed) {
            val smart = executeAPICall(200) { service.smartSearch(SmartSearchRequest(query = query, page = page, size = size, type = type, albumIds = albumIds.ifEmpty { null }, visibility = if (albumIds.isEmpty()) "timeline" else null)) }
            if (smart.isRight()) {
                searchBackend = SearchBackend.SMART
                return smart.map { it.toAssetResponse() }.map { it.copy(assets = it.assets.filter(excludeByTag())) }
            }
            if (!isFirstPage) return smart.map { AssetResponse(emptyList(), false) }
            // (a cancelled call has thrown before this point, so an interrupted request never lands here)
            smartSearchUnavailableUntil = System.currentTimeMillis() + SMART_SEARCH_RETRY_MILLIS
        }
        searchBackend = SearchBackend.METADATA
        return metadataSearchPage(query, type, page, size, albumIds)
    }

    /** The merged metadata hits of the last query, so paging does not fetch and merge everything again. */
    private var metadataCache: Pair<List<Any?>, List<Asset>>? = null

    /**
     * The four field searches are paginated separately, so a page of each cannot be merged into a correct
     * global order. Instead the hits of every field are fetched once (up to [MAX_FALLBACK_HITS_PER_FIELD]),
     * merged and sorted, and the page is cut from that list.
     */
    private suspend fun metadataSearchPage(query: String, type: String?, page: Int, size: Int, albumIds: List<String>): Either<String, AssetResponse> {
        val cacheKey = listOf(query, type, albumIds)
        // a new search (page 1) always looks again; the cache only serves the pages of the search in progress
        if (page <= 1) metadataCache = null
        val cached = metadataCache?.takeIf { it.first == cacheKey }?.second
        val merged = cached ?: run {
            val perField = buildMetadataSearchRequests(query, 1, MAX_SEARCH_PAGE_SIZE, type, albumIds).pmap { first ->
                var hits = emptyList<Asset>()
                var request = first
                while (true) {
                    val response = search(request).getOrElse { return@pmap if (hits.isEmpty()) Either.Left(it) else Either.Right(hits) }
                    hits = hits + response.assets.items
                    if (response.assets.nextPage == null || hits.size >= MAX_FALLBACK_HITS_PER_FIELD) break
                    request = request.copy(page = (request.page ?: 1) + 1)
                }
                Either.Right(hits)
            }
            if (perField.all { it.isLeft() }) return perField.first().map { AssetResponse(emptyList(), false) }
            mergeSearchResults(perField.mapNotNull { it.getOrNull() }).filter(excludeByTag())
                .also { metadataCache = cacheKey to it }
        }
        val slice = sliceSearchPage(merged, page, size)
        return Either.Right(AssetResponse(slice.assets, slice.hasMore))
    }

    suspend fun searchPeople(name: String): Either<String, List<Person>> {
        return executeAPICall(200) { service.searchPeople(name) }.map { people -> people.filter { !it.name.isNullOrBlank() } }
    }

    suspend fun listAlbums(assetId: Option<String> = None): Either<String, List<Album>> {
        return executeAPICall(200) { service.listAlbums(assetId.getOrNull()) }
    }

    suspend fun listPeople(): Either<String, List<Person>> {
        return executeAPICall(200) { service.listPeople() }.map { response -> response.people.filter { !it.name.isNullOrBlank() } }
    }

    suspend fun listAssetsFromAlbum(albumIds: List<String>,
                                    contentType: ContentType = ContentType.ALL,
                                    pageCount: Int = 100): Either<String, AssetResponse> {
        val results = albumIds.pmap { albumId ->
            val album = executeAPICall(200) { service.getAlbum(albumId) }
                .getOrElse { return@pmap Either.Left(it) }

            val total = album.assetCount
            val size = pageCount.coerceAtMost(MAX_SEARCH_PAGE_SIZE)
            val numPages = (total + size - 1) / size
            val pages = (1..numPages).toList()

            val assetsResults = pages.pmap { page ->
                search(SearchRequest(
                    page = page,
                    size = size,
                    albumIds = listOf(albumId),
                    type = if (contentType == ContentType.ALL) null else contentType.toString()
                ))
            }

            val albumAssets = mutableListOf<Asset>()
            for (res in assetsResults) {
                val resp = res.getOrElse { return@pmap Either.Left(it) }
                albumAssets.addAll(resp.assets.items)
            }
            Either.Right(albumAssets.toList())
        }

        val allAssets = mutableListOf<Asset>()
        for (res in results) {
            val assets = res.getOrElse { return Either.Left(it) }
            allAssets.addAll(assets)
        }
        return Either.Right(AssetResponse(allAssets.filter(excludeByTag()), false))
    }

    suspend fun recentAssets(page: Int, pageCount: Int, contentType: ContentType): Either<String, AssetResponse> {
        val now = LocalDateTime.now()
        return listAssets(page, pageCount, true, "desc",
            contentType = contentType, fromDate = now.minusMonths(PreferenceManager.get(RECENT_ASSETS_MONTHS_BACK).toLong()), endDate = now)
            .map { it.copy(assets = it.assets.shuffled()) }
    }

    /** The favorites, newest first per page; the page is shuffled so the slideshow does not run in date order. */
    suspend fun favoriteAssets(page: Int, pageCount: Int, contentType: ContentType): Either<String, AssetResponse> =
        listAssetsByFilter(page, pageCount, contentType, favorite = true)
            .map { response -> response.copy(assets = response.assets.filter(excludeByTag()).shuffled()) }

    suspend fun similarAssets(page: Int, pageCount: Int, contentType: ContentType): Either<String, AssetResponse> {
        val now = LocalDateTime.now()
        val map: List<Either<String, AssetResponse>> = (0 until PreferenceManager.get(SIMILAR_ASSETS_YEARS_BACK)).toList().map {
            listAssets(page,
                pageCount,
                true,
                "desc",
                fromDate = now.minusDays((PreferenceManager.get(SIMILAR_ASSETS_PERIOD_DAYS) / 2).toLong()).minusYears(it.toLong()),
                endDate = now.plusDays((PreferenceManager.get(SIMILAR_ASSETS_PERIOD_DAYS) / 2).toLong()).minusYears(it.toLong()),
                contentType = contentType)
        }
        if (map.all { it.isLeft() }) {
            return map.first()
        }
        val responses = map.mapNotNull { it.getOrNull() }
        return Either.Right(AssetResponse(
            responses.flatMap { it.assets }.shuffled(),
            responses.any { it.canLoadMore }
        ))
    }

    suspend fun listAssets(page: Int,
                           pageCount: Int,
                           random: Boolean = false,
                           order: String = "desc",
                           personIds: List<UUID> = emptyList(),
                           fromDate: LocalDateTime? = null,
                           endDate: LocalDateTime? = null,
                           contentType: ContentType,
                           albumIds: List<String> = emptyList()): Either<String, AssetResponse> {
        val searchRequest = buildListAssetsSearchRequest(
            page = page,
            pageCount = pageCount,
            albumIds = albumIds,
            order = order,
            type = if (contentType == ContentType.ALL) null else contentType.toString(),
            personIds = personIds,
            endDate = endDate?.atZone(ZoneId.systemDefault())?.format(dateTimeFormatter),
            fromDate = fromDate?.atZone(ZoneId.systemDefault())?.format(dateTimeFormatter)
        )
        val assetsResult = if (random) {
            // RandomSearchDto (POST /search/random) has no `page`/`order` properties at all -
            // MetadataSearchDto is the only search DTO that supports pagination/ordering. Immich v3's
            // stricter request validation rejects unknown properties with a 400, so they must be
            // stripped here rather than sent as on the metadata-search path below.
            executeAPICall(200) { service.randomAssets(searchRequest.copy(page = null, order = null)) }
                .map { it.toRandomAssetResponse() }
        } else {
            search(searchRequest).map { it.toAssetResponse() }
        }
        return assetsResult
    }

    private fun excludeByTag() = { asset: Asset ->
        asset.tags?.none { t -> t.name == "exclude_immich_tv" } ?: true
    }

    /** The photos of one place (city) or one tag, newest first (a separate function so [listAssets] keeps its signature). */
    suspend fun listAssetsByFilter(page: Int, pageCount: Int, contentType: ContentType, city: String? = null, tagIds: List<String> = emptyList(), order: String = "desc", favorite: Boolean? = null, visibility: String? = null): Either<String, AssetResponse> {
        val request = buildListAssetsSearchRequest(
            page = page, pageCount = pageCount, albumIds = emptyList(), order = order,
            type = if (contentType == ContentType.ALL) null else contentType.toString(),
            personIds = emptyList(), endDate = null, fromDate = null, city = city, tagIds = tagIds, isFavorite = favorite, visibility = visibility,
        )
        return search(request).map { it.toAssetResponse() }
    }

    /**
     * The newest photo of a city or tag that the TV app may show (photos tagged `exclude_immich_tv` are skipped).
     * Pages through the hits until one is found or the search is exhausted (at most [COVER_SEARCH_PAGES] pages).
     */
    private suspend fun findCover(city: String? = null, tagIds: List<String> = emptyList()): Asset? {
        val allowed = excludeByTag()
        for (page in 1..COVER_SEARCH_PAGES) {
            val response = listAssetsByFilter(page, COVER_PAGE_SIZE, ContentType.ALL, city = city, tagIds = tagIds).getOrNull() ?: return null
            response.assets.firstOrNull(allowed)?.let { return it }
            if (!response.canLoadMore) return null
        }
        return null
    }

    /** One photo per place (city), sorted by city name; the photo serves as the card picture. */
    suspend fun listPlaces(): Either<String, List<Asset>> =
        executeAPICall(200) { service.listCities() }.map { assets ->
            val places = assets.filter { !it.exifInfo?.city.isNullOrBlank() }
                .distinctBy { it.exifInfo?.city }
                .sortedBy { it.exifInfo?.city?.lowercase() }
            val allowed = excludeByTag()
            places.pmap { representative ->
                if (allowed(representative)) {
                    representative
                } else {
                    // the server's pick is excluded from the TV app: another photo of the city is the cover,
                    // and a city with nothing left to show is dropped (opening it would be empty anyway)
                    findCover(city = representative.exifInfo?.city)
                }
            }.filterNotNull()
        }

    /** The tags with a picture each (the newest photo carrying it, if any), sorted by name. */
    suspend fun listTagsWithPictures(): Either<String, List<Pair<TagInfo, String?>>> =
        executeAPICall(200) { service.listTags() }.map { tags ->
            tags.sortedBy { it.value.lowercase() }.take(MAX_TAGS).pmap { tag ->
                tag to findCover(tagIds = listOf(tag.id))?.id
            }
        }

    suspend fun listFolders(): Either<String, Folder> {
        return executeAPICall(200) {
            service.getUniquePaths()
        }.map { paths ->
            return Either.Right(createRootFolder(Folder("", mutableListOf(), null), paths))
        }
    }

    private fun createRootFolder(parent: Folder, paths: List<String>): Folder {
        paths.forEach { path ->
            val directories = path.split("/")
            createFolders(directories, parent)
        }
        return parent
    }

    private fun createFolders(paths: List<String>, currentParent: Folder): Folder {
        if (paths.isEmpty()) {
            return currentParent
        }
        val createdChild = Folder(paths.first(), mutableListOf(), currentParent)
        val alreadyOwnedChild = currentParent.hasPath(paths.first())
        if (alreadyOwnedChild != null) {
            return createFolders(paths.drop(1), alreadyOwnedChild)
        }
        currentParent.children.add(createdChild)
        return createFolders(paths.drop(1), createdChild)
    }

    suspend fun listAssetsForFolder(folder: String): Either<String, List<Asset>> {
        return executeAPICall(200) {
            service.getAssetsForPath(folder)
        }.map { it.filter(excludeByTag()) }
    }

    suspend fun getAsset(id: String): Either<String, Asset> {
        return executeAPICall(200) { service.getAsset(id) }
    }

    suspend fun updateFavorite(id: String, isFavorite: Boolean): Either<String, Asset> {
        return executeAPICall(200) {
            service.updateAsset(id, UpdateAssetRequest(isFavorite))
        }
    }

    suspend fun getTimeBuckets(): Either<String, List<TimeBucketSummary>> =
        executeAPICall(200) {
            service.getTimeBuckets(withPartners = resolveWithPartners(PreferenceManager.get(SHOW_PARTNER_PHOTOS_IN_TIMELINE)))
        }

    /**
     * Fetches all assets for a month bucket.
     *
     * V1 gap: [excludeByTag] / EXCLUDE_ASSETS_IN_ALBUM cannot apply here — bucket responses
     * omit tags and album membership. Filtering those requires a follow-up ID set cross-check.
     */
    suspend fun getTimeBucket(timeBucket: String): Either<String, List<TimelineAsset>> =
        executeAPICall(200) {
            service.getTimeBucket(timeBucket, withPartners = resolveWithPartners(PreferenceManager.get(SHOW_PARTNER_PHOTOS_IN_TIMELINE)))
        }.map { it.toTimelineAssets() }

    /** "On this day" style memories for the current moment (server filters by day-of-year). */
    suspend fun getMemories(): Either<String, List<Memory>> {
        val date = LocalDate.now().toString()
        return executeAPICall(200) { service.getMemories(date) }.fold(
            { error ->
                if (error.contains("(400)")) {
                    val isoDate = OffsetDateTime.now().format(dateTimeFormatter)
                    executeAPICall(200) { service.getMemories(isoDate) }
                } else {
                    Either.Left(error)
                }
            },
            { Either.Right(it) }
        )
    }
}
