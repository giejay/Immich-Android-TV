package nl.giejay.android.tv.immich.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Single place that maps app concepts to vector icons, replacing the old PNG drawables.
 * Screens must use these instead of importing Material icons directly.
 */
object ImmichIcons {
    // Home navigation
    val Timeline: ImageVector get() = Icons.Filled.Timeline
    val Search: ImageVector get() = Icons.Filled.Search
    val Clear: ImageVector get() = Icons.Filled.Close
    val Albums: ImageVector get() = Icons.Filled.PhotoAlbum
    val Photos: ImageVector get() = Icons.Filled.PhotoLibrary
    val Random: ImageVector get() = Icons.Filled.Shuffle
    val People: ImageVector get() = Icons.Filled.People
    val Recent: ImageVector get() = Icons.Filled.History
    val Seasonal: ImageVector get() = Icons.Filled.Event
    val Folders: ImageVector get() = Icons.Filled.Folder
    val FolderUp: ImageVector get() = Icons.Filled.ArrowUpward
    val Edit: ImageVector get() = Icons.Filled.Edit
    val Settings: ImageVector get() = Icons.Filled.Settings

    // Settings categories
    val Server: ImageVector get() = Icons.Filled.Dns
    val ViewSettings: ImageVector get() = Icons.Filled.Visibility
    val Screensaver: ImageVector get() = Icons.Filled.Slideshow
    val HomeChannels: ImageVector get() = Icons.Filled.Tv
    val Debug: ImageVector get() = Icons.Filled.BugReport

    // Actions
    val Selected: ImageVector get() = Icons.Filled.CheckCircle
    val Add: ImageVector get() = Icons.Filled.Add
    val Remove: ImageVector get() = Icons.Filled.Remove
    val Save: ImageVector get() = Icons.Filled.Save
    val Visible: ImageVector get() = Icons.Filled.Visibility
    val Hidden: ImageVector get() = Icons.Filled.VisibilityOff
    val MoveUp: ImageVector get() = Icons.Filled.KeyboardArrowUp
    val MoveDown: ImageVector get() = Icons.Filled.KeyboardArrowDown
}
