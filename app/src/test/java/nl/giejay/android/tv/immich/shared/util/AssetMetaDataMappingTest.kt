package nl.giejay.android.tv.immich.shared.util

import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.AssetExifInfo
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.model.StaticMetaDataProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetMetaDataMappingTest {

    @Test
    fun `valueOf projects exif city and skips blank camera`() {
        val asset = Asset(
            id = "1",
            type = "IMAGE",
            deviceAssetId = null,
            exifInfo = AssetExifInfo(
                description = "desc",
                orientation = 1,
                exifImageWidth = 100,
                exifImageHeight = 200,
                city = "Oslo",
                country = "Norway",
                dateTimeOriginal = null,
                make = null,
                model = null
            ),
            fileCreatedAt = null,
            fileModifiedAt = null,
            albumName = "Trip",
            people = null,
            tags = null,
            originalPath = "/a",
            originalFileName = "a.jpg",
            isFavorite = false
        )

        assertEquals("Oslo", AssetMetaDataMapping.valueOf(asset, MetaDataType.CITY))
        assertEquals("Trip", AssetMetaDataMapping.valueOf(asset, MetaDataType.ALBUM_NAME))
        assertNull(AssetMetaDataMapping.valueOf(asset, MetaDataType.CAMERA))
    }

    @Test
    fun `providersFor uses static when inline value exists else detail provider`() {
        val asset = Asset(
            id = "2",
            type = "IMAGE",
            deviceAssetId = null,
            exifInfo = AssetExifInfo(
                description = null,
                orientation = 1,
                exifImageWidth = null,
                exifImageHeight = null,
                city = "Bergen",
                country = null,
                dateTimeOriginal = null,
                make = null,
                model = null
            ),
            fileCreatedAt = null,
            fileModifiedAt = null,
            albumName = null,
            people = null,
            tags = null,
            originalPath = null,
            originalFileName = null,
            isFavorite = false
        )

        val providers = AssetMetaDataMapping.providersFor(asset)
        assertTrue(providers[MetaDataType.CITY] is StaticMetaDataProvider)
        assertTrue(providers[MetaDataType.COUNTRY] is AssetDetailMetaDataProvider)
        assertTrue(providers[MetaDataType.ALBUM_NAME] is AlbumMetaDataProvider)
    }
}
