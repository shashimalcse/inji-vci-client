package io.mosip.vciclient

import io.mosip.vciclient.authorizationCodeFlow.clientMetadata.ClientMetadata
import io.mosip.vciclient.common.JsonUtils
import io.mosip.vciclient.common.Util
import io.mosip.vciclient.constants.Constants
import io.mosip.vciclient.credential.request.CredentialRequestFactory
import io.mosip.vciclient.credential.response.CredentialResponse
import io.mosip.vciclient.credentialOffer.CredentialOfferFlowHandler
import io.mosip.vciclient.dto.IssuerMetaData
import io.mosip.vciclient.exception.DownloadFailedException
import io.mosip.vciclient.exception.InvalidAccessTokenException
import io.mosip.vciclient.exception.InvalidPublicKeyException
import io.mosip.vciclient.exception.NetworkRequestFailedException
import io.mosip.vciclient.exception.NetworkRequestTimeoutException
import io.mosip.vciclient.exception.VCIClientException
import io.mosip.vciclient.issuerMetadata.IssuerMetadata
import io.mosip.vciclient.issuerMetadata.IssuerMetadataService
import io.mosip.vciclient.proof.Proof
import io.mosip.vciclient.trustedIssuer.TrustedIssuerFlowHandler
import io.mosip.vciclient.constants.AuthorizeUserCallback
import io.mosip.vciclient.constants.CheckIssuerTrustCallback
import io.mosip.vciclient.constants.ProofJwtCallback
import io.mosip.vciclient.constants.TokenResponseCallback
import io.mosip.vciclient.constants.TxCodeCallback
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class VCIClient(traceabilityId: String) {

    private val logTag = Util.getLogTag(javaClass.simpleName, traceabilityId)
    private val logger = Logger.getLogger(logTag)

    fun getIssuerMetadata(credentialIssuer: String): Map<String, Any> {
        try {
            return IssuerMetadataService().fetchAndParseIssuerMetadata(credentialIssuer)
        } catch (exception: VCIClientException) {
            logger.severe(
                "Fetching issuer metadata failed due to ${exception.message}"
            )
            throw exception
        } catch (e: Exception) {
            logger.severe(
                "Fetching issuer metadata failed due to ${e.message}"
            )
            throw VCIClientException("VCI-010", "Unknown Exception - ${e.message}")
        }
    }

    fun getCredentialConfigurationsSupported(credentialIssuer: String): Map<String, Any> {
        try {
            return IssuerMetadataService().fetchCredentialConfigurationsSupported(credentialIssuer)
        } catch (exception: VCIClientException) {
            logger.severe(
                "Fetching credentialConfigurationsSupported from issuer metadata failed due to ${exception.message}"
            )
            throw exception
        } catch (e: Exception) {
            logger.severe(
                "Fetching credentialConfigurationsSupported from issuer metadata failed"
            )
            throw VCIClientException("VCI-010", "Unknown Exception - ${e.message}")
        }
    }



    suspend fun requestCredentialByCredentialOffer(
        credentialOffer: String,
        clientMetadata: ClientMetadata,
        getTxCode: TxCodeCallback?,
        authorizeUser: AuthorizeUserCallback,
        getTokenResponse: TokenResponseCallback,
        getProofJwt: ProofJwtCallback,
        onCheckIssuerTrust: CheckIssuerTrustCallback? = null,
        downloadTimeoutInMillis: Long = Constants.DEFAULT_NETWORK_TIMEOUT_IN_MILLIS
    ): CredentialResponse {
        try {
            return CredentialOfferFlowHandler().downloadCredentials(
                credentialOffer,
                clientMetadata,
                getTxCode,
                authorizeUser,
                getTokenResponse,
                getProofJwt,
                onCheckIssuerTrust,
                downloadTimeoutInMillis
            )
        } catch (e: VCIClientException) {
            logger.severe("Downloading credential failed due to ${e.message}")
            throw e
        } catch (e: Exception) {
            logger.severe("Downloading credential failed due to ${e.message}")
            throw VCIClientException("VCI-010", "Unknown Exception - ${e.message}")
        }
    }


    suspend fun requestCredentialFromTrustedIssuer(
        credentialIssuer: String,
        credentialConfigurationId: String,
        clientMetadata: ClientMetadata,
        authorizeUser: AuthorizeUserCallback,
        getTokenResponse: TokenResponseCallback,
        getProofJwt: ProofJwtCallback,
        downloadTimeoutInMillis: Long = Constants.DEFAULT_NETWORK_TIMEOUT_IN_MILLIS
    ): CredentialResponse {
        try {
            return TrustedIssuerFlowHandler().downloadCredentials(
                credentialIssuer,
                credentialConfigurationId,
                clientMetadata,
                getTokenResponse,
                authorizeUser,
                getProofJwt,
                downloadTimeoutInMillis,
            )
        } catch (e: VCIClientException) {
            logger.severe("Downloading credential failed due to ${e.message}")
            throw e
        } catch (e: Exception) {
            logger.severe("Downloading credential failed due to ${e.message}")
            throw VCIClientException("VCI-010", "Unknown Exception - ${e.message}")
        }
    }

    @Deprecated(
        message = "This method is deprecated as per the new VCI Client library contract. " + "Use requestCredentialByCredentialOffer() or requestCredentialFromTrustedIssuer()",
        level = DeprecationLevel.WARNING
    )
    fun requestCredential(
        issuerMetadata: IssuerMetaData,
        proof: Proof,
        accessToken: String,
    ): CredentialResponse? {

        try {
            val client = OkHttpClient.Builder().callTimeout(
                issuerMetadata.downloadTimeoutInMilliSeconds.toLong(), TimeUnit.MILLISECONDS
            ).build()

            val metadata = IssuerMetadata(
                credentialIssuer = issuerMetadata.credentialAudience,
                credentialEndpoint = issuerMetadata.credentialEndpoint,
                credentialType = issuerMetadata.credentialType?.toList(),
                credentialFormat = issuerMetadata.credentialFormat,
                doctype = issuerMetadata.doctype,
                claims = issuerMetadata.claims,
                context = null,
                authorizationServers = null,
                tokenEndpoint = null,
                scope = "openId"
            )

            val request = CredentialRequestFactory.createCredentialRequest(
                metadata.credentialFormat, accessToken, metadata, proof,
            )
            val response: Response = client.newCall(request).execute()

            if (response.code != 200) {
                val errorResponse: String? = response.body?.string()
                logger.severe(

                    "Downloading credential failed with response code ${response.code} - ${response.message}. Error - $errorResponse"
                )
                if (errorResponse != "" && errorResponse != null) {
                    throw DownloadFailedException(errorResponse)
                }
                throw DownloadFailedException(response.message)
            }
            val responseBody: String =
                response.body?.byteStream()?.bufferedReader().use { it?.readText() } ?: ""
            logger.info("credential downloaded successfully!")

            if (responseBody != "") {
                return JsonUtils.deserialize(responseBody, CredentialResponse::class.java)

            }

            logger.warning(
                "The response body from credentialEndpoint is empty, responseCode - ${response.code}, responseMessage ${response.message}, returning null."
            )
            return null
        } catch (exception: InterruptedIOException) {
            logger.severe(
                "Network request for ${issuerMetadata.credentialEndpoint} took more than expected time(${issuerMetadata.downloadTimeoutInMilliSeconds / 1000}s). Exception - $exception"
            )
            throw NetworkRequestTimeoutException("")
        } catch (exception: IOException) {
            logger.severe(
                "Network request failed due to Exception - $exception"
            )
            throw NetworkRequestFailedException(exception.message)
        } catch (exception: Exception) {
            if (exception is DownloadFailedException || exception is InvalidAccessTokenException || exception is InvalidPublicKeyException) throw exception
            logger.severe(
                "Downloading credential failed due to ${exception.message}"
            )
            throw DownloadFailedException(exception.message!!)
        }
    }

}
