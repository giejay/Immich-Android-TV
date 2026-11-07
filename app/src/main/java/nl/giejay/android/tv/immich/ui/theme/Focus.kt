package nl.giejay.android.tv.immich.ui.theme

import androidx.compose.ui.unit.dp

/** Shared focus-state values so every focusable element looks and feels the same. */
object ImmichFocus {
    /** Scale applied to a focused card. Kept small so neighbours in dense grids are not hidden. */
    const val CardScale = 1.06f
    const val ButtonScale = 1.04f
    val BorderWidth = 3.dp
    const val AnimationMillis = 150
}
