package nl.giejay.mediaslider.view

import android.app.ActivityManager
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.zeuskartik.mediaslider.R
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import nl.giejay.mediaslider.config.MediaSliderConfiguration

/**
 * Fullscreen video surface + ExoPlayer. Transport controls live in [MediaSliderView]'s
 * shared details/transport overlay in the slider shell, not inside this view.
 *
 * While buffering until the first frame, a full-screen thumbnail covers the black surface
 * with a spinner on top (same idea as the timeline mosaic muted previews).
 */
class ExoPlayerView @JvmOverloads constructor(context: Context, resourceId: Int, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private val playerView: PlayerView
    private val posterView: ImageView
    private val loadingView: ProgressBar
    private var player: ExoPlayer? = null
    private var awaitingFirstFrame = false

    init {
        LayoutInflater.from(context).inflate(resourceId, this, true)
        playerView = findViewById(R.id.video_view)
        posterView = findViewById(R.id.video_poster)
        loadingView = findViewById(R.id.video_loading)
    }

    @OptIn(UnstableApi::class)
    fun setupPlayer(
        config: MediaSliderConfiguration,
        renderersFactory: NextRenderersFactory,
        listener: ExoPlayerListener,
        onPlayerError: (ExoPlayer, Exception) -> Boolean
    ) {
        player = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(createLoadControl(config))
            .build()
        playerView.player = player
        if (!config.isVideoSoundEnable) player?.volume = 0f

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                // Spinner + poster only for the initial first-frame wait. Mid-stream
                // BUFFERING (seek/rebuffer) would otherwise flash the spinner constantly
                // during hold-scrub; PlayerView already has show_buffering="never".
                if (playbackState == Player.STATE_BUFFERING && awaitingFirstFrame) {
                    showPosterOverlay()
                } else if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                    if (!awaitingFirstFrame) {
                        loadingView.visibility = View.GONE
                    }
                }
                listener.onPlaybackStateChanged(playbackState)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listener.onIsPlayingChanged(isPlaying)
            }

            override fun onRenderedFirstFrame() {
                awaitingFirstFrame = false
                hidePosterOverlay()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // if not handled, let listener handle it
                if (!onPlayerError(player!!, error)) {
                    listener.onPlayerError(error)
                }
            }
        })
    }

    /**
     * Loads [thumbnailUrl] immediately and keeps it elevated until the first decoded frame.
     * Safe to call again when re-preparing or seeking back to the start of a page.
     */
    fun showLoadingPoster(thumbnailUrl: String?) {
        awaitingFirstFrame = true
        showPosterOverlay()
        if (thumbnailUrl.isNullOrBlank()) {
            posterView.setImageDrawable(null)
            return
        }
        Glide.with(posterView)
            .load(thumbnailUrl)
            .centerInside()
            .into(posterView)
    }

    fun clearPoster() {
        Glide.with(posterView).clear(posterView)
        posterView.setImageDrawable(null)
        hidePosterOverlay()
        awaitingFirstFrame = false
    }

    private fun showPosterOverlay() {
        posterView.visibility = View.VISIBLE
        loadingView.visibility = View.VISIBLE
        // Keep the poster above the surface while waiting for the first frame.
        posterView.elevation = 1f
        loadingView.elevation = 2f
    }

    private fun hidePosterOverlay() {
        posterView.visibility = View.GONE
        loadingView.visibility = View.GONE
    }

    fun releasePlayer() {
        clearPoster()
        player?.release()
        player = null
    }

    fun getPlayerView(): PlayerView {
        return playerView
    }

    fun getPlayer(): ExoPlayer? {
        return player
    }

    fun isReady(): Boolean {
        return player != null
    }

    @OptIn(UnstableApi::class)
    private fun createLoadControl(config: MediaSliderConfiguration): DefaultLoadControl {
        val builder = DefaultLoadControl.Builder()
        if (config.useLargeVideoBuffer) {
            builder
                .setBufferDurationsMs(
                    MIN_BUFFER_MS,
                    MAX_BUFFER_MS,
                    BUFFER_FOR_PLAYBACK_MS,
                    BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                )
                .setTargetBufferBytes(getSafetyBufferBytes())
                .setPrioritizeTimeOverSizeThresholds(true)
        } else {
            builder.setPrioritizeTimeOverSizeThresholds(false)
        }
        return builder.build()
    }

    private fun getSafetyBufferBytes(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return DEFAULT_SAFETY_BYTES
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalMemMb = memInfo.totalMem / (1024 * 1024)
        val safeMb = (totalMemMb * SAFETY_MEMORY_FRACTION).toInt().coerceIn(MIN_SAFETY_MB, MAX_SAFETY_MB)
        return safeMb * 1024 * 1024
    }

    private companion object {
        const val MIN_BUFFER_MS = 10_000
        const val MAX_BUFFER_MS = 300_000
        const val BUFFER_FOR_PLAYBACK_MS = 4_000
        const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 4_000

        const val SAFETY_MEMORY_FRACTION = 0.40
        const val MIN_SAFETY_MB = 384
        const val MAX_SAFETY_MB = 768
        const val DEFAULT_SAFETY_BYTES = 512 * 1024 * 1024
    }
}
