package nl.giejay.mediaslider.player

import android.app.ActivityManager
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import nl.giejay.mediaslider.config.MediaSliderConfiguration

private const val MIN_BUFFER_MS = 10_000
private const val MAX_BUFFER_MS = 300_000
private const val BUFFER_FOR_PLAYBACK_MS = 4_000
private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 4_000

private const val SAFETY_MEMORY_FRACTION = 0.40
private const val MIN_SAFETY_MB = 384
private const val MAX_SAFETY_MB = 768
private const val DEFAULT_SAFETY_BYTES = 512 * 1024 * 1024

/** Buffering policy shared by every slider player: a large buffer only when configured, sized to the device memory. */
@OptIn(UnstableApi::class)
fun createVideoLoadControl(context: Context, config: MediaSliderConfiguration): DefaultLoadControl {
    val builder = DefaultLoadControl.Builder()
    if (config.useLargeVideoBuffer) {
        builder
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setTargetBufferBytes(safetyBufferBytes(context))
            .setPrioritizeTimeOverSizeThresholds(true)
    } else {
        builder.setPrioritizeTimeOverSizeThresholds(false)
    }
    return builder.build()
}

private fun safetyBufferBytes(context: Context): Int {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return DEFAULT_SAFETY_BYTES
    val memInfo = ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)
    val totalMemMb = memInfo.totalMem / (1024 * 1024)
    val safeMb = (totalMemMb * SAFETY_MEMORY_FRACTION).toInt().coerceIn(MIN_SAFETY_MB, MAX_SAFETY_MB)
    return safeMb * 1024 * 1024
}
