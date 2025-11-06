package io.mosip.vciclient.constants

enum class CredentialFormat(val value: String) {
    LDP_VC("ldp_vc"),
    MSO_MDOC("mso_mdoc"),
    VC_SD_JWT("vc+sd-jwt"),
    DC_SD_JWT("dc+sd-jwt"),

    JWT_VC_JSON("jwt_vc_json")


}