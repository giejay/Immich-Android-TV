package nl.giejay.android.tv.immich.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.ui.theme.ImmichFocus
import nl.giejay.android.tv.immich.ui.theme.ImmichIcons

/** What the search page currently shows below the field. */
enum class SearchStatus { IDLE, LOADING, RESULTS, NO_RESULTS, ERROR }

/**
 * Search field, matching people, recent searches and a status line, shown above the result grid.
 * OK on the field opens the keyboard (a real text field takes over until Done), like the login form.
 */
@Composable
fun SearchHeader(
    submittedQuery: String,
    status: SearchStatus,
    people: List<String>,
    selectedPerson: String?,
    recents: List<String>,
    onSubmit: (String) -> Unit,
    onPerson: (Int) -> Unit,
    onClearPerson: () -> Unit,
    onRecent: (String) -> Unit,
    albums: List<String> = emptyList(),
    selectedAlbum: String? = null,
    onAlbum: (Int) -> Unit = {},
    onClearAlbum: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 40.dp).padding(top = 24.dp, bottom = 12.dp)) {
        SearchField(submittedQuery, onSubmit)
        Box(Modifier.height(10.dp))
        // one labeled row each: matching people, albums (also the scope of the search), recent searches
        val personChips: List<Pair<String, () -> Unit>> = when {
            selectedPerson != null -> listOf("$selectedPerson  ✕" to onClearPerson)
            else -> people.mapIndexed { index, name -> name to { onPerson(index) } }
        }
        val albumChips: List<Pair<String, () -> Unit>> = when {
            selectedAlbum != null -> listOf("$selectedAlbum  ✕" to onClearAlbum)
            else -> albums.mapIndexed { index, name -> name to { onAlbum(index) } }
        }
        val recentChips: List<Pair<String, () -> Unit>> =
            if (status == SearchStatus.IDLE && personChips.isEmpty()) recents.map { it to { onRecent(it) } } else emptyList()
        ChipRow(stringResource(R.string.search_people), personChips)
        ChipRow(stringResource(R.string.search_albums), albumChips)
        ChipRow(stringResource(R.string.search_recent), recentChips)
        val statusText = when (status) {
            SearchStatus.IDLE -> if (recents.isEmpty()) stringResource(R.string.search_hint) else null
            SearchStatus.LOADING -> stringResource(R.string.search_loading)
            SearchStatus.NO_RESULTS -> stringResource(R.string.search_no_results, selectedPerson ?: submittedQuery.ifBlank { selectedAlbum.orEmpty() })
            SearchStatus.ERROR -> stringResource(R.string.search_error)
            SearchStatus.RESULTS -> null
        }
        if (statusText != null) {
            Text(
                statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun ChipRow(label: String, chips: List<Pair<String, () -> Unit>>) {
    if (chips.isEmpty()) return
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
        items(chips) { (text, onClick) -> Chip(text, onClick) }
    }
}

@Composable
private fun SearchField(value: String, onSubmit: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(value) }
    // the field reports "not focused" once while it is created; only leaving after it had focus ends editing
    var hadFocus by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(editing) {
        hadFocus = false
        if (editing) {
            fieldFocus.requestFocus()
            keyboard?.show()
        }
    }

    Surface(
        onClick = { editing = true },
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.large),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.surface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = ImmichFocus.ButtonScale),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(ImmichIcons.Search, contentDescription = null, modifier = Modifier.size(26.dp))
            Box(Modifier.padding(start = 14.dp).weight(1f)) {
                if (editing) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            editing = false
                            keyboard?.hide()
                            onSubmit(text)
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                            .focusRequester(fieldFocus)
                            .onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus && editing) editing = false },
                    )
                } else {
                    Text(
                        text = value.ifEmpty { stringResource(R.string.search_placeholder) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.extraLarge),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = ImmichFocus.ButtonScale),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}
