package nl.giejay.android.tv.immich.api

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.AlbumDetails
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.Bucket
import nl.giejay.android.tv.immich.api.model.Folder
import nl.giejay.android.tv.immich.api.model.Person
import nl.giejay.android.tv.immich.api.model.SearchRequest
import nl.giejay.android.tv.immich.api.service.ApiService
import nl.giejay.android.tv.immich.api.util.ApiUtil.executeAPICall
import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.RECENT_ASSETS_MONTHS_BACK
import nl.giejay.android.tv.immich.shared.prefs.SIMILAR_ASSETS_PERIOD_DAYS
import nl.giejay.android.tv.immich.shared.prefs.SIMILAR_ASSETS_YEARS_BACK
import nl.giejay.android.tv.immich.shared.util.Utils.pmap
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ApiClientConfig(
    val hostName: String,
    val apiKey: String,
    val disableSslVerification: Boolean,
    val debugMode: Boolean
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

        val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME;
    }

    private val retrofit = Retrofit.Builder()
        .client(ApiClientFactory.getClient(config.disableSslVerification, config.apiKey, config.debugMode))
        .addConverterFactory(GsonConverterFactory.create())
        .baseUrl("${config.hostName}/api/")
        .build()

    private val service: ApiService = retrofit.create(ApiService::class.java)

    suspend fun listAlbums(): Either<String, List<Album>> {
        return executeAPICall(200) { service.listAlbums() }.flatMap { albums ->
            return executeAPICall(200) { service.listAlbums(true) }.map { sharedAlbums ->
                albums + sharedAlbums
            }
        }
    }

    suspend fun listPeople(): Either<String, List<Person>> {
        return executeAPICall(200) { service.listPeople() }.map { response -> response.people.filter { !it.name.isNullOrBlank() } }
    }

    suspend fun listAssetsFromAlbum(albumId: String): Either<String, AlbumDetails> {
        return executeAPICall(200) {
            val response = service.listAssetsFromAlbum(albumId)
            val album = response.body()
            val assets = album!!.assets.filter(excludeByTag())
                .map { Asset(it.id, it.type, it.deviceAssetId, it.exifInfo, it.fileModifiedAt, album.albumName, it.people, it.tags) }
            val updatedAlbum = AlbumDetails(album.albumName, album.description, album.id, album.albumThumbnailAssetId, assets)
            Response.success(updatedAlbum)
        }
    }

    suspend fun recentAssets(page: Int, pageCount: Int, includeVideos: Boolean): Either<String, List<Asset>> {
        val now = LocalDateTime.now()
        return listAssets(page, pageCount, true, "desc",
            includeVideos = includeVideos, fromDate = now.minusMonths(PreferenceManager.get(RECENT_ASSETS_MONTHS_BACK).toLong()), endDate = now)
            .map { it.shuffled() }
    }

    suspend fun similarAssets(page: Int, pageCount: Int, includeVideos: Boolean): Either<String, List<Asset>> {
        val now = LocalDateTime.now()
        val map: List<Either<String, List<Asset>>> = (0 until PreferenceManager.get(SIMILAR_ASSETS_YEARS_BACK)).toList().map {
            listAssets(page,
                pageCount,
                true,
                "desc",
                includeVideos = includeVideos,
                fromDate = now.minusDays((PreferenceManager.get(SIMILAR_ASSETS_PERIOD_DAYS) / 2).toLong()).minusYears(it.toLong()),
                endDate = now.plusDays((PreferenceManager.get(SIMILAR_ASSETS_PERIOD_DAYS) / 2).toLong()).minusYears(it.toLong()))
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
                           includeVideos: Boolean = true,
                           fromDate: LocalDateTime? = null,
                           endDate: LocalDateTime? = null): Either<String, List<Asset>> {
        val searchRequest = SearchRequest(page,
            pageCount,
            order,
            if (includeVideos) null else "IMAGE",
            personIds,
            endDate?.format(dateTimeFormatter),
            fromDate?.format(dateTimeFormatter))
        return (if (random) {
            executeAPICall(200) { service.randomAssets(searchRequest) }
        } else {
            executeAPICall(200) { service.listAssets(searchRequest) }.map { res -> res.assets.items }
        }).map { it.filter(excludeByTag()) }
    }

    private fun excludeByTag() = { asset: Asset ->
        asset.tags?.none { t -> t.name == "exclude_immich_tv" } ?: true
    }

    suspend fun listBuckets(albumId: String, order: PhotosOrder): Either<String, List<Bucket>> {
        return executeAPICall(200) {
            service.listBuckets(albumId = albumId, order = if (order == PhotosOrder.OLDEST_NEWEST) "asc" else "desc")
        }
    }

    suspend fun getAssetsForBucket(albumId: String, bucket: String, order: PhotosOrder): Either<String, List<Asset>> {
        val response = executeAPICall(200) {
            service.getBucketV2(albumId = albumId, timeBucket = bucket, order = if (order == PhotosOrder.OLDEST_NEWEST) "asc" else "desc")
        }.map {
            it.id.pmap { t -> service.getAsset(t).body()!! }.toList()
        }
        if(response.isLeft()){
            return executeAPICall(200) {
                service.getBucket(albumId = albumId, timeBucket = bucket, order = if (order == PhotosOrder.OLDEST_NEWEST) "asc" else "desc")
            }
        }
        return response
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
}

