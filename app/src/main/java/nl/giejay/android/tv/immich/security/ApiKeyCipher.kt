package nl.giejay.android.tv.immich.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "immich_api_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
private const val GCM_IV_LENGTH_BYTES = 12
private const val VERSION_PREFIX = "v1:"
// accepted tradeoff: on the rare broken-keystore path this plaintext fallback is still subject
// to whatever backup config the app has (currently none excludes it)
private const val PLAINTEXT_FALLBACK_PREFIX = "plain:"

/**
 * Encrypts the Immich API key with a key that never leaves AndroidKeyStore.
 * `security-crypto`/EncryptedSharedPreferences is deprecated since 1.1.0-beta01 in favor of this.
 */
object ApiKeyCipher {

    // Never throws: falls back to a tagged plaintext form on a broken keystore instead of
    // crashing app startup. Self-heals - retried on every subsequent save.
    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            VERSION_PREFIX + Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.e(e, "Could not encrypt API key, falling back to unencrypted storage for now")
            PLAINTEXT_FALLBACK_PREFIX + plaintext
        }
    }

    // Never throws: an unrecognized/corrupt value degrades to "" (logged out). A value from
    // before encryption existed has no prefix at all, so it's returned as-is for the caller to
    // re-save (migrate).
    fun decryptOrAdoptLegacy(stored: String): String {
        return when {
            stored.isEmpty() -> ""
            stored.startsWith(PLAINTEXT_FALLBACK_PREFIX) -> stored.removePrefix(PLAINTEXT_FALLBACK_PREFIX)
            stored.startsWith(VERSION_PREFIX) -> decrypt(stored.removePrefix(VERSION_PREFIX))
            else -> stored
        }
    }

    /** True if [stored] already went through [encrypt] (vs. legacy/fallback plaintext). */
    fun isEncrypted(stored: String): Boolean = stored.startsWith(VERSION_PREFIX)

    private fun decrypt(base64: String): String {
        // no key at all (keystore wiped/restored elsewhere) is an expected, silent logout -
        // not an error, and mustn't generate a fresh unusable key as a side effect of reading
        val key = getExistingKey() ?: return ""
        val combined = try {
            Base64.decode(base64, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            null
        }
        // size check guards copyOfRange below against corrupted/truncated input
        return combined?.takeIf { it.size > GCM_IV_LENGTH_BYTES }?.let { bytes ->
            try {
                val iv = bytes.copyOfRange(0, GCM_IV_LENGTH_BYTES)
                val encrypted = bytes.copyOfRange(GCM_IV_LENGTH_BYTES, bytes.size)
                val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                }
                String(cipher.doFinal(encrypted), Charsets.UTF_8)
            } catch (e: Exception) {
                Timber.e(e, "Could not decrypt stored API key; treating as logged out")
                null
            }
        } ?: ""
    }

    @Synchronized
    private fun getExistingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey = getExistingKey() ?: run {
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }
}
