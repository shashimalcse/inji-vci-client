package io.mosip.vciclient.credential.request

import io.mosip.vciclient.constants.CredentialFormat
import io.mosip.vciclient.credential.request.types.LdpVcCredentialRequest
import io.mosip.vciclient.credential.request.types.MsoMdocCredentialRequest
import io.mosip.vciclient.credential.request.types.SdJwtCredentialRequest
import io.mosip.vciclient.exception.InvalidDataProvidedException
import io.mosip.vciclient.issuerMetadata.IssuerMetadata
import io.mosip.vciclient.proof.Proof
import okhttp3.Request

class CredentialRequestFactory {
    companion object {
        fun createCredentialRequest(
            credentialFormat: CredentialFormat,
            accessToken: String,
            issuerMetadata: IssuerMetadata,
            proof: Proof,
        ): Request {
            when (credentialFormat) {
                CredentialFormat.LDP_VC -> {
                    return validateAndConstructRequest(
                        LdpVcCredentialRequest(
                            accessToken,
                            issuerMetadata,
                            proof
                        )
                    )
                }

                CredentialFormat.MSO_MDOC -> {
                    return validateAndConstructRequest(
                        MsoMdocCredentialRequest(
                            accessToken,
                            issuerMetadata,
                            proof
                        )
                    )
                }

                CredentialFormat.VC_SD_JWT, CredentialFormat.DC_SD_JWT ->
                    return validateAndConstructRequest(
                        SdJwtCredentialRequest(
                            accessToken,
                            issuerMetadata,
                            proof
                        )
                    )

                else -> throw InvalidDataProvidedException("Unsupported or missing credential format in configuration")
            }
        }

        private fun validateAndConstructRequest(credentialRequest: CredentialRequest): Request {
            val issuerMetaDataValidatorResult = credentialRequest.validateIssuerMetaData()
            if (issuerMetaDataValidatorResult.isValid)
                return credentialRequest.constructRequest()
            throw InvalidDataProvidedException(issuerMetaDataValidatorResult.invalidFields.toString())
        }
    }
}