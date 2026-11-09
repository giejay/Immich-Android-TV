package nl.giejay.android.tv.immich.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import nl.giejay.android.tv.immich.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.giejay.android.tv.immich.ui.theme.ImmichFocus
import nl.giejay.android.tv.immich.ui.theme.ImmichIcons

data class NavRailItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    /** Draw a divider above this item. */
    val separated: Boolean = false,
)

/**
 * Side navigation. Collapsed it shows icons only; while it owns focus it expands to show labels
 * and overlays the page content (the content never relayouts).
 *
 * @param onItemFocused focus moved onto an item (used to preview/switch the page).
 * @param onItemClick centre/enter was pressed on an item.
 * @param onEnterContent right was pressed on an item: move focus into the page content.
 * @param onRailFocusChanged the rail as a whole gained/lost focus.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeNavRail(
    items: List<NavRailItem>,
    selectedKey: String?,
    expanded: Boolean,
    editMode: Boolean,
    isHidden: (String) -> Boolean,
    onItemFocused: (String) -> Unit,
    onItemClick: (String) -> Unit,
    onEnterContent: (String) -> Unit,
    onRailFocusChanged: (Boolean) -> Unit,
    editKey: String,
    doneLabel: String,
    modifier: Modifier = Modifier,
) {
    val selectedRequester = remember { FocusRequester() }
    var selectedAttached by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        if (expanded) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background,
        label = "railBackground",
    )

    Column(
        modifier
            .fillMaxHeight()
            .background(background)
            .onFocusChanged { onRailFocusChanged(it.hasFocus) }
            // Entering the rail from the content always lands on the selected page.
            .focusProperties {
                enter = { if (selectedAttached) selectedRequester else FocusRequester.Default }
            }
            .focusGroup()
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        // The logo stays pinned: only the entries scroll, so it never gets cut off on short screens.
        NavRailBrand(expanded)
        Spacer(Modifier.height(4.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
        items.forEach { item ->
            if (item.separated) Spacer(Modifier.height(12.dp))
            val selected = item.key == selectedKey
            NavRailEntry(
                item = item,
                label = if (editMode && item.key == editKey) doneLabel else item.label,
                selected = selected,
                expanded = expanded,
                editMode = editMode,
                hidden = isHidden(item.key),
                onFocused = { onItemFocused(item.key) },
                onClick = { onItemClick(item.key) },
                onEnterContent = { onEnterContent(item.key) },
                modifier = if (selected) {
                    Modifier
                        .focusRequester(selectedRequester)
                        .onGloballyPositioned { selectedAttached = true }
                } else {
                    Modifier
                },
            )
        }
        }
    }
}

/** App logo at the very top of the rail; with the labels expanded the name is shown next to it. */
@Composable
private fun NavRailBrand(expanded: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(BrandHeight).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            // the rosette alone (no tile), so it sits cleanly on the rail background
            painter = painterResource(R.drawable.icon_mark),
            contentDescription = stringResource(R.string.nav_brand),
            modifier = Modifier.size(LogoSize),
        )
        if (expanded) {
            Text(
                text = stringResource(R.string.nav_brand),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
    }
}

/** Kept low enough that the pinned logo row plus all entries fit a 1080p screen without scrolling. */
private val EntryHeight = 36.dp
private val BrandHeight = 40.dp
private val LogoSize = 32.dp

@Composable
private fun NavRailEntry(
    item: NavRailItem,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    editMode: Boolean,
    hidden: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onEnterContent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.large),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.surface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = ImmichFocus.ButtonScale),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onPreviewKeyEvent {
                if (!editMode && it.key == Key.DirectionRight) {
                    if (it.type == KeyEventType.KeyDown) onEnterContent()
                    true
                } else {
                    false
                }
            },
    ) {
        Row(
            Modifier.fillMaxWidth().height(EntryHeight).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(item.icon, contentDescription = if (expanded) null else label, modifier = Modifier.size(24.dp))
            if (expanded) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp).weight(1f),
                )
                if (editMode) {
                    Icon(
                        if (hidden) ImmichIcons.Hidden else ImmichIcons.Visible,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp).size(22.dp),
                    )
                }
            }
        }
    }
}
