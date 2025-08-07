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
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.album.AlbumFragmentDirections
import nl.giejay.android.tv.immich.album.SelectionType
import nl.giejay.android.tv.immich.screensaver.ScreenSaverType
import nl.giejay.android.tv.immich.settings.MetaDataCustomizerFragmentDirections
import nl.giejay.mediaslider.transformations.GlideTransformations

// general
data object DISABLE_SSL_VERIFICATION : BooleanPref(false,
    "Disable SSL Verification",
    "Only use this when you have issues with self signed certificates!") {
    override fun key() = "disableSSLVerification"
}

data object API_KEY : StringPref("", "API Key", "API Key") {
    override fun key() = "apiKey"
}

data object HOST_NAME : StringPref("", "Server URL", "Server URL") {
    override fun key() = "hostName"

    override fun save(sharedPreferences: SharedPreferences, value: String) {
        super.save(sharedPreferences, value.replace(Regex("/$"), ""))
    }
}

// screensaver
private val SCREENSAVER_SETTINGS = "android.settings.DREAM_SETTINGS"

data object SCREENSAVER_SET : ActionPref("Set Immich as screensaver", "Set Immich as screensaver", { context, navController ->
    if (PreferenceManager.get(SCREENSAVER_TYPE) == ScreenSaverType.ALBUMS && PreferenceManager.get(SCREENSAVER_ALBUMS).isEmpty()) {
        Toast.makeText(
            context,
            "Please set your albums to show first!",
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
                .setTitle("Not possible to set screensaver")
                .create()
            dialog.setView(inflate)
            dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Close") { d, _ ->
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

data object SCREENSAVER_INTERVAL : IntListPref(3, "Interval", "Interval of the screensaver", R.array.interval_titles, R.array.interval_values)
data object SCREENSAVER_SHOW_MEDIA_COUNT : BooleanPref(true, "Show media count", "Show the number of total items and currently selected item")
data object SCREENSAVER_SHOW_DESCRIPTION : BooleanPref(true, "Show description", "Show description of asset in screensaver")
data object SCREENSAVER_SHOW_ALBUM_NAME : BooleanPref(true, "Show album name", "Show album name of asset in screensaver")
data object SCREENSAVER_SHOW_DATE : BooleanPref(true, "Show date", "Show date of asset in screensaver")
data object SCREENSAVER_SHOW_CLOCK : BooleanPref(true, "Show clock", "Show clock in screensaver")
data object SCREENSAVER_ANIMATE_ASSET_SLIDE : BooleanPref(true, "Slide the new asset in", "Slide the new asset in when transitioning")
data object SCREENSAVER_ALBUMS : StringSetPref(mutableSetOf(), "Set albums to show in screensaver", "Set albums to show in screensaver") {
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

data object SCREENSAVER_METADATA_CUSTOMIZER : ActionPref("Customize metadata", "Configure what to show in screensaver", { _, navController ->
    navController.navigate(
        MetaDataCustomizerFragmentDirections.actionToMetadataFragment(MetaDataScreen.SCREENSAVER)
    )
    true
})

data object SCREENSAVER_INCLUDE_VIDEOS : BooleanPref(false, "Include videos", "Include videos in screensaver")
data object SCREENSAVER_PLAY_SOUND : BooleanPref(false, "Play sound", "Play sound of videos during screensaver")
data object SCREENSAVER_TYPE : EnumByTitlePref<ScreenSaverType>(ScreenSaverType.RECENT,
    "Screensaver type",
    "What to show: albums, random, recent etc.") {

    override fun fromPrefValue(prefValue: String): ScreenSaverType {
        return ScreenSaverType.valueOf(prefValue)
    }

    override fun getEnumEntries(): Array<ScreenSaverType> {
        return ScreenSaverType.entries.toTypedArray()
    }
}

// slider/viewer
data object SLIDER_INTERVAL : IntListPref(3, "Interval", "Interval of the slideshow", R.array.interval_titles, R.array.interval_values)
data object SLIDER_ANIMATION_SPEED : IntListPref(0,
    "Slide animation speed (ms)",
    "Slide animation speed in milliseconds",
    R.array.animation_speed_ms,
    R.array.animation_speed_ms)

data object SLIDER_SHOW_DESCRIPTION : BooleanPref(true, "Show description", "Show description of asset in slideshow")
data object SLIDER_SHOW_MEDIA_COUNT : BooleanPref(true, "Show media count", "Show the number of total items and currently selected item")
data object SLIDER_SHOW_DATE : BooleanPref(false, "Show date", "Show date of asset in slideshow")
data object SLIDER_SHOW_CITY : BooleanPref(true, "Show city", "Show city of asset in slideshow")
data object SLIDER_METADATA_CUSTOMIZER : ActionPref("Customize metadata", "Configure what to show in viewer", { _, navController ->
    navController.navigate(
        MetaDataCustomizerFragmentDirections.actionToMetadataFragment(MetaDataScreen.VIEWER)
    )
    true
})

data object SLIDER_ONLY_USE_THUMBNAILS : BooleanPref(true, "Use high resolution thumbnails", "Use high resolution thumbnails instead of native/full images. Will dramatically speed up loading.")
data object SLIDER_MERGE_PORTRAIT_PHOTOS : BooleanPref(true, "Merge portrait photos", "Show two portrait photos next to each other")
data object SLIDER_MAX_CUT_OFF_WIDTH : IntSeekbarPref(20,
    "Safe Center Crop max cutoff height %",
    "Maximum percentage to cut off image height if using SafeCenterCrop")

data object SLIDER_MAX_CUT_OFF_HEIGHT : IntSeekbarPref(20,
    "Safe Center Crop max cutoff width %",
    "Maximum percentage to cut off image width if using SafeCenterCrop")

data object SLIDER_GLIDE_TRANSFORMATION : EnumPref<GlideTransformations>(GlideTransformations.CENTER_INSIDE,
    "Photo transformation",
    "Photo transformation", R.array.glide_transformation_labels, R.array.glide_transformation_keys) {
    override fun fromPrefValue(prefValue: String): GlideTransformations {
        return GlideTransformations.valueOfSafe(prefValue, defaultValue)
    }
}


// other
data object ALBUMS_SORTING : EnumByTitlePref<AlbumsOrder>(AlbumsOrder.LAST_UPDATED,
    "Albums",
    "Set the order in which albums should appear") {

    override fun fromPrefValue(prefValue: String): AlbumsOrder {
        return AlbumsOrder.valueOfSafe(prefValue, defaultValue)
    }

    override fun getEnumEntries(): Array<AlbumsOrder> {
        return AlbumsOrder.entries.toTypedArray();
    }
}

data object PHOTOS_SORTING : EnumByTitlePref<PhotosOrder>(PhotosOrder.OLDEST_NEWEST,
    "Photos in albums",
    "Set the order in which photos should appear inside albums") {
    override fun fromPrefValue(prefValue: String): PhotosOrder {
        return PhotosOrder.valueOfSafe(prefValue, defaultValue)
    }

    override fun getEnumEntries(): Array<PhotosOrder> {
        return PhotosOrder.entries.toTypedArray()
    }
}

data class PHOTOS_SORTING_FOR_SPECIFIC_ALBUM(val albumId: String, val albumName: String) : EnumByTitlePref<PhotosOrder>(PreferenceManager.get(
    PHOTOS_SORTING),
    "Order",
    "Set the order in which photos should appear") {
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
    "Content type",
    "Filter by content type") {

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
    "Content type",
    "Filter by content type") {

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
    "Assets outside albums",
    "Set the order in which assets should appear outside Albums") {
    override fun fromPrefValue(prefValue: String): PhotosOrder {
        return PhotosOrder.valueOfSafe(prefValue, defaultValue)
    }

    override fun getEnumEntries(): Array<PhotosOrder> {
        return PhotosOrder.entries.toTypedArray()
    }
}

// other
data object DEBUG_MODE : BooleanPref(false, "Enable debug mode", "Enable this if you are experiencing issues.")
data object LOAD_BACKGROUND_IMAGE : BooleanPref(true, "Load selected item as background", "Load the currently selected image/album as the background")
data object HIDDEN_HOME_ITEMS : StringSetPref(emptySet(), "", "")
data object USER_ID : NotUserEditableStringPref("User ID", "Your user id, needed for debugging")

// seasonal/random/recents
data object SIMILAR_ASSETS_YEARS_BACK : IntListPref(10,
    "Seasonal photos years back",
    "How many years to go back when selecting seasonal photos",
    R.array.similar_assets_years_back,
    R.array.similar_assets_years_back)

data object SIMILAR_ASSETS_PERIOD_DAYS : IntListPref(30,
    "Seasonal photos period in days",
    "Seasonal photos period in days",
    R.array.similar_assets_period_days,
    R.array.similar_assets_period_days)

data object RECENT_ASSETS_MONTHS_BACK : IntListPref(5,
    "Recent photos months back",
    "Recent photos months back",
    R.array.recent_assets_months_back,
    R.array.recent_assets_months_back)

data object EXCLUDE_ASSETS_IN_ALBUM : StringSetPref(emptySet(), "Excluded albums", "Exclude assets in specific albums for random/seasonal view") {
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
data object ViewPrefScreen : PrefScreen("View Settings", "view",
    listOf(
        PrefCategory("Ordering",
            listOf(
                ALBUMS_SORTING,
                PHOTOS_SORTING,
                ALL_ASSETS_SORTING)
        ),
        PrefCategory("Slideshow", listOf(
            SLIDER_ONLY_USE_THUMBNAILS,
            SLIDER_MERGE_PORTRAIT_PHOTOS,
            SLIDER_METADATA_CUSTOMIZER,
            SLIDER_INTERVAL,
            SLIDER_ANIMATION_SPEED,
            SLIDER_GLIDE_TRANSFORMATION,
            SLIDER_MAX_CUT_OFF_WIDTH,
            SLIDER_MAX_CUT_OFF_HEIGHT)),
        PrefCategory("Other", listOf(
            SIMILAR_ASSETS_YEARS_BACK,
            SIMILAR_ASSETS_PERIOD_DAYS,
            RECENT_ASSETS_MONTHS_BACK,
            EXCLUDE_ASSETS_IN_ALBUM,
            LOAD_BACKGROUND_IMAGE))
    )
)

data object ScreensaverPrefScreen : PrefScreen("Screensaver Settings", "screensaver",
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

data object DebugPrefScreen : PrefScreen("Debug Settings", "debug", listOf(PrefCategory("", listOf(DEBUG_MODE, USER_ID))), { prefManager ->
    prefManager.findPreference<Preference>(USER_ID.key())?.summary = PreferenceManager.get(USER_ID)
})

data class AlbumDetailsSettingsScreen(val albumId: String, val albumName: String) : PrefScreen("Settings for $albumName",
    "album_settings_${albumId}",
    listOf(
        PrefCategory("Ordering", listOf(PHOTOS_SORTING_FOR_SPECIFIC_ALBUM(albumId, albumName))),
        PrefCategory("Filtering", listOf(FILTER_CONTENT_TYPE_FOR_SPECIFIC_ALBUM(albumId, albumName)))
    )
)

data object GenericAssetsSettingsScreen : PrefScreen("Settings",
    "generic_assets_settings",
    listOf(
        PrefCategory("Ordering", listOf(ALL_ASSETS_SORTING)),
        PrefCategory("Filtering", listOf(FILTER_CONTENT_TYPE))
    )
)