package nl.giejay.android.tv.immich.api

import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager.hostName

object ApiUtil {

    fun getThumbnailUrl(assetId: String?, format: String): String? {
        return assetId?.let {
            "${hostName()}/api/asset/thumbnail/${it}?format=${format}"
        }
    }

    fun getFileUrl(assetId: String?): String? {
        return assetId?.let {
            "${hostName()}/api/asset/file/${it}"
        }
    }
}