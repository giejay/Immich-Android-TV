package nl.giejay.android.tv.immich.api.model

/** A user who shares their library with the signed-in user (GET /partners?direction=shared-with). */
data class Partner(
    val id: String,
    val name: String,
    val email: String
)
