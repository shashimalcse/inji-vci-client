package com.example.vciclient.util

import android.util.Log
import com.example.vciclient.util.ConfigConstants.Companion.clientId
import com.example.vciclient.util.ConfigConstants.Companion.credentialConfigurationId
import com.example.vciclient.util.ConfigConstants.Companion.credentialIssuer
import com.example.vciclient.util.ConfigConstants.Companion.redirectUri
import com.google.gson.Gson
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.Ed25519Signer
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.OctetKeyPair
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.mosip.vciclient.VCIClient
import io.mosip.vciclient.authorizationCodeFlow.clientMetadata.ClientMetadata
import io.mosip.vciclient.token.TokenRequest
import io.mosip.vciclient.token.TokenResponse
import io.mosip.vciclient.credential.response.CredentialResponse
import com.example.vciclient.model.CredentialOfferData
import com.example.vciclient.model.CredentialTypeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.Base64
import java.util.Date
import kotlin.coroutines.resume


class VCIClientWrapper {

    private val client = VCIClient(traceabilityId = "demo-trace-id")
    private val signerJwk: OctetKeyPair by lazy {
        OctetKeyPairGenerator(Curve.Ed25519)
            .keyID("0")
            .generate()
    }

    // Fetch credential offer metadata without starting the flow
    suspend fun fetchCredentialOfferData(scanned: String): CredentialOfferData? {
        return try {
            // Parse the credential offer URL to get issuer
            val credentialIssuerUrl = extractIssuerFromOffer(scanned)
            
            // Fetch issuer metadata
            val issuerMetadata = client.getIssuerMetadata(credentialIssuerUrl)
            val credentialConfigsSupported = client.getCredentialConfigurationsSupported(credentialIssuerUrl)
            
            // Extract display name from metadata
            val displayList = issuerMetadata["display"] as? List<*>
            val issuerName = if (displayList != null && displayList.isNotEmpty()) {
                val firstDisplay = displayList[0] as? Map<*, *>
                (firstDisplay?.get("name") as? String) ?: extractDomainName(credentialIssuerUrl)
            } else {
                extractDomainName(credentialIssuerUrl)
            }
            
            // Parse credential types
            val credentialTypes = mutableMapOf<String, CredentialTypeInfo>()
            credentialConfigsSupported.forEach { (configId, config) ->
                if (config is Map<*, *>) {
                    val format = config["format"] as? String ?: "unknown"
                    val display = config["display"] as? List<*>
                    val displayName = if (display != null && display.isNotEmpty()) {
                        val firstDisplay = display[0] as? Map<*, *>
                        (firstDisplay?.get("name") as? String) ?: configId
                    } else {
                        configId
                    }
                    
                    // Extract claims from credential definition or metadata
                    val claims = mutableListOf<String>()
                    when (format) {
                        "ldp_vc", "jwt_vc_json" -> {
                            // First try credential_metadata.claims (newer format)
                            val credentialMetadata = config["credential_metadata"] as? Map<*, *>
                            val metadataClaims = credentialMetadata?.get("claims") as? List<*>
                            if (metadataClaims != null) {
                                metadataClaims.forEach { claim ->
                                    if (claim is Map<*, *>) {
                                        val path = claim["path"] as? List<*>
                                        path?.forEach { pathElement ->
                                            claims.add(pathElement.toString())
                                        }
                                    }
                                }
                            } else {
                                // Fall back to credential_definition.credentialSubject (older format)
                                val credentialDef = config["credential_definition"] as? Map<*, *>
                                val credentialSubject = credentialDef?.get("credentialSubject") as? Map<*, *>
                                credentialSubject?.keys?.forEach { key ->
                                    claims.add(key.toString())
                                }
                            }
                        }
                        "mso_mdoc" -> {
                            val claimsMap = config["claims"] as? Map<*, *>
                            claimsMap?.values?.forEach { namespaceValue ->
                                if (namespaceValue is Map<*, *>) {
                                    namespaceValue.keys.forEach { key ->
                                        claims.add(key.toString())
                                    }
                                }
                            }
                        }
                        "vc+sd-jwt", "dc+sd-jwt" -> {
                            val claimsMap = config["claims"] as? Map<*, *>
                            claimsMap?.keys?.forEach { key ->
                                claims.add(key.toString())
                            }
                        }
                    }
                    
                    credentialTypes[configId] = CredentialTypeInfo(
                        format = format,
                        displayName = displayName,
                        claims = claims
                    )
                }
            }
            
            CredentialOfferData(
                credentialIssuer = credentialIssuerUrl,
                credentialConfigurationIds = credentialTypes.keys.toList(),
                issuerName = issuerName,
                credentialTypes = credentialTypes,
                rawOffer = scanned
            )
        } catch (e: Exception) {
            Log.e("VCIClientWrapper", "Error fetching offer data", e)
            null
        }
    }
    
    private fun extractIssuerFromOffer(offer: String): String {
        // If it's a credential_offer_uri, fetch it first
        return if (offer.contains("credential_offer_uri=")) {
            val uri = offer.substringAfter("credential_offer_uri=")
            // Extract base URL from the URI
            val parts = uri.split("/")
            "${parts[0]}//${parts[2]}/${parts[3]}"
        } else {
            // Try to parse embedded offer
            credentialIssuer // Fallback to configured issuer
        }
    }
    
    private fun extractDomainName(url: String): String {
        return try {
            url.substringAfter("://").substringBefore("/").split(".").takeLast(2).joinToString(".").replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            "Credential Issuer"
        }
    }

    suspend fun startCredentialOfferFlow(scanned: String, onResult: (CredentialResponse?) -> Unit) {
        try {
            val response = client.requestCredentialByCredentialOffer(
                credentialOffer = scanned,
                clientMetadata = ClientMetadata(clientId, redirectUri),
                getTxCode = null,
                authorizeUser = { authUrl ->
                    suspendCancellableCoroutine { cont ->
                        NotificationCenter.post("ShowAuthWebView", authUrl)
                        NotificationCenter.once("AuthCodeReceived") { code ->
                            cont.resume(code)
                        }
                    }
                },
                getTokenResponse = { exchangeToken(it, proxy = false) },
                getProofJwt = { credentialIssuer, cNonce, _ ->
                    signProofJWT(
                        cNonce = cNonce,
                        issuer = credentialIssuer,
                        isTrusted = false,
                    )
                }
            )

            onResult(response)
        } catch (e: Exception) {
            Log.e("VCIClientWrapper", "Error in credential offer flow", e)
            onResult(null)
        }
    }

    suspend fun startTrustedIssuerFlow(onResult: (String) -> Unit) {
        try {
            val response = client.requestCredentialFromTrustedIssuer(
                credentialIssuer = credentialIssuer,
                credentialConfigurationId = credentialConfigurationId,
                clientMetadata = ClientMetadata(
                    clientId = clientId,
                    redirectUri = redirectUri
                ),
                authorizeUser = { authUrl ->
                    suspendCancellableCoroutine { cont ->

                        NotificationCenter.post("ShowAuthWebView", authUrl)
                        NotificationCenter.once("AuthCodeReceived") { code ->
                            cont.resume(code)
                        }
                    }
                },
                getTokenResponse = { exchangeToken(it, proxy = true) },
                getProofJwt = { credentialIssuer, cNonce, _ ->
                    signProofJWT(
                        cNonce = cNonce,
                        issuer = credentialIssuer,
                        isTrusted = true
                    )
                }
            )

            val result = response.toString() ?: "❌ No credential received"
            onResult("✅ Credential Issued:\n$result")
        } catch (e: Exception) {
            onResult("❌ Error: ${e.localizedMessage}")
        }
    }

     fun fetchCredentialTypes(
        credentialIssuer: String,
        onResult: (String, List<String>) -> Unit
    ) {
        try {
            val types = client.getCredentialConfigurationsSupported(credentialIssuer)
            val json = types.toString()
            val keys = types.keys.toList()
            onResult("✅ Credential Types:\n$json", keys)
        } catch (e: Exception) {
            onResult("❌ Error: ${e.localizedMessage}", emptyList())
        }
    }

    private fun signProofJWT(
        cNonce: String?,
        issuer: String,
        isTrusted: Boolean
    ): String {
        val kid = "did:jwk:" + signerJwk.toPublicJWK().toJSONString().base64Url() + "#0"

        val edHeader =  if(isTrusted)  JWSAlgorithm.Ed25519 else JWSAlgorithm.EdDSA
        val header = JWSHeader.Builder(edHeader)
            .keyID(kid)
            .type(JOSEObjectType("openid4vci-proof+jwt"))
            .build()

        val claimsSet = JWTClaimsSet.Builder()
            .audience(issuer)
            .claim("nonce", cNonce)
            .issueTime(Date())
            .expirationTime(Date(System.currentTimeMillis() + 5 * 60 * 60 * 1000))
            .build()

        val signedJWT = SignedJWT(header, claimsSet)
        signedJWT.sign(Ed25519Signer(signerJwk))
        Log.d("ProofJWT", "Signed JWT: ${signedJWT.serialize()}")
        return signedJWT.serialize()
    }

    private suspend fun exchangeToken(req: TokenRequest, proxy: Boolean): TokenResponse {
        val params = mutableListOf(
            "grant_type" to req.grantType.value
        )
        req.authCode?.let { params += "code" to it }
        req.codeVerifier?.let { params += "code_verifier" to it }
        req.preAuthCode?.let { params += "pre-authorized_code" to it }
        req.txCode?.let { params += "tx_code" to it }
        req.clientId?.let { params += "client_id" to it }
        req.redirectUri?.let { params += "redirect_uri" to it }

        val encoded = params.joinToString("&") {
            "${URLEncoder.encode(it.first, "UTF-8")}=${URLEncoder.encode(it.second, "UTF-8")}"
        }

        val url = req.tokenEndpoint

        val request = Request.Builder()
            .url(url)
            .post(encoded.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        val response = withContext(Dispatchers.IO) {
            OkHttpClient().newCall(request).execute()
        }

        val body = response.body?.string() ?: throw Exception("Empty response from token endpoint")
        if (!response.isSuccessful) throw Exception("Token request failed: ${response.code} $body")
        Log.d("token",body)
        val tokenResponse = Gson().fromJson(body, TokenResponse::class.java)
        Log.d("token", "Token Response: $tokenResponse")
        return tokenResponse
    }

    // Base64URL encode for did:jwk
    private fun String.base64Url(): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray())
    }
}
