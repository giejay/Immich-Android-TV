package nl.giejay.mediaslider.player

import android.content.Context
import android.media.MediaFormat
import android.os.Handler
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import timber.log.Timber

/**
 * Amlogic decoder drivers (e.g. Chromecast with Google TV, "sabrina") reject
 * MediaCodec.configure() with BAD_VALUE when the frame rate passed in the
 * MediaFormat is fractionally above 60 (ExoPlayer derives e.g. 60.000004 from
 * the sample count / duration of 60 fps recordings). Clamp it back to 60 so
 * hardware decoding of 4K60 content initializes instead of failing playback.
 */
@UnstableApi
class FrameRateClampingVideoRenderer(
    context: Context,
    mediaCodecSelector: MediaCodecSelector,
    allowedJoiningTimeMs: Long,
    enableDecoderFallback: Boolean,
    eventHandler: Handler?,
    eventListener: VideoRendererEventListener?,
    maxDroppedFramesToNotify: Int
) : MediaCodecVideoRenderer(
    context,
    mediaCodecSelector,
    allowedJoiningTimeMs,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    maxDroppedFramesToNotify
) {
    override fun getMediaFormat(
        format: Format,
        codecMimeType: String,
        codecMaxValues: CodecMaxValues,
        codecOperatingRate: Float,
        deviceNeedsNoPostProcessWorkaround: Boolean,
        tunnelingAudioSessionId: Int
    ): MediaFormat {
        val mediaFormat = super.getMediaFormat(
            format,
            codecMimeType,
            codecMaxValues,
            codecOperatingRate,
            deviceNeedsNoPostProcessWorkaround,
            tunnelingAudioSessionId
        )
        clampFloatKey(mediaFormat, MediaFormat.KEY_FRAME_RATE)
        clampFloatKey(mediaFormat, MediaFormat.KEY_OPERATING_RATE)
        return mediaFormat
    }

    private fun clampFloatKey(mediaFormat: MediaFormat, key: String) {
        if (!mediaFormat.containsKey(key)) return
        val value = try {
            mediaFormat.getFloat(key)
        } catch (e: ClassCastException) {
            return // stored as an integer, cannot carry a fractional overshoot
        }
        if (value > MAX_SANE_FRAME_RATE && value < MAX_SANE_FRAME_RATE + 1f) {
            Timber.i("Clamping $key from $value to $MAX_SANE_FRAME_RATE for Amlogic decoder")
            mediaFormat.setFloat(key, MAX_SANE_FRAME_RATE)
        }
    }

    private companion object {
        const val MAX_SANE_FRAME_RATE = 60f
    }
}
