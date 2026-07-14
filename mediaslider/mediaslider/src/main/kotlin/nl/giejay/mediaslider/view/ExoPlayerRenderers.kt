package nl.giejay.mediaslider.view

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import java.util.ArrayList

/**
 * Media3 renderers for Immich videos.
 *
 * Immich often remuxes AVI (and similar) into an mp4 playback stream with [audio/mpeg].
 * MediaCodec frequently claims support then fails; FFmpeg handles those streams. Prefer
 * FFmpeg for audio only so ordinary H.264/HEVC video still uses hardware decode.
 */
@OptIn(UnstableApi::class)
private class ImmichRenderersFactory(context: Context) : NextRenderersFactory(context) {
    init {
        // Register FFmpeg video as a non-preferred fallback for uncommon codecs.
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
        setEnableDecoderFallback(true)
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        super.buildAudioRenderers(
            context,
            EXTENSION_RENDERER_MODE_PREFER,
            mediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out
        )
    }
}

@OptIn(UnstableApi::class)
fun createVideoRenderersFactory(context: Context): DefaultRenderersFactory =
    ImmichRenderersFactory(context)
