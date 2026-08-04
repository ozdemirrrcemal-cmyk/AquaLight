package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareIndependentProductChannelTest {

    private val planner = DeviceFirmwareUpdatePlanner { listOf("tr") }

    @Test
    fun `wrgb remains up to date when dose pro 4 advances independently`() {
        val wrgb = product(
            env = "light_wrgb_pro_elite",
            productKey = "LIGHT_WRGB_PRO_ELITE",
            productId = "com.aqualight.light.wrgb_pro_elite",
            family = DeviceFamily.LIGHT,
            line = "wrgb_pro",
            model = "wrgb_pro_elite_120",
            displayName = "WRGB Pro Elite 120",
            skuCode = "AQL-L-WPE120-GLB-BLK",
            capabilities = DeviceCapabilities(
                light = true,
                manualLight = true,
                lightProgram = true,
                lightPresets = true,
                fan = true,
                cooling = true,
                temperature = true,
                timeSync = true,
                ota = true
            ),
            limits = DeviceLimits(
                lightChannelCount = 4,
                fanOutputCount = 2,
                temperatureSensorCount = 1
            )
        )
        val dosePro4 = product(
            env = "dosing_dose_pro_4",
            productKey = "DOSING_DOSE_PRO_4",
            productId = "com.aqualight.dosing.dose_pro_4",
            family = DeviceFamily.DOSING,
            line = "dose_pro",
            model = "dose_pro_4",
            displayName = "Dose Pro 4",
            skuCode = "AQL-D-DP4-GLB-BLK",
            capabilities = DeviceCapabilities(
                dosing = true,
                timeSync = true,
                ota = true
            ),
            limits = DeviceLimits(dosingChannelCount = 4)
        )

        val wrgbAvailability = planner.evaluateUpdate(
            snapshot(wrgb, installedVersion = "1.0.1"),
            manifest(wrgb, version = "1.0.1")
        ).getOrThrow()
        val doseAvailability = planner.evaluateUpdate(
            snapshot(dosePro4, installedVersion = "1.0.1"),
            manifest(dosePro4, version = "1.0.2")
        ).getOrThrow()

        assertTrue(wrgbAvailability is DeviceFirmwareAvailability.UpToDate)
        assertEquals(
            "1.0.1",
            (wrgbAvailability as DeviceFirmwareAvailability.UpToDate).latestVersion
        )
        assertTrue(doseAvailability is DeviceFirmwareAvailability.UpdateAvailable)
        assertEquals(
            "1.0.2",
            (doseAvailability as DeviceFirmwareAvailability.UpdateAvailable).plan.targetVersion
        )
        assertEquals("light_wrgb_pro_elite-v1.0.1", manifest(wrgb, "1.0.1").tag)
        assertEquals("dosing_dose_pro_4-v1.0.2", manifest(dosePro4, "1.0.2").tag)
    }

    private fun snapshot(
        fixture: ProductFixture,
        installedVersion: String
    ): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(
            uid = DeviceUid("AQL-${fixture.productKey}-TEST"),
            customName = "Owner display name"
        ),
        product = DeviceProduct(
            brand = "AquaLight",
            productId = fixture.productId,
            productKey = fixture.productKey,
            family = fixture.family,
            familyRaw = fixture.family.wireValue,
            line = fixture.line,
            model = fixture.model,
            displayName = fixture.displayName,
            skuCode = fixture.skuCode,
            hardwareRevision = HARDWARE_REVISION
        ),
        firmwareVersion = installedVersion,
        apiVersion = "1",
        protocolVersion = "1",
        capabilities = fixture.capabilities,
        limits = fixture.limits,
        runtimeMetadataGeneration = 11L
    )

    private fun manifest(
        fixture: ProductFixture,
        version: String
    ): DeviceFirmwareManifest {
        val releaseTag = "${fixture.env}-v$version"
        val filename = "AquaLight-${fixture.env}-v$version-ota.bin"
        return DeviceFirmwareManifest(
            schema = DeviceFirmwareRuntimeContract.Manifest.SCHEMA,
            brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
            channel = DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL,
            version = version,
            tag = releaseTag,
            releaseRepo = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY,
            generatedAt = "2026-08-04T00:00:00+00:00",
            platform = OFFICIAL_PLATFORM,
            releaseNotes = DeviceFirmwareReleaseNotes(
                schema = DeviceFirmwareRuntimeContract.ReleaseNotes.SCHEMA,
                defaultLocale = DeviceFirmwareRuntimeContract.ReleaseNotes.DEFAULT_LOCALE,
                items = listOf(
                    DeviceFirmwareReleaseNoteItem(
                        tr = "Ürün kanalına özel kararlılık güncellemesi.",
                        en = "Product-channel stability update."
                    )
                )
            ),
            artifacts = listOf(
                DeviceFirmwareManifestArtifact(
                    env = fixture.env,
                    product = DeviceFirmwareManifestProduct(
                        productKey = fixture.productKey,
                        productId = fixture.productId,
                        brand = "AquaLight",
                        family = fixture.family.wireValue,
                        line = fixture.line,
                        model = fixture.model,
                        displayName = "AquaLight ${fixture.displayName}",
                        skuCode = fixture.skuCode,
                        hardwareRevision = HARDWARE_REVISION,
                        capabilities = fixture.capabilities,
                        limits = fixture.limits
                    ),
                    compatibility = DeviceFirmwareCompatibility(
                        productKey = fixture.productKey,
                        productId = fixture.productId,
                        family = fixture.family.wireValue,
                        line = fixture.line,
                        model = fixture.model,
                        hardwareRevision = HARDWARE_REVISION
                    ),
                    firmware = DeviceFirmwareAsset(
                        version = version,
                        filename = filename,
                        url = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
                            "$releaseTag/$filename",
                        sha256 = "a".repeat(64),
                        size = 1_048_576,
                        format = DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT,
                        otaSlotCompatible = true
                    ),
                    factory = null
                )
            ),
            signature = DeviceFirmwareManifestSignature(
                scheme = DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256,
                keyId = "release-key-1",
                payloadHash = "b".repeat(64),
                value = "signed-value"
            )
        )
    }

    private fun product(
        env: String,
        productKey: String,
        productId: String,
        family: DeviceFamily,
        line: String,
        model: String,
        displayName: String,
        skuCode: String,
        capabilities: DeviceCapabilities,
        limits: DeviceLimits
    ) = ProductFixture(
        env = env,
        productKey = productKey,
        productId = productId,
        family = family,
        line = line,
        model = model,
        displayName = displayName,
        skuCode = skuCode,
        capabilities = capabilities,
        limits = limits
    )

    private data class ProductFixture(
        val env: String,
        val productKey: String,
        val productId: String,
        val family: DeviceFamily,
        val line: String,
        val model: String,
        val displayName: String,
        val skuCode: String,
        val capabilities: DeviceCapabilities,
        val limits: DeviceLimits
    )

    private companion object {
        const val HARDWARE_REVISION = "2.0"
        val OFFICIAL_PLATFORM = DeviceFirmwareManifestPlatform(
            framework = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_FRAMEWORK,
            core = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_CORE,
            platform = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_PACKAGE,
            partitionTable = DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE,
            normalOtaAssetType = DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE
        )
    }
}
