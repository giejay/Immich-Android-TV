package nl.giejay.mediaslider.view

interface ExoPlayerListener {

    fun onPlaybackStateChanged(playbackState: Int)

    fun onIsPlayingChanged(isPlaying: Boolean)

    fun onPlayerError(error: androidx.media3.common.PlaybackException)
}