package nl.giejay.android.tv.immich.timeline

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_FORCE_ORIGINAL_VIDEO
import timber.log.Timber

/**
 * Immich-web-style muted video preview for timeline mosaic cells.
 *
 * Starts as soon as a video cell is focused. The Glide thumbnail stays elevated above the
 * [PlayerView] until [Player.Listener.onRenderedFirstFrame], then the video is brought
 * on top — no grey flash, and the TextureView still gets a live surface to decode into.
 *
 * While playing, the cell's duration badge counts down from the full length to 0
 * (Immich-style), then resets to the full duration when focus leaves.
 */
@SuppressLint("UnsafeOptInUsageError")
class TimelineFocusVideoPreview(context: Context) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var host: ViewGroup? = null
    private var poster: ImageView? = null
    private var durationText: TextView? = null
    private var totalDurationSeconds: Long = 0L
    private var currentAssetId: String? = null

    private val dataSourceFactory: DefaultHttpDataSource.Factory by lazy {
        DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("x-api-key" to PreferenceManager.get(API_KEY)))
    }

    private val countdownTicker = object : Runnable {
        override fun run() {
            val exo = player
            val text = durationText
            if (exo != null && text != null) {
                if (exo.isPlaying) {
                    val remaining = totalDurationSeconds - exo.currentPosition / 1000
                    text.text = TimelineVideoDuration.format(remaining)
                }
                handler.postDelayed(this, COUNTDOWN_INTERVAL_MS)
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            revealVideoOverPoster()
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.w(error, "Timeline focus video preview failed for %s", currentAssetId)
            stop()
        }
    }

    fun onCellFocus(cell: TimelineMosaicCell, cellView: View) {
        if (cell.asset.isImage) {
            stop()
            return
        }
        if (currentAssetId == cell.asset.id && playerView != null) {
            player?.playWhenReady = true
            return
        }
        val hostView = cellView as? ViewGroup ?: return
        if (!hostView.isAttachedToWindow) return
        start(cell, hostView)
    }

    fun onCellBlur(cell: TimelineMosaicCell) {
        if (currentAssetId == cell.asset.id) {
            stop()
        }
    }

    /** Cell recycled / row rebound while preview might still be attached. */
    fun onHostDetached(cellView: View) {
        if (host === cellView) {
            stop()
        }
    }

    fun pause() {
        player?.playWhenReady = false
    }

    fun release() {
        stop()
        player?.removeListener(playerListener)
        player?.release()
        player = null
    }

    private fun start(cell: TimelineMosaicCell, hostView: ViewGroup) {
        val url = ApiUtil.getFileUrl(
            cell.asset.id,
            "VIDEO",
            PreferenceManager.get(SLIDER_FORCE_ORIGINAL_VIDEO)
        ) ?: return

        detachViewOnly()

        val image = hostView.findViewById<ImageView>(R.id.timeline_mosaic_image) ?: return
        val exo = ensurePlayer()
        val preview = LayoutInflater.from(hostView.context)
            .inflate(R.layout.timeline_mosaic_video_preview, hostView, false) as PlayerView
        preview.setShutterBackgroundColor(Color.TRANSPARENT)
        preview.setBackgroundColor(Color.TRANSPARENT)
        preview.visibility = View.VISIBLE
        // Decode under the poster until the first frame is ready.
        preview.elevation = 0f
        image.elevation = 4f
        preview.player = exo
        // Insert behind the ImageView so z-order is ImageView (poster) on top.
        hostView.addView(preview, 0)

        playerView = preview
        host = hostView
        poster = image
        durationText = hostView.findViewById(R.id.timeline_mosaic_video_duration)
        totalDurationSeconds = TimelineVideoDuration.parseSeconds(cell.asset.duration) ?: 0L
        currentAssetId = cell.asset.id

        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
        exo.setMediaSource(mediaSource, /* startPositionMs = */ 0L)
        exo.prepare()
        exo.playWhenReady = true

        handler.removeCallbacks(countdownTicker)
        handler.post(countdownTicker)
    }

    private fun revealVideoOverPoster() {
        val preview = playerView ?: return
        val image = poster
        preview.elevation = 4f
        image?.elevation = 0f
        // Also ensure draw order in case elevation is ignored on some TVs.
        preview.bringToFront()
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        return ExoPlayer.Builder(appContext).build().also { exo ->
            exo.volume = 0f
            exo.repeatMode = Player.REPEAT_MODE_ONE
            exo.addListener(playerListener)
            player = exo
        }
    }

    private fun stop() {
        handler.removeCallbacks(countdownTicker)
        player?.run {
            playWhenReady = false
            stop()
            clearMediaItems()
        }
        poster?.elevation = 0f
        // Restore the full duration for the next time this cell is (un)focused.
        durationText?.text = TimelineVideoDuration.format(totalDurationSeconds)
        detachViewOnly()
        currentAssetId = null
    }

    private fun detachViewOnly() {
        playerView?.player = null
        playerView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        playerView = null
        host = null
        poster = null
        durationText = null
    }

    companion object {
        private const val COUNTDOWN_INTERVAL_MS = 250L
    }
}
