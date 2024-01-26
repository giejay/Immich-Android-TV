package nl.giejay.android.tv.immich.shared

import android.content.Context
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.Headers
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.load.model.stream.BaseGlideUrlLoader
import com.bumptech.glide.module.AppGlideModule
import nl.giejay.android.tv.immich.shared.prefs.LivePreference
import java.io.InputStream


@GlideModule
class CustomGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
    }

    override fun registerComponents(
        context: Context, glide: Glide, registry: Registry
    ) {
        // override default loader with one that attaches headers
        registry.replace(
            String::class.java,
            InputStream::class.java, Loader.Factory()
        )
    }

    private class Loader(concreteLoader: ModelLoader<GlideUrl?, InputStream?>?) :
        BaseGlideUrlLoader<String?>(concreteLoader) {

        override fun handles(model: String): Boolean {
            return true
        }

        override fun getHeaders(
            model: String?,
            width: Int,
            height: Int,
            options: Options?
        ): Headers {
            val apiKey = PreferenceManager.apiKey()
            if(apiKey.isNotEmpty()){
                return LazyHeaders.Builder()
                    .addHeader("x-api-key", apiKey)
                    .build()
            }
            return Headers.DEFAULT

        }

        class Factory :
            ModelLoaderFactory<String?, InputStream?> {
            override fun build(
                multiFactory: MultiModelLoaderFactory
            ): ModelLoader<String?, InputStream?> {
                return Loader(
                    multiFactory.build(
                        GlideUrl::class.java,
                        InputStream::class.java
                    )
                )
            }

            override fun teardown() { /* nothing to free */
            }
        }

        override fun getUrl(model: String?, width: Int, height: Int, options: Options?): String {
            return model!!
        }
    }
}