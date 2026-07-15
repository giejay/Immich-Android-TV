package nl.giejay.mediaslider.view

import android.app.ActivityManager
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.zeuskartik.mediaslider.R
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import nl.giejay.mediaslider.config.MediaSliderConfiguration

class ExoPlayerView @JvmOverloads constructor(context: Context, resourceId: Int, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private val playerView: PlayerView
    private var player: ExoPlayer? = null

    init {
        LayoutInflater.from(context).inflate(resourceId, this, true)
        playerView = findViewById(R.id.video_view)
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
                listener.onPlaybackStateChanged(playbackState)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listener.onIsPlayingChanged(isPlaying)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // if not handled, let listener handle it
                if (!onPlayerError(player!!, error)) {
                    listener.onPlayerError(error)
                }
            }
        })
    }

    fun releasePlayer() {
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

