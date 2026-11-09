package nl.giejay.android.tv.immich.ui.dialog

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import com.github.alexzhirkevich.customqrgenerator.QrData
import com.github.alexzhirkevich.customqrgenerator.vector.QrCodeDrawable
import nl.giejay.android.tv.immich.BuildConfig
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.ui.auth.AuthButton
import nl.giejay.android.tv.immich.ui.immichComposeView

/**
 * Shows [content] in a transparent dialog window (Material 3 card on a dimmed screen). The dialog is a
 * [ComponentDialog], so Compose gets its lifecycle and saved state owners; Back closes it.
 */
fun showComposeDialog(context: Context, content: @Composable (dismiss: () -> Unit) -> Unit): ComponentDialog {
    val dialog = ComponentDialog(context)
    dialog.window?.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
    dialog.setContentView(
        immichComposeView(context) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { content { dialog.dismiss() } }
        },
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
    )
    // the window spans the screen width, the card inside is as wide as its content needs (up to a limit)
    dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    dialog.show()
    return dialog
}

/** Title, body and a Close button on a rounded surface; the button has focus from the start. */
@Composable
fun ImmichDialogCard(
    title: String?,
    closeLabel: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try {
            closeFocus.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier
                .widthIn(max = 900.dp)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraLarge)
                // the text is not focusable, so Up/Down on the remote scroll it while it overflows
                .onPreviewKeyEvent { event ->
                    val down = event.key == Key.DirectionDown
                    val up = event.key == Key.DirectionUp
                    val canScroll = (down && scroll.canScrollForward) || (up && scroll.canScrollBackward)
                    if (canScroll && event.type == KeyEventType.KeyDown) {
                        scope.launch { scroll.animateScrollBy(if (down) SCROLL_STEP else -SCROLL_STEP) }
                    }
                    canScroll
                }
                .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(scroll)) { content() }
            Spacer(Modifier.height(16.dp))
            AuthButton(closeLabel, onClose, Modifier.focusRequester(closeFocus))
        }
    }
}

/** Steps for enabling the screensaver by hand when the device has no settings screen for it. */
fun showScreenSaverAdbDialog(context: Context) {
    showComposeDialog(context) { dismiss ->
        val command = "adb shell settings put secure screensaver_components " +
            "${BuildConfig.APPLICATION_ID}/nl.giejay.android.tv.immich.screensaver.ScreenSaverService"
        ImmichDialogCard(context.getString(R.string.screensaver_not_possible_title), context.getString(R.string.close), dismiss) {
            Text(context.getString(R.string.screensaver_adb_intro), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            listOf(
                R.string.screensaver_adb_step1,
                R.string.screensaver_adb_step2,
                R.string.screensaver_adb_step3,
                R.string.screensaver_adb_step4,
            ).forEachIndexed { index, res ->
                Text("${index + 1}. ${context.getString(res)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
            }
            Text("5. ${context.getString(R.string.screensaver_adb_step5)}", style = MaterialTheme.typography.bodyMedium)
            Box(
                Modifier.padding(vertical = 8.dp).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium).padding(12.dp),
            ) {
                Text(command, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(context.getString(R.string.screensaver_adb_more, "https://github.com/daniii88/Immich-Android-TV-Next"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The user id as a QR code (white card for the scanner's contrast) and as text. */
fun showUserIdDialog(context: Context, userId: String) {
    showComposeDialog(context) { dismiss ->
        val qr: ImageBitmap = remember(userId) { QrCodeDrawable(QrData.Text(userId)).toBitmap(QR_PIXELS, QR_PIXELS).asImageBitmap() }
        ImmichDialogCard(context.getString(R.string.user_id), context.getString(R.string.close), dismiss) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(300.dp).background(Color.White, MaterialTheme.shapes.extraLarge).padding(16.dp)) {
                    Image(qr, contentDescription = context.getString(R.string.user_id), modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(16.dp))
                Text(userId, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
        }
    }
}

private const val QR_PIXELS = 512
private const val SCROLL_STEP = 240f
