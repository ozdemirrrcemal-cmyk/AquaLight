package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceFirmwareManifestUrlPolicyTest {

    @Test
    fun `accepts one canonical product channel manifest URL`() {
        val stableUrl = DeviceFirmwareRuntimeContract.OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX +
            "stable/light_wrgb_pro_elite.json"

        val location = requireOfficialFirmwareChannelManifestUrl("  $stableUrl  ")

        assertEquals(stableUrl, location.url)
        assertEquals(DeviceFirmwareChannel.STABLE, location.channel)
        assertEquals("light_wrgb_pro_elite", location.environment)
        assertEquals(stableUrl, requireOfficialFirmwareManifestUrl(stableUrl))
    }

    @Test
    fun `accepts isolated beta and dev product channels`() {
        val betaUrl = DeviceFirmwareRuntimeContract.OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX +
            "beta/dosing_dose_pro_4.json"
        val devUrl = DeviceFirmwareRuntimeContract.OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX +
            "dev/cooling_cool_pro_3f.json"

        assertEquals(
            DeviceFirmwareChannel.BETA,
            requireOfficialFirmwareChannelManifestUrl(betaUrl).channel
        )
        assertEquals(
            DeviceFirmwareChannel.DEV,
            requireOfficialFirmwareChannelManifestUrl(devUrl).channel
        )
    }

    @Test
    fun `rejects global latest and immutable release manifests`() {
        val latest =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/" +
                "releases/latest/download/manifest-stable.json"
        val immutableRelease = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "dosing_dose_pro_4-v1.0.2/manifest-dosing_dose_pro_4-v1.0.2.json"

        assertThrows(IllegalArgumentException::class.java) {
            requireOfficialFirmwareManifestUrl(latest)
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireOfficialFirmwareManifestUrl(immutableRelease)
        }
    }

    @Test
    fun `rejects lookalike transport query fragment and invalid product paths`() {
        val officialPrefix = DeviceFirmwareRuntimeContract.OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX
        val lookalike =
            "https://raw.githubusercontent.com/example/AquaLight-OTA-Releases/" +
                "main/channels/stable/light_wrgb_pro_elite.json"
        val insecure = officialPrefix.replace("https://", "http://") +
            "stable/light_wrgb_pro_elite.json"
        val query = officialPrefix + "stable/light_wrgb_pro_elite.json?candidate=1"
        val fragment = officialPrefix + "stable/light_wrgb_pro_elite.json#signed"
        val unsupportedChannel = officialPrefix + "nightly/light_wrgb_pro_elite.json"
        val traversal = officialPrefix + "stable/../dosing_dose_pro_4.json"
        val invalidEnvironment = officialPrefix + "stable/LIGHT_WRGB_PRO_ELITE.json"
        val binary = officialPrefix + "stable/light_wrgb_pro_elite.bin"

        listOf(
            lookalike,
            insecure,
            query,
            fragment,
            unsupportedChannel,
            traversal,
            invalidEnvironment,
            binary
        ).forEach { candidate ->
            assertThrows(candidate, IllegalArgumentException::class.java) {
                requireOfficialFirmwareManifestUrl(candidate)
            }
        }
    }
}
