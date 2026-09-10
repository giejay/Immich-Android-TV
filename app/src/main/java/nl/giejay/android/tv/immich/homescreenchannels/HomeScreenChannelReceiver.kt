package nl.giejay.android.tv.immich.homescreenchannels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Handles the two broadcasts the platform sends this app about home screen channels:
 * - INITIALIZE_PROGRAMS: sent once after install, before the user has opened the app. Runs the
 *   same [HomeScreenChannelManager.syncAll] as every other trigger (so it also honours the
 *   enable pref and requires the user already be logged in - on a genuinely fresh install
 *   neither is true yet, so this is mostly useful when the launcher re-sends the broadcast
 *   later, e.g. after an app update). Album channels aren't created here regardless, since
 *   approving a non-default channel requires the app in the foreground (see
 *   [HomeScreenChannelManager.createAlbumChannelAndRequestApproval]).
 * - PREVIEW_PROGRAM_BROWSABLE_DISABLED: sent when the user removes one card from the launcher's
 *   own UI. The platform contract requires deleting the row and never reinserting it.
 */
class HomeScreenChannelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TvContractCompat.ACTION_INITIALIZE_PROGRAMS -> handleInitializePrograms(context)
            TvContractCompat.ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED -> handleProgramBrowsableDisabled(context, intent)
        }
    }

    private fun handleInitializePrograms(context: Context) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                HomeScreenChannelManager.syncAll(appContext)
            } catch (e: Exception) {
                Timber.e(e, "Could not initialize home screen channels")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleProgramBrowsableDisabled(context: Context, intent: Intent) {
        val programId = intent.getLongExtra(TvContractCompat.EXTRA_PREVIEW_PROGRAM_ID, -1L)
        if (programId < 0) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                removeDisabledProgram(appContext, programId)
            } catch (e: Exception) {
                Timber.e(e, "Could not process disabled preview program $programId")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun removeDisabledProgram(context: Context, programId: Long) {
        val resolver = context.contentResolver
        val programUri = TvContractCompat.buildPreviewProgramUri(programId)
        resolver.query(programUri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use
            val program = PreviewProgram.fromCursor(cursor)
            // the system is the only legitimate sender of this broadcast, but the receiver is
            // necessarily exported - a spoofed broadcast can at most make us exclude one of our
            // own already-hidden rows, never anyone else's data, but check the row actually is
            // hidden before acting on it anyway
            if (program.isBrowsable) return@use
            val assetId = program.internalProviderId ?: return@use
            val channelInternalId = resolver.query(TvContractCompat.buildChannelUri(program.channelId), null, null, null, null)
                ?.use { channelCursor -> if (channelCursor.moveToFirst()) Channel.fromCursor(channelCursor).internalProviderId else null }
                ?: return@use
            ChannelRegistryStore.addExcludedAssetId(channelInternalId, assetId)
        }
        resolver.delete(programUri, null, null)
    }
}
