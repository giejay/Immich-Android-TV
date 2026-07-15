package nl.giejay.android.tv.immich.shared.util

import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.mediaslider.model.MetaDataProvider
import nl.giejay.mediaslider.model.MetaDataType
import nl.giejay.mediaslider.model.StaticMetaDataProvider

/**
 * Single place to map an [Asset] onto slider [MetaDataType] values / providers.
 * Add new metadata fields here — not in [Asset.toSliderItem] and not in
 * [AssetDetailMetaDataProvider].
 */
object AssetMetaDataMapping {

    /** Fields the Immich viewer overlay can show for an asset. */
    private val SLIDER_FIELDS = listOf(
        MetaDataType.DATE,
        MetaDataType.CITY,
        MetaDataType.COUNTRY,
        MetaDataType.DESCRIPTION,
        MetaDataType.FILENAME,
        MetaDataType.PEOPLE,
        MetaDataType.FILEPATH,
        MetaDataType.CAMERA,
        MetaDataType.ALBUM_NAME
    )

    /**
     * Project a loaded/partial [Asset] to a display string for [field], without network.
     * Returns null when the asset does not carry that field yet.
     */
    fun valueOf(asset: Asset, field: MetaDataType): String? = when (field) {
        MetaDataType.DATE -> {
            val date = asset.exifInfo?.dateTimeOriginal
                ?: asset.fileCreatedAt
                ?: asset.fileModifiedAt
            date?.let { formatAssetDate(it) }
        }
        MetaDataType.CITY -> asset.exifInfo?.city
        MetaDataType.COUNTRY -> asset.exifInfo?.country
        MetaDataType.DESCRIPTION -> asset.exifInfo?.description
        MetaDataType.FILENAME -> asset.originalFileName
        MetaDataType.FILEPATH -> asset.originalPath
        MetaDataType.PEOPLE -> asset.people
            ?.mapNotNull { it.name }
            ?.filter { it.isNotBlank() }
            ?.joinToString(", ")
            ?.ifBlank { null }
        MetaDataType.CAMERA -> listOfNotNull(asset.exifInfo?.make, asset.exifInfo?.model)
            .joinToString(" ")
            .ifBlank { null }
        MetaDataType.ALBUM_NAME -> asset.albumName?.ifBlank { null }
        else -> null
    }

    /**
     * Build providers for [Asset.toSliderItem]: static when [valueOf] is known, else lazy
     * detail fetch ([AssetDetailMetaDataProvider]) or album list lookup.
     */
    fun providersFor(asset: Asset): Map<MetaDataType, MetaDataProvider> =
        SLIDER_FIELDS.associateWith { field ->
            val inline = valueOf(asset, field)
            when {
                inline != null -> StaticMetaDataProvider(inline)
                field == MetaDataType.ALBUM_NAME -> AlbumMetaDataProvider(asset.id)
                else -> AssetDetailMetaDataProvider(asset.id, field)
            }
        }
}
