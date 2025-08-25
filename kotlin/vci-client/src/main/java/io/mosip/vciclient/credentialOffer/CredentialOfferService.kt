package io.mosip.vciclient.credentialOffer

import io.mosip.vciclient.common.JsonUtils
import io.mosip.vciclient.exception.CredentialOfferFetchFailedException
import io.mosip.vciclient.networkManager.HttpMethod
import io.mosip.vciclient.networkManager.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLDecoder

internal class CredentialOfferService {

    suspend fun fetchCredentialOffer(credentialOfferData: String): CredentialOffer {
        try {
            val normalized = credentialOfferData.replace(
                "openid-credential-offer://?", "openid-credential-offer://dummy?"
            )
            val uri = URI(normalized)
            val queryParams = uri.rawQuery?.split("&")?.associate {
                    val (key, value) = it.split("=")
                    key to URLDecoder.decode(value, "UTF-8")
                } ?: throw CredentialOfferFetchFailedException("No query parameters in the URI")

            return when {
                queryParams.containsKey("credential_offer") -> {
                    val offer = queryParams["credential_offer"]
                        ?: throw CredentialOfferFetchFailedException("'credential_offer' in query parameters has empty value")
                    handleByValueOffer(offer)
                }

                queryParams.containsKey("credential_offer_uri") -> {
                    val uriOffer = queryParams["credential_offer_uri"]
                        ?: throw CredentialOfferFetchFailedException("'credential_offer_uri' in query parameters has empty value")
                    handleByReferenceOffer(uriOffer)
                }

                else -> throw CredentialOfferFetchFailedException(
                    "Invalid credential offer URL: must contain 'credential_offer' or 'credential_offer_uri'"
                )
            }
        } catch (e: Exception) {
            throw CredentialOfferFetchFailedException("Credential offer URL not valid $e.message")
        }
    }

    internal fun handleByValueOffer(encodedOffer: String): CredentialOffer {
        val decodedOffer = URLDecoder.decode(encodedOffer, "UTF-8")
        val credentialOffer = (JsonUtils.deserialize(decodedOffer, CredentialOffer::class.java)
            ?: throw CredentialOfferFetchFailedException("Invalid credential offer JSON"))
        CredentialOfferValidator.validate(credentialOffer)
        return credentialOffer
    }

    internal suspend fun handleByReferenceOffer(url: String): CredentialOffer {
        val responseBody = withContext(Dispatchers.IO) {
            val response = NetworkManager.sendRequest(
                url = url, method = HttpMethod.GET, headers = mapOf("Accept" to "application/json")
            )

            if (response.body.isBlank()) {
                throw CredentialOfferFetchFailedException("Empty response from $url")
            }

            response.body
        }

        val credentialOffer = withContext(Dispatchers.Default) {
            JsonUtils.deserialize(responseBody, CredentialOffer::class.java)
                ?: throw CredentialOfferFetchFailedException("Invalid credential offer JSON")
        }

        CredentialOfferValidator.validate(credentialOffer)
        return credentialOffer
    }

}