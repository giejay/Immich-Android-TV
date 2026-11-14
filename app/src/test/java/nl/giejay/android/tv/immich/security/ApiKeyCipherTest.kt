package nl.giejay.android.tv.immich.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers the pure migration/prefix logic only - the AES-GCM round trip needs a real
// AndroidKeyStore, unavailable under plain JVM unit tests.
class ApiKeyCipherTest {

    @Test
    fun `empty value decrypts to empty without touching the keystore`() {
        assertEquals("", ApiKeyCipher.decryptOrAdoptLegacy(""))
    }

    @Test
    fun `unprefixed value is treated as a legacy plaintext key`() {
        assertEquals("legacy-plaintext-key", ApiKeyCipher.decryptOrAdoptLegacy("legacy-plaintext-key"))
        assertFalse(ApiKeyCipher.isEncrypted("legacy-plaintext-key"))
    }

    @Test
    fun `plaintext fallback prefix is stripped without touching the keystore`() {
        assertEquals("fallback-key", ApiKeyCipher.decryptOrAdoptLegacy("plain:fallback-key"))
        assertFalse(ApiKeyCipher.isEncrypted("plain:fallback-key"))
    }

    @Test
    fun `versioned value is recognized as already encrypted`() {
        assertTrue(ApiKeyCipher.isEncrypted("v1:whatever-ciphertext"))
    }
}
