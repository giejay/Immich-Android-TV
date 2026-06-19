package nl.giejay.mediaslider.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.zeuskartik.mediaslider.R
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import nl.giejay.mediaslider.config.MediaSliderConfiguration

class ExoPlayerView @JvmOverloads constructor(context: Context, resourceId: Int,  attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private val playerView: PlayerView
    private val playBtn: ImageButton
    private val muteBtn: ImageButton
    private val forwardBtn: ImageButton
    private val rewindBtn: ImageButton
    private val slideshowBtn: ImageButton
    private var player: ExoPlayer? = null

    init {
        LayoutInflater.from(context).inflate(resourceId, this, true)
        playerView = findViewById(R.id.video_view)
        playBtn = playerView.findViewById(R.id.exo_pause)
        muteBtn = playerView.findViewById(R.id.exo_mute)
        forwardBtn = playerView.findViewById(R.id.exo_forward)
        rewindBtn = playerView.findViewById(R.id.exo_rewind)
        slideshowBtn = playerView.findViewById(R.id.exo_slideshow)
    }

    @OptIn(UnstableApi::class)
    fun setupPlayer(
        config: MediaSliderConfiguration,
        renderersFactory: NextRenderersFactory,
        listener: ExoPlayerListener,
        onButtonClick: (Int) -> Unit,
        onPlayerError: (ExoPlayer, Exception) -> Boolean
    ) {
        player = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(DefaultLoadControl.Builder()
                .setPrioritizeTimeOverSizeThresholds(false)
                .build()
            ).build()
        playerView.player = player
        if (!config.isVideoSoundEnable) player?.volume = 0f

        playBtn.setOnClickListener {
            onButtonClick(R.id.exo_pause)
            player?.let {
                if (it.isPlaying) it.pause()
                else {
                    if (it.currentPosition >= it.contentDuration) it.seekToDefaultPosition()
                    it.play()
                }
            }
        }
        muteBtn.setOnClickListener {
            player?.let {
                if (it.volume == 0f) {
                    it.volume = 1f
                    muteBtn.setImageResource(R.drawable.unmute_icon)
                    config.isVideoSoundEnable = true
                } else {
                    it.volume = 0f
                    muteBtn.setImageResource(R.drawable.mute_icon)
                    config.isVideoSoundEnable = false
                }
            }
        }
        forwardBtn.setOnClickListener { onButtonClick(R.id.exo_forward) }
        rewindBtn.setOnClickListener { onButtonClick(R.id.exo_rewind) }
        slideshowBtn.setOnClickListener { onButtonClick(R.id.exo_slideshow) }
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                listener.onPlaybackStateChanged(playbackState)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listener.onIsPlayingChanged(isPlaying)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // if not handled, let listener handle it
                if(!onPlayerError(player!!, error)){
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
}
