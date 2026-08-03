package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import java.io.File
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareLiveManifestContractTest {

    @Test
    fun `published stable manifest verifies and matches the Android commercial catalog`() {
        val verified = loadVerifiedManifest()
        assertManifestHeader(verified.manifest)
        assertNoUserDefinedDeviceName(verified.rawManifest)

        verified.manifest.artifacts.forEach { artifact ->
            assertEquals(verified.manifest.version, artifact.firmware.version)
            val product = requireExactCatalogProduct(artifact)
            DeviceFirmwareManifestContractValidator.requireValid(
                artifact = artifact,
                manifest = verified.manifest,
                product = product
            )
        }
    }

    private fun loadVerifiedManifest(): VerifiedManifest {
        val publicKeyPem = System.getenv(ENV_PUBLIC_KEY_PEM).orEmpty()
        val expectedKeyId = System.getenv(ENV_KEY_ID).orEmpty()
        assertTrue(
            "OTA manifest public key is missing from the build environment.",
            publicKeyPem.isNotBlank()
        )
        assertTrue(
            "OTA manifest key id is missing from the build environment.",
            expectedKeyId.isNotBlank()
        )

        val rawManifest = readPublishedManifest()
        assertTrue("Published stable OTA manifest is empty.", rawManifest.isNotBlank())
        val manifest = DeviceFirmwareManifestSignatureVerifier(
            publicKeyPem = publicKeyPem,
            expectedKeyId = expectedKeyId
        ).verifyAndParse(rawManifest).getOrThrow()
        return VerifiedManifest(rawManifest = rawManifest, manifest = manifest)
    }

    private fun readPublishedManifest(): String {
        val manifestPath = System.getenv(ENV_MANIFEST_PATH).orEmpty()
        if (manifestPath.isNotBlank()) return File(manifestPath).readText(Charsets.UTF_8)

        val connection = URI(DeviceFirmwareRuntimeContract.STABLE_MANIFEST_URL)
            .toURL()
            .openConnection()
            .apply {
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
            }
        return connection.getInputStream().bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText()
        }
    }

    private fun assertManifestHeader(manifest: DeviceFirmwareManifest) {
        assertEquals(DeviceFirmwareRuntimeContract.Manifest.SCHEMA, manifest.schema)
        assertEquals(DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL, manifest.channel)
        assertEquals(
            DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY,
            manifest.releaseRepo
        )
        assertTrue("Published stable manifest has no OTA artifacts.", manifest.artifacts.isNotEmpty())
    }

    private fun assertNoUserDefinedDeviceName(rawManifest: String) {
        assertFalse(
            "Published OTA manifest must not contain a user-defined device name.",
            JSONObject(rawManifest).containsAnyKey(setOf("deviceName", "customName"))
        )
    }

    private fun requireExactCatalogProduct(
        artifact: DeviceFirmwareManifestArtifact
    ): AqlCommercialCatalogProduct {
        val manifestIdentity = DeviceFirmwareProductIdentity.fromCompatibility(
            artifact.compatibility
        )
        val matchingProducts = AqlCommercialDeviceCatalog.products.filter { product ->
            DeviceFirmwareProductIdentity.fromCatalog(product) == manifestIdentity
        }
        assertEquals(
            "Live manifest artifact ${artifact.env} must match exactly one Android catalog product.",
            1,
            matchingProducts.size
        )
        return matchingProducts.single()
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

    private data class VerifiedManifest(
        val rawManifest: String,
        val manifest: DeviceFirmwareManifest
    )

    private companion object {
        const val ENV_MANIFEST_PATH = "AQL_LIVE_OTA_MANIFEST_PATH"
        const val ENV_PUBLIC_KEY_PEM = "AQL_OTA_MANIFEST_PUBLIC_KEY_PEM"
        const val ENV_KEY_ID = "AQL_OTA_MANIFEST_KEY_ID"
        const val NETWORK_TIMEOUT_MS = 20_000
    }
}
