package nl.giejay.android.tv.immich.homescreenchannels

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import arrow.core.getOrElse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.shared.prefs.CHANNEL_ALBUMS
import nl.giejay.android.tv.immich.shared.prefs.ContentType
import nl.giejay.android.tv.immich.shared.prefs.ENABLE_HOME_SCREEN_CHANNELS
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.util.Debouncer
import timber.log.Timber
import java.util.concurrent.TimeUnit

private const val MAX_PROGRAMS_PER_CHANNEL = 15
private const val DEBOUNCE_KEY = "home_screen_channels_sync"
private const val REQUEST_CHANNEL_BROWSABLE = 4201
private const val DEFAULT_CHANNEL_INTERNAL_ID = "default"
private const val ALBUM_CHANNEL_INTERNAL_ID_PREFIX = "album:"

private fun albumChannelInternalId(albumId: String) = "$ALBUM_CHANNEL_INTERNAL_ID_PREFIX$albumId"
private fun albumIdFromChannelInternalId(internalId: String) = internalId.removePrefix(ALBUM_CHANNEL_INTERNAL_ID_PREFIX)

/**
 * Publishes Immich content as Android TV home screen channels: one default "Recent photos"
 * channel, plus one channel per album the user picks in settings. A channel is a
 * [Channel]/row, a program is one asset/card - see
 * https://developer.android.com/training/tv/discovery/recommendations-channel, which requires a
 * program to be a single piece of content, so albums map to channels rather than to cards.
 *
 * The TvProvider itself (queried by the internalProviderId set at creation, [DEFAULT_CHANNEL_INTERNAL_ID]
 * / [albumChannelInternalId]) is the single source of truth for which channels exist - nothing here
 * trusts a locally-cached channel id, so a process kill between an insert and a would-be local
 * write can never orphan or duplicate a channel. [mutex] serializes every entry point so two
 * triggers (foreground, pref change, "Refresh now", the install receiver) can never race each
 * other into a duplicate create or a lost delete.
 */
object HomeScreenChannelManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var subscribed = false

    /** Wires pref-change triggers. Idempotent, call once from [ImmichApplication.onCreate]. */
    @Synchronized
    fun initSubscriptions() {
        if (subscribed) return
        subscribed = true
        PreferenceManager.subscribeMultiple(listOf(CHANNEL_ALBUMS, ENABLE_HOME_SCREEN_CHANNELS)) {
            scheduleRefresh("prefs_changed")
        }
    }

    /** Debounced background sync, safe to call from any thread/trigger. */
    fun scheduleRefresh(reason: String) {
        Debouncer.debounce(DEBOUNCE_KEY, Runnable {
            scope.launch {
                Timber.d("Syncing home screen channels ($reason)")
                syncAll(ImmichApplication.appContext!!)
            }
        }, 5, TimeUnit.SECONDS)
    }

    /**
     * Creates/updates/deletes every channel this app owns so they match current login state,
     * the enable toggle, and the selected albums. Safe to call concurrently - callers are
     * serialized on [mutex].
     */
    suspend fun syncAll(context: Context) = mutex.withLock {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return@withLock
        }
        if (!PreferenceManager.isLoggedId() || !PreferenceManager.get(ENABLE_HOME_SCREEN_CHANNELS)) {
            try {
                deleteAllChannels(context)
            } catch (e: Exception) {
                Timber.e(e, "Could not delete home screen channels")
            }
            return@withLock
        }
        try {
            syncDefaultChannel(context)
            syncAlbumChannels(context)
        } catch (e: Exception) {
            Timber.e(e, "Could not sync home screen channels")
        }
    }

    /**
     * Creates (if the TvProvider doesn't already have one for [albumId]) and requests approval
     * for one album's channel. Called from the foreground album picker rather than the debounced
     * background sync, since approval requires an [Activity] and must happen while the app is in
     * the foreground (platform requirement). The background sync only ever syncs *programs* for
     * an album channel that already exists - it never creates one, so it can never leave behind
     * a channel nobody was ever asked to approve.
     */
    suspend fun createAlbumChannelAndRequestApproval(activity: Activity, albumId: String, albumName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val channelId = mutex.withLock {
                withContext(Dispatchers.IO) {
                    val internalId = albumChannelInternalId(albumId)
                    findChannel(activity, internalId)?.id
                        ?: createChannel(activity, internalId, albumName, ChannelDeepLinks.forAlbum(albumId, albumName), requestBrowsable = false)
                }
            }
            requestChannelApproval(activity, channelId)
        } catch (e: Exception) {
            Timber.e(e, "Could not create/approve home screen channel for album $albumId")
            Toast.makeText(activity, activity.getString(R.string.home_screen_channel_error), Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun deleteAlbumChannel(context: Context, albumId: String) {
        try {
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    findChannel(context, albumChannelInternalId(albumId))?.let { deleteChannel(context, it.id) }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Could not delete home screen channel for album $albumId")
        }
    }

    private suspend fun syncDefaultChannel(context: Context) {
        val apiClient = ApiClient.getClient(ApiClientConfig.fromPrefs())
        // order=desc, not random - a stable order keeps this channel's card order from
        // churning (insert+delete) on every refresh
        val assets = apiClient.listAssets(
            page = 1,
            pageCount = MAX_PROGRAMS_PER_CHANNEL,
            random = false,
            order = "desc",
            contentType = ContentType.ALL
        ).fold({
            Timber.w("Could not load recent assets for the default home screen channel: $it")
            null
        }, { it.assets }) ?: return

        val channelId = findChannel(context, DEFAULT_CHANNEL_INTERNAL_ID)?.id
            ?: createChannel(
                context,
                internalProviderId = DEFAULT_CHANNEL_INTERNAL_ID,
                displayName = context.getString(R.string.channel_recent_photos),
                appLinkUri = ChannelDeepLinks.forHome(),
                requestBrowsable = true
            )

        syncProgramsForChannel(context, DEFAULT_CHANNEL_INTERNAL_ID, channelId, assets)
    }

    private suspend fun syncAlbumChannels(context: Context) {
        val selectedAlbumIds = PreferenceManager.get(CHANNEL_ALBUMS)
        val existingAlbumChannels = queryAlbumChannels(context)

        // channels for albums the user deselected since the last sync
        existingAlbumChannels
            .filterKeys { it !in selectedAlbumIds }
            .values
            .forEach { deleteChannel(context, it.id) }

        if (selectedAlbumIds.isEmpty()) {
            return
        }

        val apiClient = ApiClient.getClient(ApiClientConfig.fromPrefs())
        val albums = apiClient.listAlbums().getOrElse {
            Timber.w("Could not load albums for home screen channels: $it")
            return
        }

        selectedAlbumIds.forEach { albumId ->
            // only sync programs for a channel the foreground picker already created + had
            // approved - the background sync never creates one itself, see
            // [createAlbumChannelAndRequestApproval]
            val channel = existingAlbumChannels[albumId] ?: return@forEach
            val album = albums.find { it.id == albumId } ?: return@forEach
            val assets = apiClient.listAssets(
                page = 1,
                pageCount = MAX_PROGRAMS_PER_CHANNEL,
                random = false,
                order = "desc",
                contentType = ContentType.ALL,
                albumIds = listOf(albumId)
            ).getOrElse {
                Timber.w("Could not load assets for album channel $albumId: $it")
                return@forEach
            }.assets

            syncProgramsForChannel(context, albumChannelInternalId(albumId), channel.id, assets)
            // keep the channel name in sync with the album name, in case it was renamed
            if (channel.displayName != album.albumName) {
                val updated = Channel.Builder(channel).setDisplayName(album.albumName).build()
                context.contentResolver.update(TvContractCompat.buildChannelUri(channel.id), updated.toContentValues(), null, null)
            }
        }
    }

    private fun requestChannelApproval(activity: Activity, channelId: Long) {
        val intent = Intent(TvContractCompat.ACTION_REQUEST_CHANNEL_BROWSABLE)
        intent.putExtra(TvContractCompat.EXTRA_CHANNEL_ID, channelId)
        try {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQUEST_CHANNEL_BROWSABLE)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "Device does not support the channel approval dialog")
        }
    }

    private fun createChannel(context: Context, internalProviderId: String, displayName: String, appLinkUri: Uri, requestBrowsable: Boolean): Long {
        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(displayName)
            .setAppLinkIntentUri(appLinkUri)
            .setInternalProviderId(internalProviderId)
            .build()
        val channelUri = context.contentResolver.insert(TvContractCompat.Channels.CONTENT_URI, channel.toContentValues())
            ?: error("Could not insert home screen channel into TvProvider")
        val channelId = ContentUris.parseId(channelUri)

        // 80dp x 80dp opaque logo, displayed under a circular mask
        BitmapFactory.decodeResource(context.resources, R.drawable.icon_600px)?.let { logo ->
            ChannelLogoUtils.storeChannelLogo(context, channelId, logo)
        }

        if (requestBrowsable) {
            // only guaranteed to be auto-approved for the very first channel the app creates
            TvContractCompat.requestChannelBrowsable(context, channelId)
        }
        return channelId
    }

    /**
     * Every channel this app currently owns in the TvProvider. Deliberately queries with no
     * `selection` and filters client-side - at least one real device (Sony Bravia/Google TV)
     * throws `SecurityException: Selection not allowed for content://android.media.tv/channel`
     * for any non-null selection on this URI, even one scoped to this app's own rows.
     */
    private fun queryAllChannels(context: Context): List<Channel> {
        val channels = mutableListOf<Channel>()
        context.contentResolver.query(TvContractCompat.Channels.CONTENT_URI, null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                channels.add(Channel.fromCursor(cursor))
            }
        }
        return channels
    }

    private fun findChannel(context: Context, internalProviderId: String): Channel? =
        queryAllChannels(context).find { it.internalProviderId == internalProviderId }

    /** Every album channel this app currently owns in the TvProvider, keyed by album id. */
    private fun queryAlbumChannels(context: Context): Map<String, Channel> {
        return queryAllChannels(context)
            .mapNotNull { channel -> channel.internalProviderId?.let { it to channel } }
            .filter { (internalId, _) -> internalId.startsWith(ALBUM_CHANNEL_INTERNAL_ID_PREFIX) }
            .associate { (internalId, channel) -> albumIdFromChannelInternalId(internalId) to channel }
    }

    private fun deleteChannel(context: Context, channelId: Long) {
        context.contentResolver.delete(TvContractCompat.buildChannelUri(channelId), null, null)
    }

    /** Deletes every channel this app owns - not just ones this process remembers creating. */
    private fun deleteAllChannels(context: Context) {
        queryAllChannels(context).forEach { channel -> deleteChannel(context, channel.id) }
    }

    /**
     * Diffs [assets] against the programs the provider currently has for [channelId] - not just
     * our local state - so a card the user removed (browsable-disabled) or a whole channel the
     * user cleared out is respected rather than silently overwritten. Assets already present are
     * updated in place (poster art URL - and the API key embedded in it - title, and weight can
     * all change between syncs), not just left stale.
     */
    private fun syncProgramsForChannel(context: Context, channelInternalId: String, channelId: Long, assets: List<Asset>) {
        val resolver = context.contentResolver
        val existingProgramIdsByAssetId = mutableMapOf<String, Long>()
        resolver.query(TvContractCompat.buildPreviewProgramsUriForChannel(channelId), null, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val program = PreviewProgram.fromCursor(cursor)
                program.internalProviderId?.let { assetId -> existingProgramIdsByAssetId[assetId] = program.id }
            }
        }

        val excludedAssetIds = ChannelRegistryStore.excludedAssetIds(channelInternalId)
        val targetAssetIds = assets.map { it.id }.filterNot { excludedAssetIds.contains(it) }.toSet()

        existingProgramIdsByAssetId
            .filterKeys { it !in targetAssetIds }
            .values
            .forEach { programId -> resolver.delete(TvContractCompat.buildPreviewProgramUri(programId), null, null) }

        assets.forEachIndexed { index, asset ->
            if (asset.id !in targetAssetIds) {
                return@forEachIndexed
            }
            val posterArtUrl = ApiUtil.getThumbnailUrl(asset.id, "preview", includeApiKey = true) ?: return@forEachIndexed
            val program = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(TvContractCompat.PreviewPrograms.TYPE_CLIP)
                .setTitle(asset.originalFileName ?: asset.id)
                .setPosterArtUri(Uri.parse(posterArtUrl))
                .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                .setIntentUri(ChannelDeepLinks.forAsset(asset.id))
                .setInternalProviderId(asset.id)
                .setWeight(assets.size - index)
                .build()

            val existingProgramId = existingProgramIdsByAssetId[asset.id]
            if (existingProgramId != null) {
                resolver.update(TvContractCompat.buildPreviewProgramUri(existingProgramId), program.toContentValues(), null, null)
            } else {
                resolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, program.toContentValues())
            }
        }
    }
}
