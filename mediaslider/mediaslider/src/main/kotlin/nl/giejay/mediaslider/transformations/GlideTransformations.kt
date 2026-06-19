package nl.giejay.mediaslider.transformations

import android.content.Context
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CenterInside
import nl.giejay.mediaslider.config.MediaSliderConfiguration

enum class GlideTransformations(val transform: (Context, MediaSliderConfiguration, (String) -> Unit) -> BitmapTransformation) {
    CENTER_CROP({ _, _, _ -> CenterCrop() }),
    CENTER_INSIDE({ _, _, _ -> CenterInside() }),
    SAFE_CENTER_CROP({ context, config, position -> SafeCenterCrop(context, config.maxCutOffHeight, config.maxCutOffWidth, position) });

    companion object {
        fun valueOfSafe(name: String, default: GlideTransformations): GlideTransformations {
            return entries.find { it.toString() == name } ?: default
        }
    }

}