package nl.giejay.mediaslider.plugin

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.SeekParameters
import nl.giejay.mediaslider.model.SliderItemType
import nl.giejay.mediaslider.view.MediaSliderController

/**
 * Remote / D-pad playback controls that stay out of [MediaSliderController]:
 * - Video FF/RW: tap (~10s) vs hold (smooth scrub); edge seek → next/prev asset
 * - Opt-in D-pad Left/Right seek in video ([nl.giejay.mediaslider.config.MediaSliderConfiguration.dpadSeeksInVideo])
 * - Photo FF/RW → next/prev asset
 * - Photo Play/Pause → toggle slideshow
 * - Video Play/Pause → play/pause current player
 * - Back during an active photo slideshow → exit without pausing first
 */
class MediaRemoteControlsKeyEventPlugin : SliderKeyEventPlugin {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingSeekTargetMs: Long? = null
    private var holdSeekStepMs: Long = 0L
    private var holdSeekEngaged = false
    private var pendingSeekTapForward: Boolean? = null
    private var pausedForHoldSeek = false
    private var activeController: MediaSliderController? = null

    private val holdSeekRunnable = object : Runnable {
        override fun run() {
            if (holdSeekStepMs == 0L) return
            val controller = activeController ?: return
            holdSeekEngaged = true
            pendingSeekTapForward = null
            beginHoldSeekScrub(controller)
            seekOrSkipBy(controller, holdSeekStepMs, allowAssetSkip = false)
            mainHandler.postDelayed(this, HOLD_SEEK_TICK_MS)
        }
    }

    override fun onKeyDown(event: KeyEvent, state: SliderKeyEventState): SliderKeyEventResult {
        activeController = state.controller
        val itemType = state.currentItemType
        val controller = state.controller

        if (event.keyCode == KeyEvent.KEYCODE_BACK &&
            state.isSlideshowPlaying &&
            itemType == SliderItemType.IMAGE &&
            !state.isControllerVisible
        ) {
            // Exit the viewer without pausing the slideshow (or flashing the center glyph).
            return SliderKeyEventResult.DISPATCH_TO_SUPER
        }

        if (itemType == SliderItemType.IMAGE &&
            (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        ) {
            controller.toggleSlideshow(true)
            return SliderKeyEventResult.HANDLED_CONSUME
        }

        if (itemType == SliderItemType.VIDEO && event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            return if (togglePlayPause(controller)) {
                SliderKeyEventResult.HANDLED_CONSUME
            } else {
                SliderKeyEventResult.UNHANDLED
            }
        }

        if (itemType == SliderItemType.IMAGE && isRemoteSeekForward(event.keyCode)) {
            controller.goToNextAsset()
            return SliderKeyEventResult.HANDLED_CONSUME
        }
        if (itemType == SliderItemType.IMAGE && isRemoteSeekRewind(event.keyCode)) {
            controller.goToPreviousAsset()
            return SliderKeyEventResult.HANDLED_CONSUME
        }

        if (itemType == SliderItemType.VIDEO && isRemoteSeekForward(event.keyCode)) {
            return if (onVideoSeekKeyDown(controller, forward = true, isRepeat = event.repeatCount > 0)) {
                SliderKeyEventResult.HANDLED_CONSUME
            } else {
                SliderKeyEventResult.UNHANDLED
            }
        }
        if (itemType == SliderItemType.VIDEO && isRemoteSeekRewind(event.keyCode)) {
            return if (onVideoSeekKeyDown(controller, forward = false, isRepeat = event.repeatCount > 0)) {
                SliderKeyEventResult.HANDLED_CONSUME
            } else {
                SliderKeyEventResult.UNHANDLED
            }
        }

        if (itemType == SliderItemType.VIDEO &&
            state.config.dpadSeeksInVideo &&
            !state.isControllerVisible
        ) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT ->
                    return if (onVideoSeekKeyDown(controller, forward = true, isRepeat = event.repeatCount > 0)) {
                        SliderKeyEventResult.HANDLED_CONSUME
                    } else {
                        SliderKeyEventResult.UNHANDLED
                    }
                KeyEvent.KEYCODE_DPAD_LEFT ->
                    return if (onVideoSeekKeyDown(controller, forward = false, isRepeat = event.repeatCount > 0)) {
                        SliderKeyEventResult.HANDLED_CONSUME
                    } else {
                        SliderKeyEventResult.UNHANDLED
                    }
            }
        }

        return SliderKeyEventResult.UNHANDLED
    }

    override fun onKeyUp(event: KeyEvent, state: SliderKeyEventState): SliderKeyEventResult {
        activeController = state.controller
        val itemType = state.currentItemType
        val isSeekKey = isRemoteSeekForward(event.keyCode) || isRemoteSeekRewind(event.keyCode) ||
            (state.config.dpadSeeksInVideo &&
                itemType == SliderItemType.VIDEO &&
                (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT))
        if (!isSeekKey) return SliderKeyEventResult.UNHANDLED
        return if (onSeekKeyUp(state.controller)) {
            SliderKeyEventResult.HANDLED_CONSUME
        } else {
            SliderKeyEventResult.UNHANDLED
        }
    }

    private fun onVideoSeekKeyDown(
        controller: MediaSliderController,
        forward: Boolean,
        isRepeat: Boolean
    ): Boolean {
        if (controller.currentPlayer == null) return false
        if (isRepeat) return true
        pendingSeekTapForward = forward
        holdSeekEngaged = false
        startHoldSeek(if (forward) HOLD_SEEK_STEP_MS else -HOLD_SEEK_STEP_MS)
        return true
    }

    private fun onSeekKeyUp(controller: MediaSliderController): Boolean {
        val tapForward = pendingSeekTapForward
        val engaged = holdSeekEngaged
        stopHoldSeek()
        if (!engaged && tapForward != null) {
            seekOrSkipBy(
                controller,
                if (tapForward) REMOTE_SEEK_STEP_MS else -REMOTE_SEEK_STEP_MS,
                allowAssetSkip = true
            )
            return true
        }
        return engaged || tapForward != null
    }

    private fun stopHoldSeek() {
        holdSeekStepMs = 0L
        pendingSeekTapForward = null
        holdSeekEngaged = false
        mainHandler.removeCallbacks(holdSeekRunnable)
        endHoldSeekScrub()
    }

    private fun startHoldSeek(stepMs: Long) {
        holdSeekStepMs = 0L
        mainHandler.removeCallbacks(holdSeekRunnable)
        holdSeekStepMs = stepMs
        mainHandler.postDelayed(holdSeekRunnable, HOLD_SEEK_START_DELAY_MS)
    }

    @OptIn(UnstableApi::class)
    private fun beginHoldSeekScrub(controller: MediaSliderController) {
        val player = controller.currentPlayer ?: return
        if (!pausedForHoldSeek) {
            pausedForHoldSeek = player.isPlaying || player.playWhenReady
            if (pausedForHoldSeek) {
                player.pause()
            }
            player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        }
    }

    @OptIn(UnstableApi::class)
    private fun endHoldSeekScrub() {
        val player = activeController?.currentPlayer
        if (player != null) {
            player.setSeekParameters(SeekParameters.DEFAULT)
            if (pausedForHoldSeek) {
                player.play()
            }
        }
        pausedForHoldSeek = false
        pendingSeekTargetMs = null
    }

    private fun seekOrSkipBy(
        controller: MediaSliderController,
        deltaMs: Long,
        allowAssetSkip: Boolean
    ): Boolean {
        val player = controller.currentPlayer ?: return false
        val duration = player.contentDuration.takeIf { it > 0 } ?: player.duration
        if (duration <= 0) return false
        val position = pendingSeekTargetMs ?: player.currentPosition
        if (deltaMs > 0) {
            if (allowAssetSkip && position >= duration - SEEK_EDGE_EPSILON_MS) {
                pendingSeekTargetMs = null
                controller.cancelNextAssetTimer()
                controller.goToNextAsset()
                return true
            }
            val target = (position + deltaMs).coerceAtMost(duration)
            pendingSeekTargetMs = target
            player.seekTo(target)
            return true
        }
        if (allowAssetSkip && position <= SEEK_EDGE_EPSILON_MS) {
            pendingSeekTargetMs = null
            controller.cancelNextAssetTimer()
            controller.goToPreviousAsset()
            return true
        }
        val target = (position + deltaMs).coerceAtLeast(0L)
        pendingSeekTargetMs = target
        player.seekTo(target)
        return true
    }

    private fun togglePlayPause(controller: MediaSliderController): Boolean {
        val player = controller.currentPlayer ?: return false
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.contentDuration > 0 && player.currentPosition >= player.contentDuration) {
                player.seekToDefaultPosition()
            }
            player.play()
        }
        return true
    }

    private fun isRemoteSeekForward(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ||
            keyCode == KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD ||
            keyCode == KeyEvent.KEYCODE_FORWARD

    private fun isRemoteSeekRewind(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_MEDIA_REWIND ||
            keyCode == KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD

    private companion object {
        const val REMOTE_SEEK_STEP_MS = 10_000L
        const val HOLD_SEEK_STEP_MS = 2_000L
        const val HOLD_SEEK_START_DELAY_MS = 400L
        const val HOLD_SEEK_TICK_MS = 200L
        const val SEEK_EDGE_EPSILON_MS = 500L
    }
}
