package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.BuildConfig
import com.aqua.aqualight.application.devices.DEVICE_FIRMWARE_MANIFEST_URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceFirmwareManifestUrlPolicyTest {

    @Test
    fun `build application and runtime use the fixed stable channel manifest`() {
        val stable = DeviceFirmwareRuntimeContract.STABLE_MANIFEST_URL

        assertEquals(stable, BuildConfig.AQL_OTA_MANIFEST_URL)
        assertEquals(stable, DEVICE_FIRMWARE_MANIFEST_URL)
        assertEquals(stable, requireOfficialFirmwareManifestUrl(stable))
        assertEquals(stable, requireOfficialFirmwareManifestUrl("  $stable  "))
    }

    @Test
    fun `rejects latest versioned lookalike insecure and decorated URLs`() {
        val latest =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/" +
                "releases/latest/download/manifest-stable.json"
        val versioned = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "v6.0.0/manifest-stable.json"
        val lookalike =
            "https://github.com/example/AquaLight-OTA-Releases/releases/download/" +
                "ota-stable/manifest-stable.json"
        val insecure = DeviceFirmwareRuntimeContract.STABLE_MANIFEST_URL
            .replace("https://", "http://")
        val query = DeviceFirmwareRuntimeContract.STABLE_MANIFEST_URL + "?cache=false"
        val fragment = DeviceFirmwareRuntimeContract.STABLE_MANIFEST_URL + "#manifest"
        val otherAsset = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "ota-stable/manifest-beta.json"

        listOf(latest, versioned, lookalike, insecure, query, fragment, otherAsset).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) {
                requireOfficialFirmwareManifestUrl(url)
            }
        }
    }
}
