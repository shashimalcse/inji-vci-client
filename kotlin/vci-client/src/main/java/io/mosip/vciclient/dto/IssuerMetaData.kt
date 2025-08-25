package io.mosip.vciclient.dto

import io.mosip.vciclient.constants.CredentialFormat

@Deprecated(
    message = "IssuerMetaData is deprecated. Use IssuerMetadata instead as per the new VCIClient library contract.",
    replaceWith = ReplaceWith("IssuerMetadata"),
    level = DeprecationLevel.WARNING
)
@Suppress("ArrayInDataClass")
data class IssuerMetaData(
    val credentialAudience: String,
    val credentialEndpoint: String,
    val downloadTimeoutInMilliSeconds: Int,
    val credentialType: Array<String>? = null,
    val credentialFormat: CredentialFormat,
    val doctype: String? = null,
    val claims: Map<String, Any>? = null,
)