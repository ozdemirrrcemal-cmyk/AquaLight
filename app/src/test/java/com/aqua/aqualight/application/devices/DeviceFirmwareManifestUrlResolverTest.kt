package com.aqua.aqualight.application.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareManifestUrlResolverTest {

    @Test
    fun `resolves a distinct stable channel for all seven commercial products`() {
        val productKeys = listOf(
            "LIGHT_WRGB_PRO_ELITE",
            "LIGHT_RGB_PRO_SLIM",
            "TIMER_RELAY_PRO_2",
            "TIMER_RELAY_PRO_4",
            "DOSING_DOSE_PRO_2",
            "DOSING_DOSE_PRO_4",
            "COOLING_COOL_PRO_1F"
        )

        val resolved = productKeys.associateWith { productKey ->
            DeviceFirmwareManifestUrlResolver.resolve(
                template = DEVICE_FIRMWARE_MANIFEST_URL,
                productKey = productKey
            )
        }

        assertEquals(7, resolved.values.toSet().size)
        assertEquals(
            DEVICE_FIRMWARE_PRODUCT_ENVIRONMENTS,
            productKeys.map { it.lowercase() }.toSet()
        )
        resolved.forEach { (productKey, url) ->
            assertTrue(
                url.contains(
                    "/channels/stable/${productKey.lowercase()}/manifest-stable.json"
                )
            )
        }
    }

    @Test
    fun `models in the same product family resolve independently`() {
        val dosePro2 = DeviceFirmwareManifestUrlResolver.resolve(
            DEVICE_FIRMWARE_MANIFEST_URL,
            "DOSING_DOSE_PRO_2"
        )
        val dosePro4 = DeviceFirmwareManifestUrlResolver.resolve(
            DEVICE_FIRMWARE_MANIFEST_URL,
            "DOSING_DOSE_PRO_4"
        )

        assertTrue(dosePro2.contains("/channels/stable/dosing_dose_pro_2/"))
        assertTrue(dosePro4.contains("/channels/stable/dosing_dose_pro_4/"))
        assertTrue(dosePro2 != dosePro4)
    }

    @Test
    fun `rejects missing product identity before network access`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceFirmwareManifestUrlResolver.resolve(
                DEVICE_FIRMWARE_MANIFEST_URL,
                ""
            )
        }
    }

    @Test
    fun `rejects unknown or non canonical product keys`() {
        for (
            productKey in listOf(
                "DOSING",
                "DOSING_DOSE_PRO",
                "dosing_dose_pro_2",
                " DOSING_DOSE_PRO_2",
                "COOLING_COOL_PRO_2F",
                "COOLING_COOL_PRO_3F"
            )
        ) {
            assertThrows(IllegalArgumentException::class.java) {
                DeviceFirmwareManifestUrlResolver.resolve(
                    DEVICE_FIRMWARE_MANIFEST_URL,
                    productKey
                )
            }
        }
    }

    @Test
    fun `accepts an explicit product manifest only for its exact product`() {
        val explicit =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/" +
                "releases/download/dosing_dose_pro_2-v1.0.2/" +
                "manifest-dosing_dose_pro_2-v1.0.2.json"

        assertEquals(
            explicit,
            DeviceFirmwareManifestUrlResolver.resolve(explicit, "DOSING_DOSE_PRO_2")
        )
        assertThrows(IllegalArgumentException::class.java) {
            DeviceFirmwareManifestUrlResolver.resolve(explicit, "DOSING_DOSE_PRO_4")
        }
    }

    @Test
    fun `rejects global and ambiguous manifest templates`() {
        val global =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/" +
                "releases/latest/download/manifest-stable.json"

        assertThrows(IllegalArgumentException::class.java) {
            DeviceFirmwareManifestUrlResolver.resolve(global, "DOSING_DOSE_PRO_2")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceFirmwareManifestUrlResolver.resolve(
                "$DEVICE_FIRMWARE_MANIFEST_URL/$DEVICE_FIRMWARE_MANIFEST_ENV_PLACEHOLDER",
                "DOSING_DOSE_PRO_2"
            )
        }
    }
}
