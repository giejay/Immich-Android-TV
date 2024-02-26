//package nl.giejay.android.tv.immich.shared
//
//import android.content.Context
//import com.bumptech.glide.Glide
//import com.bumptech.glide.GlideBuilder
//import com.bumptech.glide.Registry
//import com.bumptech.glide.annotation.GlideModule
//import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
//import com.bumptech.glide.load.model.GlideUrl
//import com.bumptech.glide.module.AppGlideModule
//import nl.giejay.android.tv.immich.api.ApiClientFactory
//import nl.giejay.android.tv.immich.shared.prefs.LiveSharedPreferences
//import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
//import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager.KEY_API_KEY
//import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager.KEY_DEBUG_MODE
//import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager.KEY_DISABLE_SSL_VERIFICATION
//import java.io.InputStream
//
//
//@GlideModule
//class CustomGlideModule : AppGlideModule() {
//    private var factory: OkHttpUrlLoader.Factory = OkHttpUrlLoader.Factory()
//    private var registry: Registry? = null
//    private val prefs: LiveSharedPreferences =
//        LiveSharedPreferences(PreferenceManager.sharedPreference)
//
//    init {
//        prefs.listenMultiple(listOf(KEY_API_KEY, KEY_DEBUG_MODE, KEY_DISABLE_SSL_VERIFICATION))
//            .observeForever {
//                reloadFactory(
//                    it[KEY_API_KEY] as String,
//                    it[KEY_DISABLE_SSL_VERIFICATION] == true,
//                    it[KEY_DEBUG_MODE] == true
//                )
//            }
//    }
//
//    private fun reloadFactory(apiKey: String, disableSsl: Boolean, debugMode: Boolean) {
//        factory = if (PreferenceManager.isLoggedId()) {
//            OkHttpUrlLoader.Factory(
//                ApiClientFactory.getClient(
//                    disableSsl,
//                    apiKey,
//                    false
//                )
//            )
//        } else {
//            OkHttpUrlLoader.Factory()
//        }
////        if (this.registry != null) {
////            this.registry!!.replace(
////                GlideUrl::class.java,
////                InputStream::class.java, factory
////            )
////        }
//    }
//
//    override fun applyOptions(context: Context, builder: GlideBuilder) {
//    }
//
//    override fun registerComponents(
//        context: Context, glide: Glide, registry: Registry
//    ) {
//        this.registry = registry
//        // override default loader with one that attaches headers
//        registry.replace(
//            GlideUrl::class.java,
//            InputStream::class.java, factory
//        )
//    }
//}