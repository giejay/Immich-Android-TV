package nl.giejay.android.tv.immich.api

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.getOrElse
import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.Folder
import nl.giejay.android.tv.immich.api.model.Memory
import nl.giejay.android.tv.immich.api.model.Person
import nl.giejay.android.tv.immich.api.model.SearchRequest
import nl.giejay.android.tv.immich.api.model.UpdateAssetRequest
import nl.giejay.android.tv.immich.api.model.SearchResponse
import nl.giejay.android.tv.immich.api.service.ApiService
import nl.giejay.android.tv.immich.api.util.ApiUtil.executeAPICall
import nl.giejay.android.tv.immich.shared.prefs.ContentType
import nl.giejay.android.tv.immich.shared.prefs.EXCLUDE_ASSETS_IN_ALBUM
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.RECENT_ASSETS_MONTHS_BACK
import nl.giejay.android.tv.immich.shared.prefs.SIMILAR_ASSETS_PERIOD_DAYS
import nl.giejay.android.tv.immich.shared.prefs.SIMILAR_ASSETS_YEARS_BACK
import nl.giejay.android.tv.immich.shared.util.Utils.pmap
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ApiClientConfig(
    val hostName: String,
    val apiKey: String,
    val disableSslVerification: Boolean,
    val debugMode: Boolean
)

// internal so app/src/test can call it directly without instantiating ApiClient/Retrofit
internal fun buildListAssetsSearchRequest(
    page: Int,
    pageCount: Int,
    albumIds: List<String>,
    order: String,
    type: String?,
    personIds: List<UUID>,
    endDate: String?,
    fromDate: String?
): SearchRequest = SearchRequest(
    page = page,
    size = pageCount,
    albumIds = albumIds,
    order = order,
    type = type,
    personIds = personIds,
    takenBefore = endDate,
    takenAfter = fromDate,
    // "timeline" reproduces pre-v3 default for normal browsing; album-scoped search
    // omits it so archived-in-album assets keep showing (VIS-02)
    visibility = if (albumIds.isEmpty()) "timeline" else null
)

class ApiClient(private val config: ApiClientConfig) {
    companion object ApiClient {
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
        .client(ApiClientFactory.getClient(config.disableSslVerification, config.apiKey, config.debugMode))
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl("${config.hostName}/api/")
        .build()

    private val service: ApiService = retrofit.create(ApiService::class.java)

    private suspend fun search(searchRequest: SearchRequest): Either<String, SearchResponse> {
        return executeAPICall(200) { service.listAssets(searchRequest) }
    }

    suspend fun listAlbums(assetId: Option<String> = None): Either<String, List<Album>> {
        return executeAPICall(200) { service.listAlbums(assetId.getOrNull()) }
    }

    suspend fun listPeople(): Either<String, List<Person>> {
        return executeAPICall(200) { service.listPeople() }.map { response -> response.people.filter { !it.name.isNullOrBlank() } }
    }

    fun listAssetsFromAlbum(albumIds: List<String>, contentType: ContentType = ContentType.ALL, pageCount: Int = 100): Either<String, List<Asset>> {
        val results = albumIds.pmap { albumId ->
            val album = executeAPICall(200) { service.getAlbum(albumId) }
                .getOrElse { return@pmap Either.Left(it) }

            val total = album.assetCount
            val numPages = (total + pageCount - 1) / pageCount
            val pages = (1..numPages).toList()

            val assetsResults = pages.pmap { page ->
                search(SearchRequest(
                    page = page,
                    size = pageCount,
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
        return Either.Right(allAssets.filter(excludeByTag()))
    }

    suspend fun recentAssets(page: Int, pageCount: Int, contentType: ContentType): Either<String, List<Asset>> {
        val now = LocalDateTime.now()
        return listAssets(page, pageCount, true, "desc",
            contentType = contentType, fromDate = now.minusMonths(PreferenceManager.get(RECENT_ASSETS_MONTHS_BACK).toLong()), endDate = now)
            .map { it.shuffled() }
    }

    suspend fun similarAssets(page: Int, pageCount: Int, contentType: ContentType): Either<String, List<Asset>> {
        val now = LocalDateTime.now()
        val map: List<Either<String, List<Asset>>> = (0 until PreferenceManager.get(SIMILAR_ASSETS_YEARS_BACK)).toList().map {
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
        return Either.Right(map.flatMap { it.getOrElse { emptyList() } }.shuffled())
    }

    suspend fun listAssets(page: Int,
                           pageCount: Int,
                           random: Boolean = false,
                           order: String = "desc",
                           personIds: List<UUID> = emptyList(),
                           fromDate: LocalDateTime? = null,
                           endDate: LocalDateTime? = null,
                           contentType: ContentType,
                           albumIds: List<String> = emptyList()): Either<String, List<Asset>> {
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
        } else {
            search(searchRequest).map { it.assets.items }
        }
        return assetsResult.map { it.filter(excludeByTag()) }.map {
            val excludedAlbums = PreferenceManager.get(EXCLUDE_ASSETS_IN_ALBUM)
            if (excludedAlbums.isNotEmpty()) {
                val excludedAssets =
                    listAssetsFromAlbum(excludedAlbums.toList(), pageCount = pageCount).getOrElse { emptyList() }.map { it.id }.toSet()
                it.filterNot { asset -> excludedAssets.contains(asset.id) }
            } else {
                it
            }
        }
    }

    private fun excludeByTag() = { asset: Asset ->
        asset.tags?.none { t -> t.name == "exclude_immich_tv" } ?: true
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

    suspend fun listMemories(): Either<String, List<Memory>> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        return executeAPICall(200) { service.listMemories(today) }
    }

    suspend fun getMemory(id: String): Either<String, Memory> {
        return executeAPICall(200) { service.getMemory(id) }
    }

    suspend fun updateFavorite(id: String, isFavorite: Boolean): Either<String, Asset> {
        return executeAPICall(200) {
            service.updateAsset(id, UpdateAssetRequest(isFavorite))
        }
    }
}
