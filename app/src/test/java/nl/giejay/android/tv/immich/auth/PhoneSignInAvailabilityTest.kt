package nl.giejay.android.tv.immich.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneSignInAvailabilityTest {
    @Test
    fun `the placeholder from the example file is not available`() {
        assertThat(isPhoneSignInAvailable("https://example.com/authentication_using_phone_url")).isFalse()
        assertThat(isPhoneSignInAvailable("https://auth.example.com/")).isFalse()
    }

    @Test
    fun `empty, malformed and local addresses are not available`() {
        assertThat(isPhoneSignInAvailable("")).isFalse()
        assertThat(isPhoneSignInAvailable("not a url")).isFalse()
        assertThat(isPhoneSignInAvailable("http://localhost:3000/")).isFalse()
    }

    @Test
    fun `a real relay address is available`() {
        assertThat(isPhoneSignInAvailable("https://auth.my-relay.net/")).isTrue()
        assertThat(isPhoneSignInAvailable(" https://relay.myhost.org/api/ ")).isTrue()
    }
}
