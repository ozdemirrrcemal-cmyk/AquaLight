package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DEVICE_FIRMWARE_PRODUCT_ENVIRONMENTS
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareProductIsolationMatrixTest {

    @Test
    fun `all nine product releases are isolated from every other model`() {
        val products = AqlCommercialDeviceCatalog.products
        val environments = products.map { it.productKey.value.lowercase() }.toSet()

        assertEquals(9, products.size)
        assertEquals(DEVICE_FIRMWARE_PRODUCT_ENVIRONMENTS, environments)

        for (releaseProduct in products) {
            val manifest = manifest(releaseProduct)
            val releaseEnvironment = releaseProduct.productKey.value.lowercase()
            val channelUrl =
                DeviceFirmwareRuntimeContract.OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX +
                    "stable/$releaseEnvironment/manifest-stable.json"
            assertEquals(manifest, requireFirmwareManifestMatchesUrl(channelUrl, manifest))
            val otherEnvironment = environments.first { it != releaseEnvironment }
            assertThrows(IllegalArgumentException::class.java) {
                requireFirmwareManifestMatchesUrl(
                    DeviceFirmwareRuntimeContract.OFFICIAL_CHANNEL_MANIFEST_URL_PREFIX +
                        "stable/$otherEnvironment/manifest-stable.json",
                    manifest
                )
            }
            for (deviceProduct in products) {
                val availability = DeviceFirmwareUpdatePlanner().evaluateUpdate(
                    snapshot = snapshot(deviceProduct),
                    manifest = manifest
                ).getOrThrow()

                if (deviceProduct.productKey == releaseProduct.productKey) {
                    assertTrue(availability is DeviceFirmwareAvailability.UpdateAvailable)
                } else {
                    val upToDate = availability as DeviceFirmwareAvailability.UpToDate
                    assertEquals(CURRENT_VERSION, upToDate.currentVersion)
                    assertEquals(CURRENT_VERSION, upToDate.latestVersion)
                }
            }
        }
    }

    private fun manifest(product: AqlCommercialCatalogProduct): DeviceFirmwareManifest {
        val environment = product.productKey.value.lowercase()
        val tag = "$environment-v$TARGET_VERSION"
        val filename = "AquaLight-$tag-ota.bin"
        return DeviceFirmwareManifest(
            schema = DeviceFirmwareRuntimeContract.Manifest.SCHEMA,
            brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
            channel = DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL,
            version = TARGET_VERSION,
            tag = tag,
            releaseRepo = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY,
            generatedAt = "2026-08-08T00:00:00+00:00",
            platform = OFFICIAL_PLATFORM,
            releaseNotes = DeviceFirmwareReleaseNotes(
                schema = DeviceFirmwareRuntimeContract.ReleaseNotes.SCHEMA,
                defaultLocale = DeviceFirmwareRuntimeContract.ReleaseNotes.DEFAULT_LOCALE,
                items = listOf(DeviceFirmwareReleaseNoteItem("Ürün güncellemesi.", "Product update."))
            ),
            artifacts = listOf(artifact(product, environment, tag, filename)),
            signature = DeviceFirmwareManifestSignature(
                scheme = DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256,
                keyId = "release-key-1",
                payloadHash = "b".repeat(64),
                value = "signed-value"
            )
        )
    }

    private fun artifact(
        product: AqlCommercialCatalogProduct,
        environment: String,
        tag: String,
        filename: String
    ): DeviceFirmwareManifestArtifact = DeviceFirmwareManifestArtifact(
        env = environment,
        product = DeviceFirmwareManifestProduct(
            productKey = product.productKey.value,
            productId = product.productId.value,
            brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
            family = product.family.wireValue,
            line = product.line.value,
            model = product.model.value,
            displayName = "AquaLight ${product.displayName}",
            skuCode = product.skuCode.value,
            hardwareRevision = product.hardwareRevision.value,
            capabilities = product.capabilities(),
            limits = product.limits()
        ),
        compatibility = DeviceFirmwareCompatibility(
            productKey = product.productKey.value,
            productId = product.productId.value,
            family = product.family.wireValue,
            line = product.line.value,
            model = product.model.value,
            hardwareRevision = product.hardwareRevision.value
        ),
        firmware = DeviceFirmwareAsset(
            version = TARGET_VERSION,
            filename = filename,
            url = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX + "$tag/$filename",
            sha256 = "a".repeat(64),
            size = 1_048_576,
            format = DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT,
            otaSlotCompatible = true
        ),
        factory = null
    )

    private fun snapshot(product: AqlCommercialCatalogProduct): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(uid = DeviceUid("AQL-${product.productKey.value}")),
        product = DeviceProduct(
            brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
            productId = product.productId.value,
            productKey = product.productKey.value,
            family = product.family,
            familyRaw = product.family.wireValue,
            line = product.line.value,
            model = product.model.value,
            displayName = product.displayName,
            skuCode = product.skuCode.value,
            hardwareRevision = product.hardwareRevision.value
        ),
        firmwareVersion = CURRENT_VERSION,
        capabilities = product.capabilities(),
        limits = product.limits(),
        runtimeMetadataGeneration = 1L
    )

    private fun AqlCommercialCatalogProduct.capabilities(): DeviceCapabilities =
        DeviceCapabilities(
            light = profile.capabilities.light,
            manualLight = profile.capabilities.manualLight,
            lightProgram = profile.capabilities.lightProgram,
            lightPresets = profile.capabilities.lightPresets,
            lightSimulation = profile.capabilities.lightSimulation,
            fan = profile.capabilities.fan,
            cooling = profile.capabilities.cooling,
            temperature = profile.capabilities.temperature,
            standaloneTimer = profile.capabilities.standaloneTimer,
            dosing = profile.capabilities.dosing,
            timeSync = profile.capabilities.timeSync,
            ota = profile.capabilities.ota
        )

    private fun AqlCommercialCatalogProduct.limits(): DeviceLimits = DeviceLimits(
        lightChannelCount = limits.lightChannelCount,
        fanOutputCount = limits.fanOutputCount,
        temperatureSensorCount = limits.temperatureSensorCount,
        timerChannelCount = limits.timerChannelCount,
        dosingChannelCount = limits.dosingChannelCount
    )

    private companion object {
        const val CURRENT_VERSION = "1.0.0"
        const val TARGET_VERSION = "2.0.0"
        val OFFICIAL_PLATFORM = DeviceFirmwareManifestPlatform(
            framework = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_FRAMEWORK,
            core = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_CORE,
            platform = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_PACKAGE,
            partitionTable = DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE,
            normalOtaAssetType = DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE
        )
    }
}
