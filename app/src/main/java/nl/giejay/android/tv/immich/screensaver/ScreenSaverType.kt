package nl.giejay.android.tv.immich.screensaver

import nl.giejay.android.tv.immich.shared.prefs.EnumWithTitle
import nl.giejay.android.tv.immich.ImmichApplication
import nl.giejay.android.tv.immich.R

enum class ScreenSaverType : EnumWithTitle {
    ALBUMS {
        override fun getTitle(): String {
            return ImmichApplication.appContext!!.getString(R.string.albums)
        }
    },
    RANDOM {
        override fun getTitle(): String {
            return ImmichApplication.appContext!!.getString(R.string.random)
        }
    },
    RECENT {
        override fun getTitle(): String {
            return ImmichApplication.appContext!!.getString(R.string.recent)
        }
    },
    SIMILAR_TIME_PERIOD {
        override fun getTitle(): String {
            return ImmichApplication.appContext!!.getString(R.string.seasonal)
        }
    }
}
