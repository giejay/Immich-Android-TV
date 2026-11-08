package nl.giejay.android.tv.immich.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import nl.giejay.android.tv.immich.ui.theme.ImmichTheme

/**
 * Creates a [ComposeView] wrapped in [ImmichTheme] for hosting Compose inside Leanback/View code
 * during the migration. Disposes the composition with the view's lifecycle owner so it is safe in
 * fragments and RecyclerView items.
 */
fun immichComposeView(context: Context, content: @Composable () -> Unit): ComposeView =
    ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { ImmichTheme(content) }
    }
