package nl.giejay.android.tv.immich.ui.settings

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.giejay.android.tv.immich.shared.prefs.ActionPref
import nl.giejay.android.tv.immich.shared.prefs.BooleanPref
import nl.giejay.android.tv.immich.shared.prefs.EnumByTitlePref
import nl.giejay.android.tv.immich.shared.prefs.EnumPref
import nl.giejay.android.tv.immich.shared.prefs.EnumWithTitle
import nl.giejay.android.tv.immich.shared.prefs.IntListPref
import nl.giejay.android.tv.immich.shared.prefs.IntSeekbarPref
import nl.giejay.android.tv.immich.shared.prefs.Pref
import nl.giejay.android.tv.immich.shared.prefs.PrefScreen
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.USER_ID
import nl.giejay.android.tv.immich.ui.theme.ImmichFocus

private const val SEEKBAR_MIN = 0
private const val SEEKBAR_MAX = 100
private const val SEEKBAR_STEP = 5

private class Choice(val value: String, val title: String)

/** Choices of a list preference; null when [pref] is not a list preference. */
@Suppress("UNCHECKED_CAST")
private fun choicesOf(context: Context, pref: Pref<*, *, *>): List<Choice>? = when (pref) {
    is EnumByTitlePref<*> -> pref.getEnumEntries().map { Choice(it.toString(), (it as EnumWithTitle).getTitle()) }
    is EnumPref<*> -> {
        val titles = context.resources.getStringArray(pref.titlesResourceId)
        val values = context.resources.getStringArray(pref.valuesResourceId)
        values.mapIndexed { i, v -> Choice(v, titles[i]) }
    }
    is IntListPref -> {
        val titles = context.resources.getStringArray(pref.titlesResourceId)
        val values = context.resources.getStringArray(pref.valuesResourceId)
        values.mapIndexed { i, v -> Choice(v, titles[i]) }
    }
    else -> null
}

/** Value of a list preference in the same string form as [Choice.value]. */
private fun currentChoiceValue(pref: Pref<*, *, *>): String? {
    val sp = PreferenceManager.sharedPreference
    return when (pref) {
        is EnumByTitlePref<*> -> pref.getValue(sp).toString()
        is EnumPref<*> -> pref.getValue(sp).toString()
        is IntListPref -> pref.getValue(sp).toString()
        else -> null
    }
}

@Suppress("UNCHECKED_CAST")
private fun saveChoice(pref: Pref<*, *, *>, value: String) {
    val sp = PreferenceManager.sharedPreference
    when (pref) {
        is EnumByTitlePref<*>, is EnumPref<*> -> (pref as Pref<Any?, *, String>).let { it.save(sp, it.fromPrefValue(value)) }
        is IntListPref -> pref.save(sp, value.toInt())
        else -> Unit
    }
}

private fun summaryOf(context: Context, pref: Pref<*, *, *>): String {
    val choice = choicesOf(context, pref)
    return when {
        pref === USER_ID -> PreferenceManager.get(USER_ID).ifEmpty { pref.summary }
        choice != null -> choice.firstOrNull { it.value == currentChoiceValue(pref) }?.title ?: pref.summary
        pref is IntSeekbarPref -> pref.getValue(PreferenceManager.sharedPreference).toString()
        else -> pref.summary
    }
}

/**
 * Settings panel rendered from a [PrefScreen] declaration: the same model the Leanback preference
 * fragments used. List preferences open an inline choice list (Back returns), switches toggle on
 * click and seek bars change with Left/Right.
 */
@Composable
fun PrefScreenPanel(
    screen: PrefScreen,
    navController: NavController?,
    modifier: Modifier = Modifier,
    /** Bumped by the host when Back was pressed; closes an open choice list. */
    closeChoiceSignal: Int = 0,
    /** Tells the host whether a choice list is open, so it can intercept Back. */
    onChoosingChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    // bumped after every change so rows re-read their value from the preferences
    var version by remember { mutableIntStateOf(0) }
    var choosing by remember { mutableStateOf<Pref<*, *, *>?>(null) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(closeChoiceSignal) {
        if (closeChoiceSignal > 0) choosing = null
    }
    LaunchedEffect(choosing) {
        onChoosingChanged(choosing != null)
        try {
            firstFocus.requestFocus()
        } catch (_: IllegalStateException) {
            // nothing focusable yet
        }
    }

    Column(
        modifier
            .width(560.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        val active = choosing
        Text(
            text = active?.title ?: screen.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
        )
        if (active != null) {
            val choices = choicesOf(context, active).orEmpty()
            val selectedValue = currentChoiceValue(active)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(choices.size) { index ->
                    val choice = choices[index]
                    SettingRow(
                        title = choice.title,
                        summary = null,
                        trailing = { if (choice.value == selectedValue) SelectedDot() },
                        onClick = {
                            saveChoice(active, choice.value)
                            version++
                            choosing = null
                        },
                        modifier = if (choice.value == selectedValue || (selectedValue == null && index == 0)) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                var first = true
                screen.children.forEach { category ->
                    if (category.title.isNotBlank()) {
                        item(key = "category_${category.title}") {
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                    }
                    items(category.children, key = { it.key() }) { pref ->
                        val isFirst = first
                        first = false
                        PrefRow(
                            pref = pref,
                            version = version,
                            context = context,
                            navController = navController,
                            modifier = if (isFirst) Modifier.focusRequester(firstFocus) else Modifier,
                            onChanged = { version++ },
                            onChoose = { choosing = pref },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrefRow(
    pref: Pref<*, *, *>,
    // only here so the row recomposes (and re-reads its value) after a change
    @Suppress("UNUSED_PARAMETER") version: Int,
    context: Context,
    navController: NavController?,
    modifier: Modifier,
    onChanged: () -> Unit,
    onChoose: () -> Unit,
) {
    val sp = PreferenceManager.sharedPreference
    when (pref) {
        is BooleanPref -> {
            var checked by remember(pref, version) { mutableStateOf(pref.getValue(sp)) }
            SettingRow(
                title = pref.title,
                summary = pref.summary,
                trailing = { ToggleIndicator(checked) },
                onClick = {
                    checked = !checked
                    pref.save(sp, checked)
                    onChanged()
                },
                modifier = modifier,
            )
        }
        is IntSeekbarPref -> {
            var value by remember(pref, version) { mutableIntStateOf(pref.getValue(sp)) }
            SettingRow(
                title = pref.title,
                summary = pref.summary,
                trailing = { Text("${value}", style = MaterialTheme.typography.titleMedium) },
                onClick = {},
                modifier = modifier.onPreviewKeyEvent {
                    val delta = when (it.key) {
                        Key.DirectionLeft -> -SEEKBAR_STEP
                        Key.DirectionRight -> SEEKBAR_STEP
                        else -> 0
                    }
                    if (delta == 0) {
                        false
                    } else {
                        if (it.type == KeyEventType.KeyDown) {
                            value = (value + delta).coerceIn(SEEKBAR_MIN, SEEKBAR_MAX)
                            pref.save(sp, value)
                            onChanged()
                        }
                        true
                    }
                },
            )
        }
        else -> {
            val isChoice = choicesOf(context, pref) != null
            SettingRow(
                title = pref.title,
                summary = if (isChoice || pref is ActionPref || pref === USER_ID) summaryOf(context, pref) else pref.summary,
                trailing = null,
                onClick = {
                    if (isChoice) {
                        onChoose()
                    } else if (navController != null) {
                        pref.onClick(context, navController)
                    }
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    summary: String?,
    trailing: (@Composable () -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.surface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = ImmichFocus.ButtonScale),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!summary.isNullOrBlank()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (trailing != null) {
                Box(Modifier.padding(start = 16.dp)) { trailing() }
            }
        }
    }
}

@Composable
private fun ToggleIndicator(checked: Boolean) {
    val track by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "toggleTrack",
    )
    Box(
        Modifier
            .width(44.dp)
            .height(24.dp)
            .background(track, CircleShape),
    ) {
        Box(
            Modifier
                .offset(x = if (checked) 22.dp else 2.dp, y = 2.dp)
                .size(20.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
        )
    }
}

@Composable
private fun SelectedDot() {
    Box(
        Modifier
            .size(14.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
    )
}
