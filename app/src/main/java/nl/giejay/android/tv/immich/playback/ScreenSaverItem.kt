package nl.giejay.android.tv.immich.playback;

data class ScreenSaverItem(
    val leftUrl: String?,
    public val leftCity: String?,
    public val leftState: String?,
    public val leftCountry: String?,
    public val orientation: Int?,

    public val rightUrl: String? = null,
    public val rightCity: String? = null,
    public val rightState: String? = null,
    public val rightCountry: String? = null,
)
