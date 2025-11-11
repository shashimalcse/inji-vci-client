package com.example.vciclient.model

import com.google.gson.JsonElement

data class StoredCredential(
    val id: String, // Unique ID for the credential
    val configurationId: String, // Credential configuration ID
    val issuer: String, // Credential issuer
    val credentialData: JsonElement, // The actual credential (JWT or JSON)
    val rawResponse: String, // Full response for debugging
    val timestamp: Long = System.currentTimeMillis() // When it was received
)
