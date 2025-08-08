package io.mosip.vciclient.issuerMetadata

import io.mosip.vciclient.constants.CredentialFormat

data class IssuerMetadata(
    val credentialIssuer: String,
    val credentialEndpoint: String,
    val credentialType: List<String>? = null,
    val context: List<String>? = null,
    val credentialFormat: CredentialFormat,
    val doctype: String? = null,
    val claims: Map<String, Any>? = null,
    val authorizationServers: List<String>? = null,
    val tokenEndpoint: String? = null,
    val vct: String? =null,
    val scope: String = "openId"
)
