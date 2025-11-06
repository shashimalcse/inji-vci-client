package io.mosip.vciclient.credential.request.types

import io.mosip.vciclient.common.JsonUtils
import io.mosip.vciclient.credential.request.CredentialRequest
import io.mosip.vciclient.credential.request.util.ValidatorResult
import io.mosip.vciclient.issuerMetadata.IssuerMetadata
import io.mosip.vciclient.proof.Proof
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class JwtVcJsonCredentialRequest(
    override val accessToken: String,
    override val issuerMetadata: IssuerMetadata, override val proof: Proof
) : CredentialRequest {

    override fun constructRequest(): Request {
        return Request.Builder()
            .url(issuerMetadata.credentialEndpoint)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(generateRequestBody())
            .build()
    }

    override fun validateIssuerMetaData(): ValidatorResult {
        val validatorResult = ValidatorResult()
        if (issuerMetadata.credentialId.isNullOrEmpty()) {
            validatorResult.addInvalidField("credentialId")
        }
        return validatorResult
    }

    private fun generateRequestBody(): RequestBody {
        val credentialConfigurationId = issuerMetadata.credentialId ?: ""
        val request = JwtVcJsonRequestBody(
            credentialConfigurationId = credentialConfigurationId,
        ).toJson()

        return request.toRequestBody("application/json".toMediaTypeOrNull())
    }
}

private data class JwtVcJsonRequestBody(
    val credentialConfigurationId: String,
) {
    fun toJson(): String = JsonUtils.serialize(this)
}
