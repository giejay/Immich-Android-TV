package nl.giejay.mediaslider.plugin

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import com.zeuskartik.mediaslider.R

class ExternalPlayerButtonControllerPlugin(
    private val placement: ControllerButtonPlacement = ControllerButtonPlacement.RIGHT_OF,
    private val anchorViewId: Int = R.id.image_slideshow
) : SliderControllerPlugin {
    override fun provideControllerButton(context: ControllerPluginContext): ControllerButtonSpec? {
        val canOpenExternally = context.isVideo && !context.sliderItem.url.isNullOrBlank()
        if (!canOpenExternally) {
            return null
        }

        val externalPlayerButton = createDefaultControllerButton(
            context = context,
            contentDescriptionRes = R.string.open_in_external_player,
            iconRes = R.drawable.ic_open_external
        )
        externalPlayerButton.visibility = View.VISIBLE
        externalPlayerButton.setOnClickListener {
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
            button = externalPlayerButton,
            placement = placement,
            anchorViewId = anchorViewId
        )
    }
}


