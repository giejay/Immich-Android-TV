package nl.giejay.mediaslider.plugin

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import android.widget.Toast
import com.zeuskartik.mediaslider.R
import nl.giejay.mediaslider.controller.ControllerIds

class ExternalPlayerButtonControllerPlugin(
    private val placement: ControllerButtonPlacement = ControllerButtonPlacement.RIGHT_OF,
    private val anchorId: String = ControllerIds.SLIDESHOW
) : SliderControllerPlugin {
    override fun provideControllerButton(context: ControllerPluginContext): ControllerButtonSpec? {
        val canOpenExternally = context.isVideo && !context.sliderItem.url.isNullOrBlank()
        if (!canOpenExternally) {
            return null
        }

        val onClick: () -> Unit = {
            val url = context.sliderItem.url
            if (!url.isNullOrBlank()) {
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(url), "video/*")
                }
                val chooser = Intent.createChooser(
                    viewIntent,
                    context.context.getString(R.string.open_in_external_player)
                )
                if (context.context !is Activity) {
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.context.startActivity(chooser)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context.context, R.string.no_external_player_found, Toast.LENGTH_SHORT).show()
                }
            }
        }
        return ControllerButtonSpec(
            description = context.context.getString(R.string.open_in_external_player),
            icon = mutableStateOf(R.drawable.ic_open_external),
            onClick = onClick,
            placement = placement,
            anchorId = anchorId
        )
    }
}


