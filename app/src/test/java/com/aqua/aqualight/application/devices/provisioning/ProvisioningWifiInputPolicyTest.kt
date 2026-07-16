package com.aqua.aqualight.application.devices.provisioning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProvisioningWifiInputPolicyTest {

    @Test
    fun `empty SSID is rejected`() {
        assertEquals(
            ProvisioningWifiInputError.EMPTY_SSID,
            ProvisioningWifiInputPolicy.validate("   ", "password")
        )
    }

    @Test
    fun `SSID limit is measured in UTF8 bytes`() {
        assertNull(
            ProvisioningWifiInputPolicy.validate(
                ssid = "a".repeat(32),
                password = ""
            )
        )
        assertEquals(
            ProvisioningWifiInputError.SSID_TOO_LONG,
            ProvisioningWifiInputPolicy.validate(
                ssid = "ş".repeat(17),
                password = ""
            )
        )
    }

    @Test
    fun `password limit is measured in UTF8 bytes`() {
        assertNull(
            ProvisioningWifiInputPolicy.validate(
                ssid = "Home WiFi",
                password = "a".repeat(64)
            )
        )
        assertEquals(
            ProvisioningWifiInputError.PASSWORD_TOO_LONG,
            ProvisioningWifiInputPolicy.validate(
                ssid = "Home WiFi",
                password = "ş".repeat(33)
            )
        )
    }
}
