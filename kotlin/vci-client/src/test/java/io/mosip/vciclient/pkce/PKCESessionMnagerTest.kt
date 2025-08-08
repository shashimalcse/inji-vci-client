package io.mosip.vciclient.pkce

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.*
import java.util.regex.Pattern

class PKCESessionManagerTest {

    private val unreservedCharsPattern = Pattern.compile("^[A-Za-z0-9\\-._~]+$")

    @Test
    fun `createSession should return non-empty verifier, challenge, state, and nonce`() {
        val session = PKCESessionManager().createSession()

        assertTrue("Verifier should not be blank", session.codeVerifier.isNotBlank())
        assertTrue("Challenge should not be blank", session.codeChallenge.isNotBlank())
        assertTrue("State should not be blank", session.state.isNotBlank())
        assertTrue("Nonce should not be blank", session.nonce.isNotBlank())
    }

    @Test
    fun `codeChallenge should be base64url(SHA256(codeVerifier))`() {
        val session = PKCESessionManager().createSession()

        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(session.codeVerifier.toByteArray(Charsets.UTF_8))

        val expectedChallenge = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(sha256)

        assertEquals("Challenge must match SHA256 hash of verifier", expectedChallenge, session.codeChallenge)
    }

    @Test
    fun `codeVerifier should be between 43 and 128 characters`() {
        val session = PKCESessionManager().createSession()
        val len = session.codeVerifier.length
        assertTrue("codeVerifier must be at least 43 characters", len >= 43)
        assertTrue("codeVerifier must be at most 128 characters", len <= 128)
    }

    @Test
    fun `codeVerifier should contain only unreserved characters`() {
        val session = PKCESessionManager().createSession()
        assertTrue(
            "codeVerifier must contain only unreserved characters",
            unreservedCharsPattern.matcher(session.codeVerifier).matches()
        )
    }

    @Test
    fun `should pass if codeVerifier is unique across multiple sessions`() {
        val session1 = PKCESessionManager().createSession()
        val session2 = PKCESessionManager().createSession()

        assertNotEquals("codeVerifier should be unique", session1.codeVerifier, session2.codeVerifier)
        assertNotEquals("codeChallenge should be unique", session1.codeChallenge, session2.codeChallenge)
        assertNotEquals("state should be unique", session1.state, session2.state)
        assertNotEquals("nonce should be unique", session1.nonce, session2.nonce)
    }

    @Test
    fun `codeChallenge should produce known result for fixed verifier`() {
        val fixedVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"

        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(fixedVerifier.toByteArray(Charsets.US_ASCII))

        val actualChallenge = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(sha256)

        assertEquals("codeChallenge must match expected result for fixed verifier", expectedChallenge, actualChallenge)
    }
}
