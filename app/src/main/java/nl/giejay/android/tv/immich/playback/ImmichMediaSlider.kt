package nl.giejay.android.tv.immich.playback

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.View
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.gms.cast.tv.CastReceiverContext
import com.zeuskartik.mediaslider.MediaSliderFragment
import nl.giejay.android.tv.immich.shared.db.LocalStorage
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import timber.log.Timber

class ImmichMediaSlider : MediaSliderFragment() {
    private lateinit var mediaSession: MediaSessionCompat

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Timber.i("Loading ${this.javaClass.simpleName}")

        val bundle = ImmichMediaSliderArgs.fromBundle(requireArguments())

        if (LocalStorage.mediaSliderItems.isNullOrEmpty()) {
            Timber.i("No items to play for photoslider")
            Toast.makeText(requireContext(), "No items to play", Toast.LENGTH_SHORT).show()
            findNavController().navigate(
                ImmichMediaSliderDirections.photoSliderToAlbumDetailsFragment(
                    bundle.albumId
                )
            )
            return
        }

        setDefaultExoFactory(
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("x-api-key" to PreferenceManager.apiKey()))
        )

        loadMediaSliderView(bundle.config, LocalStorage.mediaSliderItems)

        createMediaSession()
    }

    private fun createMediaSession() {
        mediaSession = MediaSessionCompat(requireContext(), "ImmichTV")
        CastReceiverContext.getInstance().mediaManager.setSessionCompatToken(
            mediaSession.sessionToken
        )

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "title")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "subtitle")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, "icon")
            .build()

        val playbackState = PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PLAYING,
                0,
                1f,
                System.currentTimeMillis()
            )
            .build()

        mediaSession.setMetadata(metadata)
        mediaSession.setPlaybackState(playbackState)
        mediaSession.setCallback(MyMediaSessionCallback(mediaSession))
        mediaSession.isActive = true
    }

    class MyMediaSessionCallback(val mediaSessionCompat: MediaSessionCompat) : MediaSessionCompat.Callback() {

        override fun onCommand(command: String?, extras: Bundle?, cb: ResultReceiver?) {
            super.onCommand(command, extras, cb)
        }

        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            return super.onMediaButtonEvent(mediaButtonEvent)
        }

        override fun onPrepareFromUri(uri: Uri?, extras: Bundle?) {
            super.onPrepareFromUri(uri, extras)
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            super.onPlayFromMediaId(mediaId, extras)
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            super.onPlayFromSearch(query, extras)
        }

        override fun onPlayFromUri(uri: Uri?, extras: Bundle?) {
            super.onPlayFromUri(uri, extras)
        }

        override fun onSkipToQueueItem(id: Long) {
            super.onSkipToQueueItem(id)
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            super.onCustomAction(action, extras)
        }

        override fun onAddQueueItem(description: MediaDescriptionCompat?) {
            super.onAddQueueItem(description)
        }

        override fun onAddQueueItem(description: MediaDescriptionCompat?, index: Int) {
            super.onAddQueueItem(description, index)
        }

        override fun onPause() {
            Timber.i("Pause")
        }

        override fun onPlay() {
            Timber.i("play")
        }

        override fun onSeekTo(pos: Long) {
            mediaSessionCompat.setPlaybackState(PlaybackStateCompat.Builder()
                .setState(
                    PlaybackStateCompat.STATE_PLAYING,
                    pos,
                    1f,
                    System.currentTimeMillis()
                )
                .build())
            Timber.i("seek to")
        }
    }

}