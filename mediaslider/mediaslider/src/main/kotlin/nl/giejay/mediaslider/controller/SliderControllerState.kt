package nl.giejay.mediaslider.controller

import androidx.annotation.DrawableRes
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import nl.giejay.mediaslider.plugin.ControllerButtonPlacement
import nl.giejay.mediaslider.plugin.ControllerButtonSpec

/** Stable ids of the built-in controller controls (plugins anchor on them and focus is restored by them). */
object ControllerIds {
    const val MUTE = "mute"
    const val PREVIOUS = "previous"
    const val PLAY_PAUSE = "play_pause"
    const val SLIDESHOW_REVERSE = "slideshow_reverse"
    const val SLIDESHOW = "slideshow"
    const val NEXT = "next"
    const val SEEK = "seek"
}

/** One focusable button of the controller row, built-in or provided by a plugin. */
sealed interface ControllerEntry {
    val id: String

    data class BuiltIn(override val id: String) : ControllerEntry
    data class Plugin(val spec: ControllerButtonSpec) : ControllerEntry {
        override val id: String get() = spec.id
    }
}

/** Order of the button row and the focus rules that depend on it; pure so it can be unit tested. */
object ControllerLayout {
    fun entries(isVideo: Boolean, plugins: List<ControllerButtonSpec>): List<ControllerEntry> {
        val row = buildList {
            if (isVideo) add(ControllerEntry.BuiltIn(ControllerIds.MUTE))
            add(ControllerEntry.BuiltIn(ControllerIds.PREVIOUS))
            if (isVideo) add(ControllerEntry.BuiltIn(ControllerIds.PLAY_PAUSE))
            add(ControllerEntry.BuiltIn(ControllerIds.SLIDESHOW_REVERSE))
            add(ControllerEntry.BuiltIn(ControllerIds.SLIDESHOW))
            add(ControllerEntry.BuiltIn(ControllerIds.NEXT))
        }.toMutableList<ControllerEntry>()
        plugins.forEach { spec ->
            val entry = ControllerEntry.Plugin(spec)
            val anchor = spec.anchorId?.let { id -> row.indexOfFirst { it.id == id } } ?: -1
            val index = when (spec.placement) {
                ControllerButtonPlacement.START -> 0
                ControllerButtonPlacement.END -> row.size
                ControllerButtonPlacement.LEFT_OF -> if (anchor >= 0) anchor else row.size
                ControllerButtonPlacement.RIGHT_OF -> if (anchor >= 0) anchor + 1 else row.size
            }
            row.add(index, entry)
        }
        return row
    }

    /** Ids in D-pad order: the button row, then the seek bar for videos. */
    fun focusChain(isVideo: Boolean, plugins: List<ControllerButtonSpec>): List<String> =
        entries(isVideo, plugins).map { it.id } + if (isVideo) listOf(ControllerIds.SEEK) else emptyList()

    /** Control that gets focus when the controller opens. */
    fun initialFocus(isVideo: Boolean, reverseSlideshow: Boolean): String = when {
        isVideo -> ControllerIds.PLAY_PAUSE
        reverseSlideshow -> ControllerIds.SLIDESHOW_REVERSE
        else -> ControllerIds.SLIDESHOW
    }

    /**
     * Control to focus after the page changed while the controller stayed open: the same control if it
     * still exists, a semantic neighbour across photo <-> video, else the same slot in the chain.
     */
    fun restoreFocus(
        previousId: String?,
        previousIndex: Int,
        chain: List<String>,
        isVideo: Boolean,
        reverseSlideshow: Boolean,
    ): String {
        if (previousId != null && previousId in chain) return previousId
        val semantic = when (previousId) {
            ControllerIds.PLAY_PAUSE -> ControllerIds.SLIDESHOW
            ControllerIds.SLIDESHOW -> if (isVideo) ControllerIds.PLAY_PAUSE else null
            ControllerIds.MUTE -> ControllerIds.PREVIOUS
            ControllerIds.SEEK -> if (isVideo) ControllerIds.PLAY_PAUSE else ControllerIds.SLIDESHOW
            else -> null
        }
        if (semantic != null && semantic in chain) return semantic
        if (previousIndex >= 0 && chain.isNotEmpty()) return chain[previousIndex.coerceIn(0, chain.lastIndex)]
        return initialFocus(isVideo, reverseSlideshow).takeIf { it in chain } ?: chain.firstOrNull() ?: ControllerIds.SLIDESHOW
    }
}

/** Asks the bar to move focus to [id]; a new instance each time so repeated requests re-fire. */
class ControllerFocusRequest(val id: String)

/**
 * Everything the Compose controller bar shows. [MediaSliderController] owns and updates it; the bar
 * only reads it and calls the actions.
 */
class SliderControllerUiState {
    var visible by mutableStateOf(false)
    var isVideo by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var soundOn by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var pluginButtons by mutableStateOf<List<ControllerButtonSpec>>(emptyList())
    var focusRequest by mutableStateOf<ControllerFocusRequest?>(null)

    /** Last focused control, reported by the bar; plain fields because nothing renders them. */
    var focusedId: String? = null
    var focusedIndex: Int = -1

    var onPrevious: () -> Unit = {}
    var onNext: () -> Unit = {}
    var onSlideshow: () -> Unit = {}
    var onSlideshowReverse: () -> Unit = {}
    var onMute: () -> Unit = {}
    var onPlayPause: () -> Unit = {}
    var onSeekTo: (Long) -> Unit = {}
}

/** Observable icon for plugin buttons that change after a click (favorite). */
fun iconState(@DrawableRes initial: Int): androidx.compose.runtime.MutableState<Int> = mutableStateOf(initial)

typealias ControllerIcon = State<Int>
