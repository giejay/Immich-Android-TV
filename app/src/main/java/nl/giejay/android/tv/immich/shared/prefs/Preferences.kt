package nl.giejay.android.tv.immich.shared.prefs;

import android.content.SharedPreferences
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.screensaver.ScreenSaverType
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
data object SCREENSAVER_SET: ActionPref("Set Immich as screensaver", "Set Immich as screensaver", {
    // todo
})

data object SCREENSAVER_INTERVAL : IntListPref(3, "Interval", "Interval of the screensaver", R.array.interval_titles, R.array.interval_values)
data object SCREENSAVER_SHOW_MEDIA_COUNT : BooleanPref(true, "Show media count", "Show the number of total items and currently selected item")
data object SCREENSAVER_SHOW_DESCRIPTION : BooleanPref(true, "Show description", "Show description of asset in screensaver")
data object SCREENSAVER_SHOW_ALBUM_NAME : BooleanPref(true, "Show album name", "Show album name of asset in screensaver")
data object SCREENSAVER_SHOW_DATE : BooleanPref(true, "Show date", "Show date of asset in screensaver")
data object SCREENSAVER_SHOW_CLOCK : BooleanPref(true, "Show clock", "Show clock in screensaver")
data object SCREENSAVER_ANIMATE_ASSET_SLIDE : BooleanPref(true, "Slide the new asset in", "Slide the new asset in when transitioning")
data object SCREENSAVER_ALBUMS : StringSetPref(mutableSetOf(), "Set albums to show in screensaver", "Set albums to show in screensaver")
data object SCREENSAVER_INCLUDE_VIDEOS : BooleanPref(false, "Include videos", "Include videos in screensaver")
data object SCREENSAVER_PLAY_SOUND : BooleanPref(false, "Play sound", "Play sound of videos during screensaver")
data object SCREENSAVER_TYPE : EnumPref<ScreenSaverType>(ScreenSaverType.RECENT, "Screensaver type", "What to show: albums, random, recent etc.", R.array.screensaver_type_keys, R.array.screensaver_type_values) {
    override fun parse(any: Any?): ScreenSaverType {
        return ScreenSaverType.valueOf(any as String)
    }
}

// slider/viewer
data object SLIDER_INTERVAL : IntListPref(3, "Interval", "Interval of the slideshow", R.array.interval_titles, R.array.interval_values)
data object SLIDER_ANIMATION_SPEED : IntListPref(0, "Slide animation speed (ms)", "Slide animation speed in milliseconds", R.array.animation_speed_ms, R.array.animation_speed_ms)
data object SLIDER_SHOW_DESCRIPTION : BooleanPref(true, "Show description", "Show description of asset in slideshow")
data object SLIDER_SHOW_MEDIA_COUNT : BooleanPref(true, "Show media count", "Show the number of total items and currently selected item")
data object SLIDER_SHOW_DATE : BooleanPref(false, "Show date", "Show date of asset in slideshow")
data object SLIDER_SHOW_CITY : BooleanPref(true, "Show city", "Show city of asset in slideshow")
data object SLIDER_ONLY_USE_THUMBNAILS : BooleanPref(true, "Play sound", "Play sound of videos")
data object SLIDER_MERGE_PORTRAIT_PHOTOS : BooleanPref(true, "Merge portrait photos", "Show two portrait photos next to each other")
data object SLIDER_MAX_CUT_OFF_WIDTH : IntSeekbarPref(20, "Safe Center Crop max cutoff height %", "Maximum percentage to cut off image height if using SafeCenterCrop")
data object SLIDER_MAX_CUT_OFF_HEIGHT : IntSeekbarPref(20, "Safe Center Crop max cutoff width %", "Maximum percentage to cut off image width if using SafeCenterCrop")
data object SLIDER_GLIDE_TRANSFORMATION : EnumPref<GlideTransformations>(GlideTransformations.CENTER_INSIDE,
    "Photo transformation",
    "Photo transformation", R.array.glide_transformation_labels, R.array.glide_transformation_keys) {
    override fun parse(any: Any?): GlideTransformations {
        return GlideTransformations.valueOfSafe(any as String, defaultValue)
    }
}


// other
data object ALBUMS_SORTING : EnumPref<AlbumsOrder>(AlbumsOrder.LAST_UPDATED, "Albums", "Set the order in which albums should appear", R.array.albums_order, R.array.albums_order_keys) {
    override fun parse(any: Any?): AlbumsOrder {
        return AlbumsOrder.valueOfSafe(any as String, defaultValue)
    }
}

data object PHOTOS_SORTING : EnumPref<PhotosOrder>(PhotosOrder.OLDEST_NEWEST,
    "Photos in albums",
    "Set the order in which photos should appear inside albums", R.array.photos_order, R.array.photos_order_keys) {
    override fun parse(any: Any?): PhotosOrder {
        return PhotosOrder.valueOfSafe(any as String, defaultValue)
    }
}

data object ALL_ASSETS_SORTING : EnumPref<PhotosOrder>(PhotosOrder.NEWEST_OLDEST,
    "Photos",
    "Set the order in which photos should appear in the Photos tab", R.array.all_assets_order, R.array.all_assets_order_keys) {
    override fun parse(any: Any?): PhotosOrder {
        return PhotosOrder.valueOfSafe(any as String, defaultValue)
    }
}

// other
data object DEBUG_MODE : BooleanPref(false, "Enable debug mode", "Enable this if you are experiencing issues.")
data object LOAD_BACKGROUND_IMAGE : BooleanPref(true, "Load selected item as background", "Load the currently selected image/album as the background")
data object HIDDEN_HOME_ITEMS : StringSetPref(emptySet(), "", "")
data object SIMILAR_ASSETS_YEARS_BACK : IntListPref(10, "Seasonal photos years back", "How many years to go back when selecting seasonal photos", R.array.similar_assets_years_back, R.array.similar_assets_years_back)
data object SIMILAR_ASSETS_PERIOD_DAYS : IntListPref(30, "Seasonal photos period in days", "Seasonal photos period in days", R.array.similar_assets_period_days, R.array.similar_assets_period_days)
data object RECENT_ASSETS_MONTHS_BACK : IntListPref(5, "Recent photos months back", "Recent photos months back", R.array.recent_assets_months_back, R.array.recent_assets_months_back)
data object USER_ID : StringPref("", "User ID", "Your user id, needed for debugging")
