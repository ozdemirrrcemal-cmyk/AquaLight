package com.aqua.aqualight.data.devices.runtime.modules.firmware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceFirmwareManifestUrlPolicyTest {

    @Test
    fun `accepts only versioned or latest assets from the official release repository`() {
        val versioned = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "v2.0.0/manifest-stable.json"
        val latest = DeviceFirmwareRuntimeContract.OFFICIAL_LATEST_RELEASE_URL_PREFIX +
            "manifest-stable.json"

        assertEquals(versioned, requireOfficialFirmwareManifestUrl(versioned))
        assertEquals(latest, requireOfficialFirmwareManifestUrl("  $latest  "))
    }

    @Test
    fun `rejects lookalike repositories insecure transport and non-json assets`() {
        val lookalike =
            "https://github.com/example/AquaLight-OTA-Releases/releases/latest/download/" +
                "manifest-stable.json"
        val insecure = DeviceFirmwareRuntimeContract.OFFICIAL_LATEST_RELEASE_URL_PREFIX
            .replace("https://", "http://") + "manifest-stable.json"
        val binary = DeviceFirmwareRuntimeContract.OFFICIAL_LATEST_RELEASE_URL_PREFIX +
            "firmware.bin"

        assertThrows(IllegalArgumentException::class.java) {
            requireOfficialFirmwareManifestUrl(lookalike)
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireOfficialFirmwareManifestUrl(insecure)
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireOfficialFirmwareManifestUrl(binary)
        }
    }
}
