package nl.giejay.mediaslider.plugin

import android.view.KeyEvent

/**
 * Viewer Enter/Back for the bottom details strip owned by [MetadataViewPlugin].
 *
 * - Enter/Center: show details (falls through so transport can still open).
 * - Back: hide details when transport is already closed; otherwise let main/remote handle Back.
 *
 * Register on the key-plugin list **before** [MediaRemoteControlsKeyEventPlugin] so Back can
 * dismiss details before exiting an autoplay slideshow.
 */
class DetailsOverlayKeyPlugin(
    private val metadata: MetadataViewPlugin
) : SliderKeyEventPlugin {

    override fun onKeyDown(event: KeyEvent, state: SliderKeyEventState): SliderKeyEventResult {
        if (!metadata.detailsToggleEnabled) {
            return SliderKeyEventResult.UNHANDLED
        }
        val controller = state.controller
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (controller.isControllerVisible) {
                    return SliderKeyEventResult.DISPATCH_TO_SUPER
                }
                metadata.setDetailsVisible(true)
                // Let MediaSliderController open the transport bar as well.
                return SliderKeyEventResult.UNHANDLED
            }
            KeyEvent.KEYCODE_BACK -> {
                if (controller.isControllerVisible) {
                    return SliderKeyEventResult.UNHANDLED
                }
                if (metadata.detailsVisible) {
                    metadata.setDetailsVisible(false)
                    return SliderKeyEventResult.HANDLED_CONSUME
                }
            }
        }
        return SliderKeyEventResult.UNHANDLED
    }
}
