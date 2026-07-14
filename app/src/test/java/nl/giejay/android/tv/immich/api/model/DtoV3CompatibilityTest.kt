package nl.giejay.android.tv.immich.api.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DtoV3CompatibilityTest {

    private val gson = Gson()

    @Test
    fun `parses v3-shaped Album JSON without owner or ownerId`() {
        val json = """
            {
                "albumName": "My Album",
                "description": "A test album",
                "id": "album-1",
                "albumThumbnailAssetId": "asset-1",
                "updatedAt": "2024-01-01T00:00:00.000Z",
                "endDate": null,
                "assetCount": 5,
                "albumUsers": [
                    { "user": { "id": "user-1", "name": "Owner" }, "role": "editor" }
                ]
            }
        """.trimIndent()

        val album = gson.fromJson(json, Album::class.java)

        assertNotNull(album)
        assertEquals("My Album", album.albumName)
        assertEquals("album-1", album.id)
        assertEquals(5, album.assetCount)
    }

    @Test
    fun `parses v3-shaped Asset JSON missing deviceAssetId as null`() {
        val json = """
            {
                "id": "asset-1",
                "type": "IMAGE",
                "exifInfo": null,
                "fileModifiedAt": null,
                "albumName": null,
                "people": null,
                "tags": null,
                "originalPath": null,
                "originalFileName": "photo.jpg"
            }
        """.trimIndent()

        val asset = gson.fromJson(json, Asset::class.java)

        assertNotNull(asset)
        assertNull(asset.deviceAssetId)
    }

    @Test
    fun `parses SearchAssetResponseDto JSON missing deprecated total as null`() {
        val json = """
            {
                "count": 1,
                "items": [],
                "nextPage": null
            }
        """.trimIndent()

        val response = gson.fromJson(json, SearchAssetResponseDto::class.java)

        assertNotNull(response)
        assertNull(response.total)
    }

    @Test
    fun `parses full v3-shaped SearchResponse JSON without throwing`() {
        val json = """
            {
                "albums": {
                    "total": 1,
                    "count": 1,
                    "items": [
                        {
                            "albumName": "My Album",
                            "description": "A test album",
                            "id": "album-1",
                            "albumThumbnailAssetId": null,
                            "updatedAt": "2024-01-01T00:00:00.000Z",
                            "endDate": null,
                            "assetCount": 1,
                            "albumUsers": []
                        }
                    ]
                },
                "assets": {
                    "count": 1,
                    "items": [
                        {
                            "id": "asset-1",
                            "type": "IMAGE",
                            "exifInfo": null,
                            "fileModifiedAt": null,
                            "albumName": null,
                            "people": null,
                            "tags": null,
                            "originalPath": null,
                            "originalFileName": "photo.jpg"
                        }
                    ],
                    "nextPage": null
                }
            }
        """.trimIndent()

        val response = gson.fromJson(json, SearchResponse::class.java)

        assertNotNull(response)
        assertEquals(1, response.albums.items.size)
        assertEquals(1, response.assets.items.size)
        assertNull(response.assets.total)
    }
}
