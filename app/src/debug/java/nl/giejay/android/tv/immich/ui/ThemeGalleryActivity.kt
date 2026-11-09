package nl.giejay.android.tv.immich.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.giejay.android.tv.immich.api.model.TimelineAsset
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.timeline.TimelineMosaicCell
import nl.giejay.android.tv.immich.ui.timeline.TimelineDayHeader
import nl.giejay.android.tv.immich.ui.timeline.TimelineMosaicCell
import nl.giejay.android.tv.immich.ui.card.ImmichCard
import nl.giejay.android.tv.immich.ui.theme.ImmichIcons
import java.time.OffsetDateTime

/** Debug-only: shows typography, icons and focusable buttons to check the theme on a real TV. */
class ThemeGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(immichComposeView(this) { Gallery() })
    }
}

@Composable
private fun Gallery() {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Immich TV Next", style = MaterialTheme.typography.displaySmall)
        Text("Headline and body text on the background colour", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            listOf(
                ImmichIcons.Timeline, ImmichIcons.Albums, ImmichIcons.Photos, ImmichIcons.Random,
                ImmichIcons.People, ImmichIcons.Recent, ImmichIcons.Seasonal, ImmichIcons.Folders,
                ImmichIcons.Settings,
            ).forEach { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        }
        Row {
            ImmichCard(Card("Folder card", "unfocused", "1", "folder", null), focused = false)
            ImmichCard(Card("Focused card", "scaled + border", "2", "folder_up", null), focused = true)
            ImmichCard(Card("Selected card", "check badge", "3", "folder", null, selected = true), focused = false)
        }
        TimelineDayHeader("Sun, Aug 24, 2025")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TimelineMosaicCell(sampleCell(isImage = true), focused = false, thumbnailUrl = null)
            TimelineMosaicCell(sampleCell(isImage = true), focused = true, thumbnailUrl = null)
            TimelineMosaicCell(sampleCell(isImage = false), focused = false, thumbnailUrl = null)
            TimelineMosaicCell(sampleCell(isImage = false), focused = true, thumbnailUrl = null)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Button(onClick = {}) { Text("First") }
            Button(onClick = {}) { Text("Second") }
            Button(onClick = {}) { Text("Third") }
        }
    }
}

private fun sampleCell(isImage: Boolean) = TimelineMosaicCell(
    dayKey = "2025-08-24",
    asset = TimelineAsset(
        id = "sample", ratio = 1.5, isFavorite = false, isImage = isImage, thumbhash = null,
        fileCreatedAt = OffsetDateTime.parse("2025-08-24T10:00:00Z"), localOffsetHours = 0.0,
        duration = if (isImage) null else "0:01:23.000",
    ),
    widthPx = 240, heightPx = 160, rowId = "row", indexInRow = 0, xPx = 0,
)
