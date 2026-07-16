package com.aqua.aqualight.application.devices.provisioning

/** Firmware-compatible Wi-Fi input validation expressed at the application boundary. */
object ProvisioningWifiInputPolicy {
    const val SSID_MAX_UTF8_BYTES = 32
    const val PASSWORD_MAX_UTF8_BYTES = 64

    fun validate(
        ssid: String,
        password: String
    ): ProvisioningWifiInputError? = when {
        ssid.isBlank() -> ProvisioningWifiInputError.EMPTY_SSID
        ssid.utf8ByteSize() > SSID_MAX_UTF8_BYTES ->
            ProvisioningWifiInputError.SSID_TOO_LONG
        password.utf8ByteSize() > PASSWORD_MAX_UTF8_BYTES ->
            ProvisioningWifiInputError.PASSWORD_TOO_LONG
        else -> null
    }

    private fun String.utf8ByteSize(): Int = toByteArray(Charsets.UTF_8).size
}

enum class ProvisioningWifiInputError {
    EMPTY_SSID,
    SSID_TOO_LONG,
    PASSWORD_TOO_LONG
}
