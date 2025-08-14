package io.mosip.vciclient.issuerMetadata

import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.verify
import io.mosip.vciclient.constants.CredentialFormat
import io.mosip.vciclient.exception.IssuerMetadataFetchException
import io.mosip.vciclient.networkManager.NetworkManager
import io.mosip.vciclient.networkManager.NetworkResponse
import io.mosip.vciclient.testData.wellKnownResponse
import io.mosip.vciclient.testData.wellKnownResponseMap
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertThrows

class IssuerMetadataServiceTest {

    private val issuerUrl = "https://mock.issuer"
    private val wellKnownUrl = "$issuerUrl/.well-known/openid-credential-issuer"
    private val credentialConfigId = "UniversityDegreeCredential"

    @Before
    fun setup() {
        mockkObject(NetworkManager)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `fetchIssuerMetadataResult first call makes network request`() = runBlocking {
        every { NetworkManager.sendRequest(wellKnownUrl, any(), any()) } returns NetworkResponse(
            wellKnownResponse, null)

        val service = IssuerMetadataService()
        service.fetchIssuerMetadataResult(issuerUrl, credentialConfigId)

        verify(exactly = 1) { NetworkManager.sendRequest(wellKnownUrl, any(), any()) }
    }

    @Test
    fun `fetchIssuerMetadataResult second call uses cache and does not make network request`() = runBlocking {
        every { NetworkManager.sendRequest(wellKnownUrl, any(), any()) } returns NetworkResponse(wellKnownResponse, null)

        val service = IssuerMetadataService()
        service.fetchIssuerMetadataResult(issuerUrl, credentialConfigId)
        clearMocks(NetworkManager)

        service.fetchIssuerMetadataResult(issuerUrl, credentialConfigId)

        verify(exactly = 0) { NetworkManager.sendRequest(any(), any(), any()) }
    }

    @Test
    fun `should parse ldp_vc metadata successfully`() = runBlocking {
        mockJsonResponse(LDP_VC_JSON)

        val result: IssuerMetadataResult = IssuerMetadataService().fetchIssuerMetadataResult(issuerUrl, "UniversityDegreeCredential")
        val resolved = result.issuerMetadata

        assertEquals(wellKnownResponseMap, result.raw)
        assertEquals(CredentialFormat.LDP_VC, resolved.credentialFormat)
        assertEquals("https://mock.issuer", resolved.credentialIssuer)
        assertEquals(listOf("VerifiableCredential"), resolved.credentialType)
        assertEquals("degree", resolved.scope)
        assertTrue(resolved.authorizationServers!!.contains("https://auth"))
        assertTrue(resolved.context!!.contains("https://www.w3.org/2018/credentials/v1"))
    }

    @Test
    fun `should set scope in IssuerMetadataResult to openid if no scope available in well-known response`() = runBlocking {
        mockJsonResponse(LDP_VC_JSON_WITHOUT_SCOPE)

        val result: IssuerMetadataResult = IssuerMetadataService().fetchIssuerMetadataResult(issuerUrl, "UniversityDegreeCredential")
        val resolved = result.issuerMetadata

        assertEquals("openid", resolved.scope)
    }

    @Test
    fun `should parse mso_mdoc metadata successfully`() = runBlocking {
        mockJsonResponse(MSO_MDOC_JSON)

        val result = IssuerMetadataService().fetchIssuerMetadataResult(issuerUrl, "DrivingLicense")
        val resolved = result.issuerMetadata

        assertEquals(CredentialFormat.MSO_MDOC, resolved.credentialFormat)
        assertEquals("org.iso.18013.5.1.mDL", resolved.doctype)
        assertTrue(resolved.claims!!.containsKey("name"))
        assertEquals("mock_mdoc",resolved.scope)
    }

    @Test
    fun `should throw if response is empty`() = runBlocking {
        mockJsonResponse("")

        val ex = assertThrows<IssuerMetadataFetchException> {
            IssuerMetadataService().fetchIssuerMetadataResult(issuerUrl, "Invalid")
        }
        assertTrue(ex.message.contains("response is empty"))
    }

    @Test
    fun `should throw if credential_configurations_supported is missing`() = runBlocking {
        mockJsonResponse("""{ "credential_issuer": "https://mock.issuer" }""")

        val ex = assertThrows<IssuerMetadataFetchException> {
            IssuerMetadataService().fetchIssuerMetadataResult(issuerUrl, "MissingConfig")
        }
        assertTrue(ex.message.contains("credential_configurations_supported"))
    }

    @Test
    fun `should throw if credential configuration id does not exist`() = runBlocking {
        mockJsonResponse(LDP_VC_JSON) // only contains "UniversityDegreeCredential"

        val ex = assertThrows<IssuerMetadataFetchException> {
            IssuerMetadataService().fetchIssuerMetadataResult(issuerUrl, "NonExistentCredential")
        }
        assertTrue(ex.message.contains("Credential configuration not found"))
    }

    @Test
    fun `should throw if format is unknown`() = runBlocking {
        mockJsonResponse(INVALID_FORMAT_JSON)

        val ex = assertThrows<IssuerMetadataFetchException> {
            IssuerMetadataService().fetchIssuerMetadataResult(issuerUrl, "TestCredential")
        }
        assertTrue(ex.message.contains("Unsupported or missing credential format"))
    }


    private fun mockJsonResponse(body: String) {
        every {
            NetworkManager.sendRequest(wellKnownUrl, any(), any())
        } returns NetworkResponse(body,null)
    }

    @Test
    fun `should parse vc_sd_jwt metadata successfully`() = runBlocking {
        mockJsonResponse(VC_SD_JWT_JSON)

        val result = IssuerMetadataService().fetchIssuerMetadataResult(issuerUrl, "SdJwtDegree")
        val resolved = result.issuerMetadata

        assertEquals(CredentialFormat.VC_SD_JWT, resolved.credentialFormat)
        assertEquals("urn:example:degree", resolved.vct)
        assertEquals("openid", resolved.scope)
        assertTrue(resolved.authorizationServers!!.contains("https://auth"))
        assertTrue(resolved.claims!!.containsKey("given_name"))
    }

    @Test
    fun `should parse dc_sd_jwt metadata successfully`() = runBlocking {
        mockJsonResponse(DC_SD_JWT_JSON)

        val result = IssuerMetadataService().fetchIssuerMetadataResult(issuerUrl, "SdJwtVcDc")
        val resolved = result.issuerMetadata

        assertEquals(CredentialFormat.DC_SD_JWT, resolved.credentialFormat)
        assertEquals("urn:example:driver-card", resolved.vct)
        assertEquals("dc_scope", resolved.scope)
    }

    @Test
    fun `should throw if vct missing for sd_jwt`() = runBlocking {
        mockJsonResponse(SD_JWT_MISSING_VCT_JSON)

        val ex = assertThrows<IssuerMetadataFetchException> {
            IssuerMetadataService().fetchIssuerMetadataResult(issuerUrl, "SdJwtNoVct")
        }
        assertTrue(ex.message.contains("Missing vct for SD-JWT"))
    }

    @Test
    fun `should throw if credential_endpoint missing`() = runBlocking {
        mockJsonResponse(MISSING_CREDENTIAL_ENDPOINT_JSON)

        val ex = assertThrows<IssuerMetadataFetchException> {
            IssuerMetadataService().fetchIssuerMetadataResult(issuerUrl, "UniversityDegreeCredential")
        }
        assertTrue(ex.message.contains("Missing credential_endpoint"))
    }

    @Test
    fun `should throw if credential_issuer missing`() = runBlocking {
        mockJsonResponse(MISSING_CREDENTIAL_ISSUER_JSON)

        val ex = assertThrows<IssuerMetadataFetchException> {
            IssuerMetadataService().fetchIssuerMetadataResult(issuerUrl, "UniversityDegreeCredential")
        }
        assertTrue(ex.message.contains("Missing credential_issuer"))
    }

    @Test
    fun `should throw if network call throws`() = runBlocking {
        every { NetworkManager.sendRequest(wellKnownUrl, any(), any()) } throws Exception("Invalid request")

        val ex = assertThrows<IssuerMetadataFetchException> {
            IssuerMetadataService().fetchIssuerMetadataResult(issuerUrl, credentialConfigId)
        }
        assertTrue(ex.message.startsWith("Failed to fetch issuerMetadata"))
        assertTrue(ex.message.contains("Invalid request"))
    }



    companion object {
        const val LDP_VC_JSON = """
        {
          "credential_issuer": "https://mock.issuer",
          "credential_endpoint": "https://mock.issuer/endpoint",
          "authorization_servers": ["https://auth"],
          "credential_configurations_supported": {
            "UniversityDegreeCredential": {
              "format": "ldp_vc",
              "scope": "degree",
              "credential_definition": {
                "@context": ["https://www.w3.org/2018/credentials/v1"],
                "type": ["VerifiableCredential"]
              }
            }
          }
        }
        """

        const val LDP_VC_JSON_WITHOUT_SCOPE = """
        {
          "credential_issuer": "https://mock.issuer",
          "credential_endpoint": "https://mock.issuer/endpoint",
          "authorization_servers": ["https://auth"],
          "credential_configurations_supported": {
            "UniversityDegreeCredential": {
              "format": "ldp_vc",
              "credential_definition": {
                "@context": ["https://www.w3.org/2018/credentials/v1"],
                "type": ["VerifiableCredential"]
              }
            }
          }
        }
        """

        const val MSO_MDOC_JSON = """
        {
          "credential_issuer": "https://mock.issuer",
          "credential_endpoint": "https://mock.issuer/endpoint",
          "authorization_servers": ["https://auth"],
          "credential_configurations_supported": {
            "DrivingLicense": {
              "scope": "mock_mdoc",
              "format": "mso_mdoc",
              "doctype": "org.iso.18013.5.1.mDL",
              "claims": {
                "name": { "mandatory": true }
              }
            }
          }
        }
        """

        const val INVALID_FORMAT_JSON = """
        {
          "credential_issuer": "https://mock.issuer",
          "credential_endpoint": "https://mock.issuer/endpoint",
          "authorization_servers": ["https://auth"],
          "credential_configurations_supported": {
            "TestCredential": {
              "format": "unsupported_format"
            }
          }
        }
        """

        const val VC_SD_JWT_JSON = """
{
  "credential_issuer": "https://mock.issuer",
  "credential_endpoint": "https://mock.issuer/endpoint",
  "authorization_servers": ["https://auth"],
  "credential_configurations_supported": {
    "SdJwtDegree": {
      "format": "vc+sd-jwt",
      "vct": "urn:example:degree",
      "claims": {
        "given_name": {"mandatory": true}
      }
    }
  }
}
"""

        const val DC_SD_JWT_JSON = """
{
  "credential_issuer": "https://mock.issuer",
  "credential_endpoint": "https://mock.issuer/endpoint",
  "authorization_servers": ["https://auth"],
  "credential_configurations_supported": {
    "SdJwtVcDc": {
      "format": "dc+sd-jwt",
      "scope": "dc_scope",
      "vct": "urn:example:driver-card",
      "claims": {
        "family_name": {"mandatory": true}
      }
    }
  }
}
"""

        const val SD_JWT_MISSING_VCT_JSON = """
{
  "credential_issuer": "https://mock.issuer",
  "credential_endpoint": "https://mock.issuer/endpoint",
  "credential_configurations_supported": {
    "SdJwtNoVct": {
      "format": "vc+sd-jwt",
      "claims": { "foo": {"mandatory": true} }
    }
  }
}
"""

        const val MISSING_CREDENTIAL_ENDPOINT_JSON = """
{
  "credential_issuer": "https://mock.issuer",
  "authorization_servers": ["https://auth"],
  "credential_configurations_supported": {
    "UniversityDegreeCredential": {
      "format": "ldp_vc",
      "credential_definition": { "@context": [], "type": [] }
    }
  }
}
"""

        const val MISSING_CREDENTIAL_ISSUER_JSON = """
{
  "credential_endpoint": "https://mock.issuer/endpoint",
  "authorization_servers": ["https://auth"],
  "credential_configurations_supported": {
    "UniversityDegreeCredential": {
      "format": "ldp_vc",
      "credential_definition": { "@context": [], "type": [] }
    }
  }
}
"""

    }
}
