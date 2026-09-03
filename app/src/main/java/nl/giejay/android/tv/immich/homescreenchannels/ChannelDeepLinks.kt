package nl.giejay.android.tv.immich.homescreenchannels

import android.net.Uri

private const val SCHEME = "immichtv"

sealed class DeepLinkTarget {
    data class Asset(val assetId: String) : DeepLinkTarget()
    data class Album(val albumId: String, val albumName: String) : DeepLinkTarget()
    data object Home : DeepLinkTarget()
    data object None : DeepLinkTarget()
}

object ChannelDeepLinks {

    fun forAsset(assetId: String): Uri =
        Uri.parse("$SCHEME://asset/$assetId")

    fun forAlbum(albumId: String, albumName: String): Uri =
        Uri.parse("$SCHEME://album/$albumId").buildUpon()
            .appendQueryParameter("name", albumName)
            .build()

    fun forHome(): Uri = Uri.parse("$SCHEME://home")

    fun parse(uri: Uri?): DeepLinkTarget {
        if (uri == null || uri.scheme != SCHEME) {
            return DeepLinkTarget.None
        }
        return when (uri.host) {
            "asset" -> uri.lastPathSegment?.let { DeepLinkTarget.Asset(it) } ?: DeepLinkTarget.None
            "album" -> uri.lastPathSegment?.let {
                DeepLinkTarget.Album(it, uri.getQueryParameter("name") ?: "")
            } ?: DeepLinkTarget.None

            "home" -> DeepLinkTarget.Home
            else -> DeepLinkTarget.None
        }
    }
}
