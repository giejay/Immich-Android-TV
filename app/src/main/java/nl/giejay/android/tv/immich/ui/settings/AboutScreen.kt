package nl.giejay.android.tv.immich.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.giejay.android.tv.immich.BuildConfig
import nl.giejay.android.tv.immich.R

private class AboutSection(val title: Int, val body: Int)

private val SECTIONS = listOf(
    AboutSection(R.string.about_unofficial_title, R.string.about_unofficial_body),
    AboutSection(R.string.about_license_title, R.string.about_license_body),
    AboutSection(R.string.about_privacy_title, R.string.about_privacy_body),
    AboutSection(R.string.about_feedback_title, R.string.about_feedback_body),
)

/**
 * The About page of the settings: version, the disclaimer that the app is not affiliated with the Immich
 * project, license and privacy notes and where to report problems. URLs are shown as text because a TV
 * cannot open them. Every section is focusable so the D-pad scrolls through the list.
 */
@Composable
fun AboutScreen() {
    val version = remember { "v${BuildConfig.VERSION_NAME}" }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(version, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(SECTIONS) { section ->
            Surface(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                colors = ClickableSurfaceDefaults.colors(),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(section.title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(section.body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}
