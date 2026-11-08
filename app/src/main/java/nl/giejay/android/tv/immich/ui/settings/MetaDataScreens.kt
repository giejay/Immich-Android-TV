package nl.giejay.android.tv.immich.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.ui.theme.ImmichFocus
import nl.giejay.android.tv.immich.ui.theme.ImmichIcons
import nl.giejay.mediaslider.adapter.AlignOption
import nl.giejay.mediaslider.adapter.MetaDataItem
import nl.giejay.mediaslider.model.MetaDataType

/**
 * Preview of the slideshow overlay: left and right metadata columns anchored to the bottom, the
 * way they appear in the slider. OK on an item edits it, the plus buttons add a new one.
 */
@Composable
fun MetaDataCustomizerScreen(
    left: List<MetaDataItem>,
    right: List<MetaDataItem>,
    onItemClick: (MetaDataItem, List<MetaDataItem>) -> Unit,
    onAdd: (AlignOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            firstFocus.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 64.dp, vertical = 32.dp),
    ) {
        Text(
            stringResource(R.string.edit_metadata_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            MetaDataColumn(left, AlignOption.LEFT, context, onItemClick, onAdd, Modifier.weight(1f), firstFocus)
            MetaDataColumn(right, AlignOption.RIGHT, context, onItemClick, onAdd, Modifier.weight(1f), null)
        }
    }
}

@Composable
private fun MetaDataColumn(
    items: List<MetaDataItem>,
    align: AlignOption,
    context: Context,
    onItemClick: (MetaDataItem, List<MetaDataItem>) -> Unit,
    onAdd: (AlignOption) -> Unit,
    modifier: Modifier,
    initialFocus: FocusRequester?,
) {
    val horizontal = if (align == AlignOption.RIGHT) Alignment.End else Alignment.Start
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = horizontal) {
        items.forEach { item ->
            ChipButton(item.getTitle(context), onClick = { onItemClick(item, items) })
        }
        Surface(
            onClick = { onAdd(align) },
            shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                focusedContainerColor = MaterialTheme.colorScheme.primary,
                focusedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = ImmichFocus.ButtonScale),
            modifier = Modifier.padding(top = 12.dp).let { if (initialFocus != null && items.isEmpty()) it.focusRequester(initialFocus) else it },
        ) {
            Icon(ImmichIcons.Add, contentDescription = null, modifier = Modifier.padding(12.dp).size(28.dp))
        }
    }
}

@Composable
private fun ChipButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.surface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = ImmichFocus.ButtonScale),
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

private enum class EditorChoice { TYPE, ORDER }

private const val FONT_MIN = 12
private const val FONT_MAX = 50
private const val PADDING_MIN = 0
private const val PADDING_MAX = 50

/** Result of the editor: where the item goes and how it looks. */
class MetaDataItemEdit(val type: MetaDataType, val order: Int, val padding: Int, val fontSize: Int)

/** Edit panel for one metadata item (type, position, font size, padding, delete/save). */
@Composable
fun MetaDataItemEditor(
    allItems: List<MetaDataItem>,
    item: MetaDataItem,
    onSave: (MetaDataItemEdit) -> Unit,
    onDelete: () -> Unit,
    closeChoiceSignal: Int,
    onChoosingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(item.type) }
    var order by remember { mutableIntStateOf(allItems.indexOf(item) + 1) }
    var fontSize by remember { mutableIntStateOf(item.fontSize) }
    var padding by remember { mutableIntStateOf(item.padding) }
    var choosing by remember { mutableStateOf<EditorChoice?>(null) }
    val firstFocus = remember { FocusRequester() }
    // a type can only be used once per side: offer the free ones plus the current one
    val typeChoices = remember(allItems, item) {
        MetaDataType.entries.filterNot { e -> e != item.type && allItems.any { it.type == e } }
    }
    val orderChoices = remember(allItems) { List(allItems.size) { it + 1 } }

    LaunchedEffect(closeChoiceSignal) {
        if (closeChoiceSignal > 0) choosing = null
    }
    LaunchedEffect(choosing) {
        onChoosingChanged(choosing != null)
        try {
            firstFocus.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    Column(
        modifier
            .width(560.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        val active = choosing
        Text(
            when (active) {
                EditorChoice.TYPE -> stringResource(R.string.meta_data_type_dialog_title)
                EditorChoice.ORDER -> stringResource(R.string.meta_data_order_dialog_title)
                null -> stringResource(R.string.edit_metadata_title)
            },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
        )
        when (active) {
            EditorChoice.TYPE -> ChoiceColumn(
                titles = typeChoices.map { it.getTitle(context) },
                selectedIndex = typeChoices.indexOf(type),
                firstFocus = firstFocus,
                onSelect = { i ->
                    val chosen = typeChoices[i]
                    if (chosen != type) fontSize = chosen.defaultFontSize
                    type = chosen
                    choosing = null
                },
            )
            EditorChoice.ORDER -> ChoiceColumn(
                titles = orderChoices.map { it.toString() },
                selectedIndex = orderChoices.indexOf(order),
                firstFocus = firstFocus,
                onSelect = { i ->
                    order = orderChoices[i]
                    choosing = null
                },
            )
            null -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    SettingRow(
                        title = stringResource(R.string.meta_data_type_title),
                        summary = type.getTitle(context),
                        trailing = null,
                        onClick = { choosing = EditorChoice.TYPE },
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                }
                item {
                    SettingRow(
                        title = stringResource(R.string.meta_data_order_title),
                        summary = order.toString(),
                        trailing = null,
                        onClick = { choosing = EditorChoice.ORDER },
                    )
                }
                item {
                    StepperRow(stringResource(R.string.meta_data_font_size_title), fontSize, FONT_MIN, FONT_MAX) { fontSize = it }
                }
                item {
                    StepperRow(stringResource(R.string.meta_data_padding_title), padding, PADDING_MIN, PADDING_MAX) { padding = it }
                }
                item {
                    SettingRow(
                        title = stringResource(R.string.meta_data_delete_desc),
                        summary = null,
                        trailing = null,
                        onClick = onDelete,
                    )
                }
                item {
                    SettingRow(
                        title = stringResource(R.string.meta_data_save_desc),
                        summary = null,
                        trailing = null,
                        onClick = { onSave(MetaDataItemEdit(type, order, padding, fontSize)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceColumn(titles: List<String>, selectedIndex: Int, firstFocus: FocusRequester, onSelect: (Int) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(titles.size) { index ->
            SettingRow(
                title = titles[index],
                summary = null,
                trailing = { if (index == selectedIndex) SelectedDot() },
                onClick = { onSelect(index) },
                modifier = if (index == selectedIndex.coerceAtLeast(0)) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
    }
}

/** Number row changed with Left/Right (hold to repeat). */
@Composable
private fun StepperRow(title: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    SettingRow(
        title = title,
        summary = null,
        trailing = { Text(value.toString(), style = MaterialTheme.typography.titleMedium) },
        onClick = {},
        modifier = Modifier.onPreviewKeyEvent {
            val delta = when (it.key) {
                Key.DirectionLeft -> -1
                Key.DirectionRight -> 1
                else -> 0
            }
            if (delta == 0) {
                false
            } else {
                if (it.type == KeyEventType.KeyDown) onChange((value + delta).coerceIn(min, max))
                true
            }
        },
    )
}
