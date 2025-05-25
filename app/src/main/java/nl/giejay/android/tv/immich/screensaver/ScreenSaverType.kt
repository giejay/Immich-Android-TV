package nl.giejay.android.tv.immich.screensaver

import nl.giejay.android.tv.immich.shared.prefs.EnumWithTitle

enum class ScreenSaverType : EnumWithTitle {
    ALBUMS {
        override fun getTitle(): String {
            return "Albums"
        }
    },
    RANDOM {
        override fun getTitle(): String {
            return "Random"
        }
    },
    RECENT {
        override fun getTitle(): String {
            return "Recent"
        }
    },
    SIMILAR_TIME_PERIOD {
        override fun getTitle(): String {
            return "Seasonal"
        }
    }
}