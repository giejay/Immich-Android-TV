package nl.giejay.android.tv.immich.shared.prefs;

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.navigation.NavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.album.AlbumFragmentDirections
import nl.giejay.android.tv.immich.album.SelectionType
import nl.giejay.android.tv.immich.screensaver.ScreenSaverType
import nl.giejay.android.tv.immich.settings.MetaDataCustomizerFragmentDirections
import nl.giejay.mediaslider.transformations.GlideTransformations

// general
data object DISABLE_SSL_VERIFICATION : BooleanPref(false,
    ImmichApplication.appContext!!.getString(R.string.disable_ssl),
    ImmichApplication.appContext!!.getString(R.string.disable_ssl_text)) {
    override fun key() = "disableSSLVerification"
}

data object API_KEY : StringPref("", ImmichApplication.appContext!!.getString(R.string.api_key_text), ImmichApplication.appContext!!.getString(R.string.api_key_text)) {
    override fun key() = "apiKey"
}

data object HOST_NAME : StringPref("", ImmichApplication.appContext!!.getString(R.string.server_url_text), ImmichApplication.appContext!!.getString(R.string.server_url_text)) {
    override fun key() = "hostName"

    override fun save(sharedPreferences: SharedPreferences, value: String) {
        super.save(sharedPreferences, value.replace(Regex("/$"), ""))
    }
}

// screensaver
private val SCREENSAVER_SETTINGS = "android.settings.DREAM_SETTINGS"

data object SCREENSAVER_SET : ActionPref(ImmichApplication.appContext!!.getString(R.string.screensaver_set), ImmichApplication.appContext!!.getString(R.string.screensaver_set), { context, navController ->
    if (PreferenceManager.get(SCREENSAVER_TYPE) == ScreenSaverType.ALBUMS && PreferenceManager.get(SCREENSAVER_ALBUMS).isEmpty()) {
        Toast.makeText(
            context,
            context.getString(R.string.screensaver_set_select_albums_first),
            Toast.LENGTH_SHORT
        ).show()
    } else {
        startScreenSaverIntent(context)
    }
    true
})

private fun startScreenSaverIntent(context: Context) {
    // Check if the daydream intent is available - some devices (e.g. NVidia Shield) do not support it
    var intent = Intent(SCREENSAVER_SETTINGS);
    if (!intentAvailable(intent, context)) {
        // Try opening the daydream settings activity directly: https://gist.github.com/reines/bc798a2cb539f51877bb279125092104
        intent = Intent(Intent.ACTION_MAIN).setClassName(
            "com.android.tv.settings",
            "com.android.tv.settings.device.display.daydream.DaydreamActivity"
        );
        if (!intentAvailable(intent, context) || Build.MANUFACTURER == "Google") {
            val layoutInflater =
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val inflate: View = layoutInflater.inflate(R.layout.screensaver_adb, null)
            val dialog = AlertDialog.Builder(context)
                .setTitle(R.string.screensaver_not_possible_title)
                .create()
            dialog.setView(inflate)
            dialog.setButton(AlertDialog.BUTTON_NEGATIVE, ImmichApplication.appContext!!.getString(R.string.close)) { d, _ ->
                d.dismiss()
            }
            return dialog.show()
        }
        context.startActivity(intent);
    }

}

private fun intentAvailable(intent: Intent, context: Context): Boolean {
    val manager = context.packageManager;
    val infos = manager.queryIntentActivities(intent, 0);
    return infos.isNotEmpty()
}

data object SCREENSAVER_INTERVAL : IntListPref(3, ImmichApplication.appContext!!.getString(R.string.interval), ImmichApplication.appContext!!.getString(R.string.screensaver_interval_desc), R.array.interval_titles, R.array.interval_values)
data object SCREENSAVER_SHOW_MEDIA_COUNT : BooleanPref(true, ImmichApplication.appContext!!.getString(R.string.show_media_count), ImmichApplication.appContext!!.getString(R.string.show_media_count_desc_screensaver))
data object SCREENSAVER_SHOW_DESCRIPTION : BooleanPref(true, ImmichApplication.appContext!!.getString(R.string.show_description), ImmichApplication.appContext!!.getString(R.string.show_description_screensaver))
data object SCREENSAVER_SHOW_ALBUM_NAME : BooleanPref(true, ImmichApplication.appContext!!.getString(R.string.show_album_name), ImmichApplication.appContext!!.getString(R.string.show_album_name_desc))
data object SCREENSAVER_SHOW_DATE : BooleanPref(true, ImmichApplication.appContext!!.getString(R.string.show_date), ImmichApplication.appContext!!.getString(R.string.show_date_screensaver))
data object SCREENSAVER_SHOW_CLOCK : BooleanPref(true, ImmichApplication.appContext!!.getString(R.string.show_clock), ImmichApplication.appContext!!.getString(R.string.show_clock_screensaver))
data object SCREENSAVER_ANIMATE_ASSET_SLIDE : BooleanPref(true, ImmichApplication.appContext!!.getString(R.string.slide_new_asset_in), ImmichApplication.appContext!!.getString(R.string.slide_new_asset_in_desc))
data object SCREENSAVER_ALBUMS : StringSetPref(mutableSetOf(), ImmichApplication.appContext!!.getString(R.string.set_albums_screensaver), ImmichApplication.appContext!!.getString(R.string.set_albums_screensaver_desc)) {
    override fun onClick(context: Context, controller: NavController): Boolean {
        controller.navigate(
            AlbumFragmentDirections.actionGlobalAlbumFragment(
                true,
                SelectionType.SET_SCREENSAVER.toString()
            )
        )
        return true
    }
}

data object SCREENSAVER_METADATA_CUSTOMIZER : ActionPref(ImmichApplication.appContext!!.getString(R.string.customize_metadata), ImmichApplication.appContext!!.getString(R.string.configure_metadata_screensaver), { _, navController ->
    navController.navigate(
        MetaDataCustomizerFragmentDirections.actionToMetadataFragment(MetaDataScreen.SCREENSAVER)
    )
    true
})

data object SCREENSAVER_INCLUDE_VIDEOS : BooleanPref(false, ImmichApplication.appContext!!.getString(R.string.include_videos), ImmichApplication.appContext!!.getString(R.string.include_videos_screensaver))
data object SCREENSAVER_PLAY_SOUND : BooleanPref(false, ImmichApplication.appContext!!.getString(R.string.play_sound), ImmichApplication.appContext!!.getString(R.string.play_sound_screensaver))
data object SCREENSAVER_TYPE : EnumByTitlePref<ScreenSaverType>(ScreenSaverType.RECENT,
    ImmichApplication.appContext!!.getString(R.string.screensaver_type),
    ImmichApplication.appContext!!.getString(R.string.screensaver_type_desc)) {

    override fun fromPrefValue(prefValue: String): ScreenSaverType {
        return ScreenSaverType.valueOf(prefValue)
    }

    override fun getEnumEntries(): Array<ScreenSaverType> {
        return ScreenSaverType.entries.toTypedArray()
    }
}

// slider/viewer
data object SLIDER_INTERVAL : IntListPref(3, ImmichApplication.appContext!!.getString(R.string.interval_slideshow), ImmichApplication.appContext!!.getString(R.string.interval_slideshow_desc), R.array.interval_titles, R.array.interval_values)
data object SLIDER_ANIMATION_SPEED : IntListPref(0,
    ImmichApplication.appContext!!.getString(R.string.slide_animation_speed_ms),
    ImmichApplication.appContext!!.getString(R.string.slide_animation_speed_desc),
    R.array.animation_speed_ms,
    R.array.animation_speed_ms)

data object SLIDER_SHOW_DESCRIPTION : BooleanPref(true, ImmichApplication.appContext!!.getString(R.string.show_description_slideshow), ImmichApplication.appContext!!.getString(R.string.show_description_slideshow_desc))
data object SLIDER_SHOW_MEDIA_COUNT : BooleanPref(true, ImmichApplication.appContext!!.getString(R.string.show_media_count_slideshow), ImmichApplication.appContext!!.getString(R.string.show_media_count_slideshow_desc))
data object SLIDER_SHOW_DATE : BooleanPref(false, ImmichApplication.appContext!!.getString(R.string.show_date_slideshow), ImmichApplication.appContext!!.getString(R.string.show_date_slideshow_desc))
data object SLIDER_SHOW_CITY : BooleanPref(true, ImmichApplication.appContext!!.getString(R.string.show_city), ImmichApplication.appContext!!.getString(R.string.show_city_desc))
data object SLIDER_METADATA_CUSTOMIZER : ActionPref(ImmichApplication.appContext!!.getString(R.string.customize_metadata_viewer), ImmichApplication.appContext!!.getString(R.string.configure_metadata_viewer), { _, navController ->
    navController.navigate(
        MetaDataCustomizerFragmentDirections.actionToMetadataFragment(MetaDataScreen.VIEWER)
    )
    true
})

data object SLIDER_ONLY_USE_THUMBNAILS : BooleanPref(true, ImmichApplication.appContext!!.getString(R.string.use_hd_thumbnails) , ImmichApplication.appContext!!.getString(R.string.use_hd_thumbnails_text))
data object SLIDER_MERGE_PORTRAIT_PHOTOS : BooleanPref(true, ImmichApplication.appContext!!.getString(R.string.merge_portrait_photos), ImmichApplication.appContext!!.getString(R.string.merge_portrait_photos_desc))
data object SLIDER_MAX_CUT_OFF_WIDTH : IntSeekbarPref(20,
    ImmichApplication.appContext!!.getString(R.string.safe_center_crop_max_cutoff_height),
    ImmichApplication.appContext!!.getString(R.string.safe_center_crop_max_cutoff_height_desc))

data object SLIDER_MAX_CUT_OFF_HEIGHT : IntSeekbarPref(20,
    ImmichApplication.appContext!!.getString(R.string.safe_center_crop_max_cutoff_width),
    ImmichApplication.appContext!!.getString(R.string.safe_center_crop_max_cutoff_width_desc))

data object SLIDER_GLIDE_TRANSFORMATION : EnumPref<GlideTransformations>(GlideTransformations.CENTER_INSIDE,
    ImmichApplication.appContext!!.getString(R.string.photo_transformation),
    ImmichApplication.appContext!!.getString(R.string.photo_transformation_desc), R.array.glide_transformation_labels, R.array.glide_transformation_keys) {
    override fun fromPrefValue(prefValue: String): GlideTransformations {
        return GlideTransformations.valueOfSafe(prefValue, defaultValue)
    }
}

data object SLIDER_FORCE_ORIGINAL_VIDEO : BooleanPref(false, ImmichApplication.appContext!!.getString(R.string.force_original_video), ImmichApplication.appContext!!.getString(R.string.force_original_video_desc))

// other
data object ALBUMS_SORTING : EnumByTitlePref<AlbumsOrder>(AlbumsOrder.LAST_UPDATED,
    ImmichApplication.appContext!!.getString(R.string.albums),
    ImmichApplication.appContext!!.getString(R.string.albums_sorting_text)) {

    override fun fromPrefValue(prefValue: String): AlbumsOrder {
        return AlbumsOrder.valueOfSafe(prefValue, defaultValue)
    }

    override fun getEnumEntries(): Array<AlbumsOrder> {
        return AlbumsOrder.entries.toTypedArray();
    }
}

data object PHOTOS_SORTING : EnumByTitlePref<PhotosOrder>(PhotosOrder.OLDEST_NEWEST,
    ImmichApplication.appContext!!.getString(R.string.photos_in_albums),
    ImmichApplication.appContext!!.getString(R.string.photos_in_albums_desc_inside)) {
    override fun fromPrefValue(prefValue: String): PhotosOrder {
        return PhotosOrder.valueOfSafe(prefValue, defaultValue)
    }

    override fun getEnumEntries(): Array<PhotosOrder> {
        return PhotosOrder.entries.toTypedArray()
    }
}

data class PHOTOS_SORTING_FOR_SPECIFIC_ALBUM(val albumId: String, val albumName: String) : EnumByTitlePref<PhotosOrder>(PreferenceManager.get(
    PHOTOS_SORTING),
    ImmichApplication.appContext!!.getString(R.string.order),
    ImmichApplication.appContext!!.getString(R.string.photos_sorting_text))  {
    override fun fromPrefValue(prefValue: String): PhotosOrder {
        return PhotosOrder.valueOfSafe(prefValue, defaultValue)
    }

    override fun getEnumEntries(): Array<PhotosOrder> {
        return PhotosOrder.entries.toTypedArray()
    }

    override fun key(): String {
        return "photos_sorting_${albumId}"
    }

    override fun createPreference(context: Context): ListPreference {
        val createPref = super.createPreference(context)
        createPref.summary = PreferenceManager.get(this).getTitle()
        return createPref
    }
}

data class FILTER_CONTENT_TYPE_FOR_SPECIFIC_ALBUM(val albumId: String, val albumName: String) : EnumByTitlePref<ContentType>(ContentType.ALL,
    ImmichApplication.appContext!!.getString(R.string.content_type),
    ImmichApplication.appContext!!.getString(R.string.filter_by_content_type)) {

    override fun fromPrefValue(prefValue: String): ContentType {
        return ContentType.valueOf(prefValue)
    }

    override fun getEnumEntries(): Array<ContentType> {
        return ContentType.entries.toTypedArray()
    }

    override fun key(): String {
        return "filter_content_type_${albumId}"
    }

    override fun createPreference(context: Context): ListPreference {
        val createPref = super.createPreference(context)
        createPref.summary = PreferenceManager.get(this).getTitle()
        return createPref
    }
}

object FILTER_CONTENT_TYPE : EnumByTitlePref<ContentType>(ContentType.ALL,
    ImmichApplication.appContext!!.getString(R.string.content_type),
    ImmichApplication.appContext!!.getString(R.string.filter_by_content_type)) {

    override fun fromPrefValue(prefValue: String): ContentType {
        return ContentType.valueOf(prefValue)
    }

    override fun getEnumEntries(): Array<ContentType> {
        return ContentType.entries.toTypedArray()
    }

    override fun key(): String {
        return "filter_content_type"
    }

    override fun createPreference(context: Context): ListPreference {
        val createPref = super.createPreference(context)
        createPref.summary = PreferenceManager.get(this).getTitle()
        return createPref
    }
}


data object ALL_ASSETS_SORTING : EnumByTitlePref<PhotosOrder>(PhotosOrder.NEWEST_OLDEST,
    ImmichApplication.appContext!!.getString(R.string.assets_outside_albums),
    ImmichApplication.appContext!!.getString(R.string.assets_outside_albums_desc)) {
    override fun fromPrefValue(prefValue: String): PhotosOrder {
        return PhotosOrder.valueOfSafe(prefValue, defaultValue)
    }

    override fun getEnumEntries(): Array<PhotosOrder> {
        return PhotosOrder.entries.toTypedArray()
    }
}

// other
data object DEBUG_MODE : BooleanPref(false, ImmichApplication.appContext!!.getString(R.string.enable_debug_mode), ImmichApplication.appContext!!.getString(R.string.enable_debug_mode_desc))
data object LOAD_BACKGROUND_IMAGE : BooleanPref(true, ImmichApplication.appContext!!.getString(R.string.load_selected_item_as_background), ImmichApplication.appContext!!.getString(R.string.load_selected_item_as_background_desc))
data object HIDDEN_HOME_ITEMS : StringSetPref(emptySet(), "", "")
data object USER_ID : NotUserEditableStringPref(ImmichApplication.appContext!!.getString(R.string.user_id), ImmichApplication.appContext!!.getString(R.string.user_id_desc))

// seasonal/random/recents
data object SIMILAR_ASSETS_YEARS_BACK : IntListPref(10,
    ImmichApplication.appContext!!.getString(R.string.seasonal_photos_years_back),
    ImmichApplication.appContext!!.getString(R.string.seasonal_photos_years_back_desc),
    R.array.similar_assets_years_back,
    R.array.similar_assets_years_back)

data object SIMILAR_ASSETS_PERIOD_DAYS : IntListPref(30,
    ImmichApplication.appContext!!.getString(R.string.seasonal_photos_period_days),
    ImmichApplication.appContext!!.getString(R.string.seasonal_photos_period_days),
    R.array.similar_assets_period_days,
    R.array.similar_assets_period_days)

data object RECENT_ASSETS_MONTHS_BACK : IntListPref(5,
    ImmichApplication.appContext!!.getString(R.string.recent_photos_months_back),
    ImmichApplication.appContext!!.getString(R.string.recent_photos_months_back),
    R.array.recent_assets_months_back,
    R.array.recent_assets_months_back)

data object EXCLUDE_ASSETS_IN_ALBUM : StringSetPref(emptySet(), ImmichApplication.appContext!!.getString(R.string.excluded_albums), ImmichApplication.appContext!!.getString(R.string.excluded_albums_desc)) {
    override fun onClick(context: Context, controller: NavController): Boolean {
        controller.navigate(
            AlbumFragmentDirections.actionGlobalAlbumFragment(
                true,
                SelectionType.EXCLUDED_ALBUMS.toString()

            )
        )
        return true
    }
}

// Building the view
data object ViewPrefScreen : PrefScreen(ImmichApplication.appContext!!.getString(R.string.view_settings), "view",
    listOf(
        PrefCategory(ImmichApplication.appContext!!.getString(R.string.ordering),
            listOf(
                ALBUMS_SORTING,
                PHOTOS_SORTING,
                ALL_ASSETS_SORTING)
        ),
        PrefCategory(ImmichApplication.appContext!!.getString(R.string.slideshow), listOf(
            SLIDER_ONLY_USE_THUMBNAILS,
            SLIDER_FORCE_ORIGINAL_VIDEO,
            SLIDER_MERGE_PORTRAIT_PHOTOS,
            SLIDER_METADATA_CUSTOMIZER,
            SLIDER_INTERVAL,
            SLIDER_ANIMATION_SPEED,
            SLIDER_GLIDE_TRANSFORMATION,
            SLIDER_MAX_CUT_OFF_WIDTH,
            SLIDER_MAX_CUT_OFF_HEIGHT)),
        PrefCategory(ImmichApplication.appContext!!.getString(R.string.other), listOf(
            SIMILAR_ASSETS_YEARS_BACK,
            SIMILAR_ASSETS_PERIOD_DAYS,
            RECENT_ASSETS_MONTHS_BACK,
            EXCLUDE_ASSETS_IN_ALBUM,
            LOAD_BACKGROUND_IMAGE))
    )
)

data object ScreensaverPrefScreen : PrefScreen(ImmichApplication.appContext!!.getString(R.string.screensaver_settings), "screensaver",
    listOf(
        PrefCategory("",
            listOf(
                SCREENSAVER_SET,
                SCREENSAVER_INTERVAL,
                SCREENSAVER_TYPE,
                SCREENSAVER_ALBUMS,
                SCREENSAVER_METADATA_CUSTOMIZER,
                SCREENSAVER_INCLUDE_VIDEOS,
                SCREENSAVER_PLAY_SOUND,
                SCREENSAVER_ANIMATE_ASSET_SLIDE)
        )
    )
)

data object DebugPrefScreen : PrefScreen(ImmichApplication.appContext!!.getString(R.string.debug_settings), "debug", listOf(PrefCategory("", listOf(DEBUG_MODE, USER_ID))), { prefManager ->
    prefManager.findPreference<Preference>(USER_ID.key())?.summary = PreferenceManager.get(USER_ID)
})

data class AlbumDetailsSettingsScreen(val albumId: String, val albumName: String) : PrefScreen(ImmichApplication.appContext!!.getString(R.string.settings_for, albumName),
    "album_settings_${albumId}",
    listOf(
        PrefCategory(ImmichApplication.appContext!!.getString(R.string.ordering), listOf(PHOTOS_SORTING_FOR_SPECIFIC_ALBUM(albumId, albumName))),
        PrefCategory(ImmichApplication.appContext!!.getString(R.string.filtering), listOf(FILTER_CONTENT_TYPE_FOR_SPECIFIC_ALBUM(albumId, albumName)))
    )
)

data object GenericAssetsSettingsScreen : PrefScreen(ImmichApplication.appContext!!.getString(R.string.settings),
    "generic_assets_settings",
    listOf(
        PrefCategory(ImmichApplication.appContext!!.getString(R.string.ordering), listOf(ALL_ASSETS_SORTING)),
        PrefCategory(ImmichApplication.appContext!!.getString(R.string.filtering), listOf(FILTER_CONTENT_TYPE))
    )
)
