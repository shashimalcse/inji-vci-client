package io.mosip.vciclient.issuerMetadata

import io.mosip.vciclient.common.JsonUtils
import io.mosip.vciclient.constants.CredentialFormat
import io.mosip.vciclient.exception.IssuerMetadataFetchException
import io.mosip.vciclient.networkManager.HttpMethod
import io.mosip.vciclient.networkManager.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CREDENTIAL_ISSUER_WELL_KNOWN_URI_SUFFIX = "/.well-known/openid-credential-issuer"

@Suppress("UNCHECKED_CAST")
class IssuerMetadataService {
    private val timeoutMillis: Long = 10000
    private val cachedRawMetadata: MutableMap<String, Map<String, Any>> = mutableMapOf()

    /**
     * Fetches and resolves issuer metadata for a given issuer URI and credential configuration ID.
     * Stores the result in a cache (member property) to avoid redundant network calls.
     */
    suspend fun fetchIssuerMetadataResult(
        credentialIssuer: String,
        credentialConfigurationId: String
    ): IssuerMetadataResult = withContext(Dispatchers.IO) {
        val rawIssuerMetadata = cachedRawMetadata[credentialIssuer] ?: run {
            val fetched = fetchAndParseIssuerMetadata(credentialIssuer)
            cachedRawMetadata[credentialIssuer] = fetched
            fetched
        }

        val resolvedIssuerMetadata = resolveMetadata(
            credentialConfigurationId = credentialConfigurationId,
            rawIssuerMetadata = rawIssuerMetadata
        )

        return@withContext IssuerMetadataResult(
            issuerMetadata = resolvedIssuerMetadata,
            raw = rawIssuerMetadata,
            credentialIssuer = credentialIssuer
        )
    }


    fun fetchAndParseIssuerMetadata(credentialIssuer: String): Map<String, Any> {
        val wellKnownUrl = "$credentialIssuer$CREDENTIAL_ISSUER_WELL_KNOWN_URI_SUFFIX"

        try {
            val response = NetworkManager.sendRequest(
                url = wellKnownUrl,
                method = HttpMethod.GET,
                timeoutMillis = timeoutMillis
            )

            val body = response.body
            if (body.isBlank()) {
                throw IssuerMetadataFetchException("Issuer metadata response is empty.")
            }

            return JsonUtils.toMap(body)
        } catch (e: IssuerMetadataFetchException) {
            throw e
        } catch (e: Exception) {
            throw IssuerMetadataFetchException("Failed to fetch issuer metadata: ${e.message}")
        }
    }

    private fun resolveMetadata(
        credentialConfigurationId: String,
        rawIssuerMetadata: Map<String, Any>
    ): IssuerMetadata {
        val credentialConfigurationsSupported =
            rawIssuerMetadata["credential_configurations_supported"] as? Map<*, *>
                ?: throw IssuerMetadataFetchException("Missing credential_configurations_supported")
        val credentialType =
            credentialConfigurationsSupported[credentialConfigurationId] as? Map<*, *>
                ?: throw IssuerMetadataFetchException("Credential configuration not found: $credentialConfigurationId")
        val credentialEndpoint = rawIssuerMetadata["credential_endpoint"] as? String
            ?: throw IssuerMetadataFetchException("Missing credential_endpoint")
        val credentialIssuer = rawIssuerMetadata["credential_issuer"] as? String
            ?: throw IssuerMetadataFetchException("Missing credential_issuer")
        val format = credentialType["format"] as? String
        val scope = credentialType["scope"] as? String ?: "openid"

        return when (format) {
            CredentialFormat.MSO_MDOC.value -> {
                val doctype = credentialType["doctype"] as? String
                    ?: throw IssuerMetadataFetchException("Missing doctype")
                val claims = credentialType["claims"] as? Map<String, Any>

                IssuerMetadata(
                    credentialIssuer = credentialIssuer,
                    credentialEndpoint = credentialEndpoint,
                    credentialFormat = CredentialFormat.MSO_MDOC,
                    doctype = doctype,
                    claims = claims,
                    scope = scope,
                    authorizationServers = rawIssuerMetadata["authorization_servers"] as? List<String>
                )
            }

            CredentialFormat.LDP_VC.value -> {
                val credentialDefinition =
                    credentialType["credential_definition"] as? Map<*, *> ?: emptyMap<String, Any>()
                val types = credentialDefinition["type"] as? List<String>
                val context = credentialDefinition["@context"] as? List<String>

                IssuerMetadata(
                    credentialIssuer = credentialIssuer,
                    credentialEndpoint = credentialEndpoint,
                    credentialType = types,
                    context = context,
                    credentialFormat = CredentialFormat.LDP_VC,
                    authorizationServers = rawIssuerMetadata["authorization_servers"] as? List<String>,
                    scope = scope,
                )
            }

            CredentialFormat.VC_SD_JWT.value, CredentialFormat.DC_SD_JWT.value -> {
                val vct = credentialType["vct"] as? String
                    ?: throw IssuerMetadataFetchException("Missing vct for SD-JWT")

                val claims = credentialType["claims"] as? Map<String, Any>
                val resolvedFormat = CredentialFormat.values().firstOrNull { it.value == format }
                    ?: throw IssuerMetadataFetchException("Unrecognized credential format: $format")

                IssuerMetadata(
                    credentialIssuer = credentialIssuer,
                    credentialEndpoint = credentialEndpoint,
                    credentialFormat = resolvedFormat,
                    vct = vct,
                    claims = claims,
                    scope = scope,
                    authorizationServers = rawIssuerMetadata["authorization_servers"] as? List<String>
                )
            }

            else -> throw IssuerMetadataFetchException("Unsupported or missing credential format in configuration")
        }
    }
}