package nl.giejay.android.tv.immich.shared.prefs

enum class ContentType:EnumWithTitle {
    ALL {
        override fun getTitle(): String {
            return "All"
        }
    }, IMAGE {
        override fun getTitle(): String {
            return "Image"
        }
    }, VIDEO {
        override fun getTitle(): String {
            return "Video"
        }
    }
}