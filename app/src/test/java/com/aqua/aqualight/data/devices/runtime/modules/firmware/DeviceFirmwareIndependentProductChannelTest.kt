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
    private val wrgb = ProductFixture(
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
    private val dosePro4 = ProductFixture(
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

    @Test
    fun `wrgb remains up to date when dose pro 4 advances independently`() {
        val wrgbManifest = manifest(wrgb, WRGB_VERSION)
        val doseManifest = manifest(dosePro4, DOSE_PRO_4_VERSION)

        val wrgbAvailability = planner.evaluateUpdate(
            snapshot(wrgb, installedVersion = WRGB_VERSION),
            wrgbManifest
        ).getOrThrow()
        val doseAvailability = planner.evaluateUpdate(
            snapshot(dosePro4, installedVersion = WRGB_VERSION),
            doseManifest
        ).getOrThrow()

        assertTrue(wrgbAvailability is DeviceFirmwareAvailability.UpToDate)
        assertEquals(
            WRGB_VERSION,
            (wrgbAvailability as DeviceFirmwareAvailability.UpToDate).latestVersion
        )
        assertTrue(doseAvailability is DeviceFirmwareAvailability.UpdateAvailable)
        assertEquals(
            DOSE_PRO_4_VERSION,
            (doseAvailability as DeviceFirmwareAvailability.UpdateAvailable).plan.targetVersion
        )
        assertEquals("${wrgb.env}-v$WRGB_VERSION", wrgbManifest.tag)
        assertEquals("${dosePro4.env}-v$DOSE_PRO_4_VERSION", doseManifest.tag)
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
            releaseNotes = releaseNotes(),
            artifacts = listOf(
                manifestArtifact(
                    fixture = fixture,
                    version = version,
                    releaseTag = releaseTag,
                    filename = filename
                )
            ),
            signature = signature()
        )
    }

    private fun manifestArtifact(
        fixture: ProductFixture,
        version: String,
        releaseTag: String,
        filename: String
    ): DeviceFirmwareManifestArtifact = DeviceFirmwareManifestArtifact(
        env = fixture.env,
        product = manifestProduct(fixture),
        compatibility = compatibility(fixture),
        firmware = firmwareAsset(
            version = version,
            releaseTag = releaseTag,
            filename = filename
        ),
        factory = null
    )

    private fun manifestProduct(fixture: ProductFixture): DeviceFirmwareManifestProduct =
        DeviceFirmwareManifestProduct(
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
        )

    private fun compatibility(fixture: ProductFixture): DeviceFirmwareCompatibility =
        DeviceFirmwareCompatibility(
            productKey = fixture.productKey,
            productId = fixture.productId,
            family = fixture.family.wireValue,
            line = fixture.line,
            model = fixture.model,
            hardwareRevision = HARDWARE_REVISION
        )

    private fun firmwareAsset(
        version: String,
        releaseTag: String,
        filename: String
    ): DeviceFirmwareAsset = DeviceFirmwareAsset(
        version = version,
        filename = filename,
        url = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "$releaseTag/$filename",
        sha256 = "a".repeat(64),
        size = 1_048_576,
        format = DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT,
        otaSlotCompatible = true
    )

    private fun releaseNotes(): DeviceFirmwareReleaseNotes = DeviceFirmwareReleaseNotes(
        schema = DeviceFirmwareRuntimeContract.ReleaseNotes.SCHEMA,
        defaultLocale = DeviceFirmwareRuntimeContract.ReleaseNotes.DEFAULT_LOCALE,
        items = listOf(
            DeviceFirmwareReleaseNoteItem(
                tr = "Ürün kanalına özel kararlılık güncellemesi.",
                en = "Product-channel stability update."
            )
        )
    )

    private fun signature(): DeviceFirmwareManifestSignature = DeviceFirmwareManifestSignature(
        scheme = DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256,
        keyId = "release-key-1",
        payloadHash = "b".repeat(64),
        value = "signed-value"
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
        const val WRGB_VERSION = "1.0.1"
        const val DOSE_PRO_4_VERSION = "1.0.2"
        val OFFICIAL_PLATFORM = DeviceFirmwareManifestPlatform(
            framework = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_FRAMEWORK,
            core = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_CORE,
            platform = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_PACKAGE,
            partitionTable = DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE,
            normalOtaAssetType = DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE
        )
    }
}
