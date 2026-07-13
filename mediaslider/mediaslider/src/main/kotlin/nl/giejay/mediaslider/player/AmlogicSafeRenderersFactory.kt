package nl.giejay.mediaslider.player

import android.content.Context
import android.os.Handler
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import timber.log.Timber

/**
 * Same renderers as [NextRenderersFactory] (hardware MediaCodec first, ffmpeg
 * software decoders as extension), but the stock hardware video renderer is
 * swapped for [FrameRateClampingVideoRenderer] to survive the Amlogic
 * "frame rate fractionally above 60 => BAD_VALUE" driver quirk.
 */
@UnstableApi
class AmlogicSafeRenderersFactory(context: Context) : NextRenderersFactory(context) {

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        super.buildVideoRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            allowedVideoJoiningTimeMs,
            out
        )
        // Replace the exact stock renderer (subclasses like the ffmpeg one are untouched)
        val index = out.indexOfFirst { it.javaClass == MediaCodecVideoRenderer::class.java }
        if (index == -1) {
            Timber.w("Stock MediaCodecVideoRenderer not found, frame rate clamp inactive")
            return
        }
        out[index] = FrameRateClampingVideoRenderer(
            context,
            mediaCodecSelector,
            allowedVideoJoiningTimeMs,
            enableDecoderFallback,
            eventHandler,
            eventListener,
            MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY
        )
    }
}
