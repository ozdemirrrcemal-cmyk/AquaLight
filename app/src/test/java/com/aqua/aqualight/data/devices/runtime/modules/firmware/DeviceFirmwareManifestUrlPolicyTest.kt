package com.aqua.aqualight.data.devices.runtime.modules.firmware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceFirmwareManifestUrlPolicyTest {

    @Test
    fun `accepts only exact product channel and immutable manifest assets`() {
        val channel = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "stable-dosing_dose_pro_2/manifest-stable.json"
        val immutable = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "dosing_dose_pro_4-v2.0.0/manifest-dosing_dose_pro_4-v2.0.0.json"

        assertEquals(channel, requireOfficialFirmwareManifestUrl(channel))
        assertEquals(immutable, requireOfficialFirmwareManifestUrl(immutable))
    }

    @Test
    fun `rejects global family unknown and non manifest release paths`() {
        val lookalike =
            "https://github.com/example/AquaLight-OTA-Releases/releases/latest/download/" +
                "manifest-stable.json"
        val globalLatest = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX
            .replace("releases/download/", "releases/latest/download/") +
            "manifest-stable.json"
        val globalVersion = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "v2.0.0/manifest-v2.0.0.json"
        val family = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "stable-dosing/manifest-stable.json"
        val unknown = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "stable-dosing_dose_pro_8/manifest-stable.json"
        val insecure = (DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "stable-dosing_dose_pro_2/manifest-stable.json").replace("https://", "http://")
        val binary = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "dosing_dose_pro_2-v2.0.0/AquaLight-dosing_dose_pro_2-v2.0.0-ota.bin"
        val whitespace = " " + DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "stable-dosing_dose_pro_2/manifest-stable.json"

        for (
            url in listOf(
                lookalike,
                globalLatest,
                globalVersion,
                family,
                unknown,
                insecure,
                binary,
                whitespace
            )
        ) {
            assertThrows(IllegalArgumentException::class.java) {
                requireOfficialFirmwareManifestUrl(url)
            }
        }
    }
}
