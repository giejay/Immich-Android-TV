package nl.giejay.mediaslider.model

import android.content.Context
import com.zeuskartik.mediaslider.R

enum class MetaDataType(val titleResId: Int, val defaultFontSize: Int) {
    ALBUM_NAME(R.string.album_name, 18),
    CAMERA(R.string.camera, 18),
    CITY(R.string.city, 18),
    CLOCK(R.string.clock, 48),
    COUNTRY(R.string.country, 18),
    DATE(R.string.date, 18),
    DESCRIPTION(R.string.description, 18),
    FILENAME(R.string.filename, 18),
    FILEPATH(R.string.filepath, 18),
    MEDIA_COUNT(R.string.media_count, 18),
    PEOPLE(R.string.people, 18);

    fun getTitle(context: Context): String {
        return context.getString(titleResId)
    }
}
