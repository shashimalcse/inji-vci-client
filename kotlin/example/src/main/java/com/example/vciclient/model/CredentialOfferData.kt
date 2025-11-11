package com.example.vciclient.model

data class CredentialOfferData(
    val credentialIssuer: String,
    val credentialConfigurationIds: List<String>,
    val issuerName: String,
    val issuerLogo: String? = null,
    val credentialTypes: Map<String, CredentialTypeInfo> = emptyMap(),
    val rawOffer: String
)

data class CredentialTypeInfo(
    val format: String,
    val displayName: String,
    val claims: List<String> = emptyList(),
    val description: String? = null
)
