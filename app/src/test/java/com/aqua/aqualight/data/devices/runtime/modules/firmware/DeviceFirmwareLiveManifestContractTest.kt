package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class DeviceFirmwareLiveManifestContractTest {

    @Test
    fun `published stable manifest verifies and matches the Android commercial catalog`() {
        val manifestPath = System.getenv(ENV_MANIFEST_PATH).orEmpty()
        assumeTrue("Live OTA manifest path is not configured.", manifestPath.isNotBlank())

        val publicKeyPem = System.getenv(ENV_PUBLIC_KEY_PEM).orEmpty()
        val expectedKeyId = System.getenv(ENV_KEY_ID).orEmpty()
        assertTrue("OTA manifest public key is missing from the build environment.", publicKeyPem.isNotBlank())
        assertTrue("OTA manifest key id is missing from the build environment.", expectedKeyId.isNotBlank())

        val rawManifest = File(manifestPath).readText(Charsets.UTF_8)
        assertTrue("Downloaded stable OTA manifest is empty.", rawManifest.isNotBlank())

        val manifest = DeviceFirmwareManifestSignatureVerifier(
            publicKeyPem = publicKeyPem,
            expectedKeyId = expectedKeyId
        ).verifyAndParse(rawManifest).getOrThrow()

        assertEquals(DeviceFirmwareRuntimeContract.Manifest.SCHEMA, manifest.schema)
        assertEquals(DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL, manifest.channel)
        assertEquals(
            DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY,
            manifest.releaseRepo
        )
        assertTrue("Published stable manifest has no OTA artifacts.", manifest.artifacts.isNotEmpty())

        val root = JSONObject(rawManifest)
        assertFalse(
            "Published OTA manifest must not contain a user-defined device name.",
            root.containsAnyKey(setOf("deviceName", "customName"))
        )

        manifest.artifacts.forEach { artifact ->
            val matchingProducts = AqlCommercialDeviceCatalog.products.filter { product ->
                artifact.env == product.productKey.value.lowercase() &&
                    artifact.compatibility.productKey == product.productKey.value &&
                    artifact.compatibility.productId == product.productId.value &&
                    artifact.compatibility.family == product.family.wireValue &&
                    artifact.compatibility.line == product.line.value &&
                    artifact.compatibility.model == product.model.value &&
                    artifact.compatibility.hardwareRevision == product.hardwareRevision.value
            }
            assertEquals(
                "Live manifest artifact ${artifact.env} must match exactly one Android catalog product.",
                1,
                matchingProducts.size
            )
            val product = matchingProducts.single()
            assertEquals(product.displayName, artifact.product.displayName)
            assertEquals(product.skuCode.value, artifact.product.skuCode)
            assertEquals(product.profile.capabilities.light, artifact.product.capabilities.light)
            assertEquals(product.profile.capabilities.manualLight, artifact.product.capabilities.manualLight)
            assertEquals(product.profile.capabilities.lightProgram, artifact.product.capabilities.lightProgram)
            assertEquals(product.profile.capabilities.lightPresets, artifact.product.capabilities.lightPresets)
            assertEquals(product.profile.capabilities.lightSimulation, artifact.product.capabilities.lightSimulation)
            assertEquals(product.profile.capabilities.fan, artifact.product.capabilities.fan)
            assertEquals(product.profile.capabilities.cooling, artifact.product.capabilities.cooling)
            assertEquals(product.profile.capabilities.temperature, artifact.product.capabilities.temperature)
            assertEquals(product.profile.capabilities.standaloneTimer, artifact.product.capabilities.standaloneTimer)
            assertEquals(product.profile.capabilities.dosing, artifact.product.capabilities.dosing)
            assertEquals(product.profile.capabilities.timeSync, artifact.product.capabilities.timeSync)
            assertEquals(product.profile.capabilities.ota, artifact.product.capabilities.ota)
            assertEquals(product.limits.lightChannelCount, artifact.product.limits.lightChannelCount)
            assertEquals(product.limits.fanOutputCount, artifact.product.limits.fanOutputCount)
            assertEquals(
                product.limits.temperatureSensorCount,
                artifact.product.limits.temperatureSensorCount
            )
            assertEquals(product.limits.timerChannelCount, artifact.product.limits.timerChannelCount)
            assertEquals(product.limits.dosingChannelCount, artifact.product.limits.dosingChannelCount)
        }
    }

    private fun Any?.containsAnyKey(forbiddenKeys: Set<String>): Boolean = when (this) {
        is JSONObject -> {
            val keys = buildList {
                val iterator = keys()
                while (iterator.hasNext()) add(iterator.next())
            }
            keys.any(forbiddenKeys::contains) || keys.any { key ->
                opt(key).containsAnyKey(forbiddenKeys)
            }
        }
        is JSONArray -> (0 until length()).any { index ->
            opt(index).containsAnyKey(forbiddenKeys)
        }
        else -> false
    }

    private companion object {
        const val ENV_MANIFEST_PATH = "AQL_LIVE_OTA_MANIFEST_PATH"
        const val ENV_PUBLIC_KEY_PEM = "AQL_OTA_MANIFEST_PUBLIC_KEY_PEM"
        const val ENV_KEY_ID = "AQL_OTA_MANIFEST_KEY_ID"
    }
}
