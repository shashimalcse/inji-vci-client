package io.mosip.vciclient.pkce

import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID


class PKCESessionManager {

    data class PKCESession(
        val codeVerifier: String,
        val codeChallenge: String,
        val state: String,
        val nonce: String,
    )

    private val CODE_VERIFIER_BYTE_SIZE = 64
    private val PKCE_CHALLENGE_ALGORITHM = "SHA-256"

    fun createSession(): PKCESession {
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val nonce = generateNonce()
        val state = generateState()
        return PKCESession(codeVerifier, codeChallenge, state, nonce)
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(CODE_VERIFIER_BYTE_SIZE)
        SecureRandom().nextBytes(bytes)
        return bytes.toByteString().base64Url().trimEnd('=')
    }

    private fun generateCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance(PKCE_CHALLENGE_ALGORITHM)
            .digest(codeVerifier.encodeUtf8().toByteArray())
            .toByteString()
        return digest.base64Url().trimEnd('=')
    }

    private fun generateState(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }

    private fun generateNonce(): String =
        UUID.randomUUID().toString().replace("-", "")
}
