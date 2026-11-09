package nl.giejay.android.tv.immich.auth

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Phone sign-in needs a relay service whose address is set at build time (`authentication_url` in
 * `strings_other.xml`). Builds without a real address (the placeholder from `strings_other.xml.example`)
 * must not offer an option that can never work.
 */
internal fun isPhoneSignInAvailable(relayUrl: String): Boolean {
    val host = relayUrl.trim().toHttpUrlOrNull()?.host ?: return false
    return host != "localhost" && host != "example.com" && !host.endsWith(".example.com")
}
