//package nl.giejay.android.tv.immich.album
//
//import android.os.Bundle
//import arrow.core.Either
//import kotlinx.coroutines.ExperimentalCoroutinesApi
//import kotlinx.coroutines.test.runTest
//import nl.giejay.android.tv.immich.api.ApiClient
//import nl.giejay.android.tv.immich.api.model.Asset
//import nl.giejay.android.tv.immich.shared.prefs.ContentType
//import nl.giejay.android.tv.immich.shared.prefs.EnumByTitlePref
//import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder
//import org.junit.Assert.*
//import org.junit.Before
//import org.junit.Test
//import org.mockito.Mockito.*
//
//@OptIn(ExperimentalCoroutinesApi::class)
//class AlbumDetailsFragmentTest {
//    private lateinit var fragment: AlbumDetailsFragment
//    private lateinit var apiClient: ApiClient
//
//    @Before
//    fun setUp() {
//        fragment = AlbumDetailsFragment()
//        apiClient = mock(ApiClient::class.java)
//        fragment.apiClient = apiClient
//        fragment.currentSort = PhotosOrder.NEWEST_OLDEST
//        fragment.currentFilter = ContentType.ALL
//        fragment.albumId = "album1"
//        fragment.albumName = "Test Album"
//    }
//
//    @Test
//    fun `loadItems returns assets for valid bucket`() = runTest {
//        val asset = Asset(
//            id = "1",
//            type = "image",
//            albumName = "Test Album",
//            fileModifiedAt = java.util.Date(1672531200000), // 2023-01-01T00:00:00Z as Date
//            originalPath = "/dummy/path.jpg",
//            exifInfo = null,
//            people = emptyList(),
//            tags = emptyList(),
//            deviceAssetId = "device1",
//            originalFileName = "filename",
//        )
//        fragment.pageToBucket = mapOf(1 to "bucket1")
//        `when`(apiClient.getAssetsForBucket("album1", "bucket1", PhotosOrder.NEWEST_OLDEST))
//            .thenReturn(Either.Right(listOf(asset)))
//
//        val result = fragment.loadItems(apiClient, 1, 10)
//        assertTrue(result.isRight())
//        assertEquals("Test Album", result.orNull()?.firstOrNull()?.albumName)
//    }
//
//    @Test
//    fun `loadItems returns empty list for invalid bucket`() = runTest {
//        fragment.pageToBucket = mapOf(1 to "bucket1")
//        val result = fragment.loadItems(apiClient, 2, 10)
//        assertTrue(result.isRight())
//        assertTrue(result.orNull()?.isEmpty() == true)
//    }
//
//    @Test
//    fun `allPagesLoaded returns true when no more pages`() {
//        fragment.pageToBucket = null
//        assertTrue(fragment.allPagesLoaded(emptyList()))
//    }
//
//    @Test
//    fun `onCreate initializes fragment correctly`() {
//        val fragment = AlbumDetailsFragment()
//        val args = Bundle().apply {
//            putString("albumId", "album1")
//            putString("albumName", "Test Album")
//        }
//        fragment.arguments = args
//        fragment.onCreate(null)
//        assertEquals("album1", fragment.albumId)
//        assertEquals("Test Album", fragment.albumName)
//    }
//}
