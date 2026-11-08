package nl.giejay.android.tv.immich.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.ui.card.ImmichCard
import nl.giejay.android.tv.immich.ui.theme.ImmichIcons

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
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Immich Android TV Next", style = MaterialTheme.typography.displaySmall)
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
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Button(onClick = {}) { Text("First") }
            Button(onClick = {}) { Text("Second") }
            Button(onClick = {}) { Text("Third") }
        }
    }
}
