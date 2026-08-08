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

class DosePro4NoPublishedArtifactRegressionTest {

    @Test
    fun `dose pro 4 without a published artifact is a neutral no-update result`() {
        val availability = DeviceFirmwareUpdatePlanner().evaluateUpdate(
            snapshot = dosePro4Snapshot(),
            manifest = manifestContainingOnlyDosePro2()
        ).getOrThrow() as DeviceFirmwareAvailability.UpToDate

        assertEquals("1.0.0", availability.currentVersion)
        assertEquals("1.0.0", availability.latestVersion)
        assertTrue(!availability.releaseContent.isPresent)
    }

    private fun dosePro4Snapshot() = DeviceSnapshot(
        identity = DeviceIdentity(uid = DeviceUid("AQL-DP4-NO-ARTIFACT")),
        product = DeviceProduct(
            brand = "AquaLight",
            productId = "com.aqualight.dosing.dose_pro_4",
            productKey = "DOSING_DOSE_PRO_4",
            family = DeviceFamily.DOSING,
            familyRaw = "dosing",
            line = "dose_pro",
            model = "dose_pro_4",
            displayName = "Dose Pro 4",
            skuCode = "AQL-D-DP4-GLB-BLK",
            hardwareRevision = "2.0"
        ),
        firmwareVersion = "1.0.0",
        capabilities = DeviceCapabilities(
            dosing = true,
            timeSync = true,
            ota = true
        ),
        limits = DeviceLimits(dosingChannelCount = 4),
        runtimeMetadataGeneration = 1L
    )

    private fun manifestContainingOnlyDosePro2(): DeviceFirmwareManifest {
        val capabilities = DeviceCapabilities(
            dosing = true,
            timeSync = true,
            ota = true
        )
        val limits = DeviceLimits(dosingChannelCount = 2)
        val env = "dosing_dose_pro_2"
        val filename = "AquaLight-$env-v1.1.0-ota.bin"
        val artifact = DeviceFirmwareManifestArtifact(
            env = env,
            product = DeviceFirmwareManifestProduct(
                productKey = "DOSING_DOSE_PRO_2",
                productId = "com.aqualight.dosing.dose_pro_2",
                brand = "AquaLight",
                family = "dosing",
                line = "dose_pro",
                model = "dose_pro_2",
                displayName = "AquaLight Dose Pro 2",
                skuCode = "AQL-D-DP2-GLB-BLK",
                hardwareRevision = "2.0",
                capabilities = capabilities,
                limits = limits
            ),
            compatibility = DeviceFirmwareCompatibility(
                productKey = "DOSING_DOSE_PRO_2",
                productId = "com.aqualight.dosing.dose_pro_2",
                family = "dosing",
                line = "dose_pro",
                model = "dose_pro_2",
                hardwareRevision = "2.0"
            ),
            firmware = DeviceFirmwareAsset(
                version = "1.1.0",
                filename = filename,
                url = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
                    "v1.1.0/$filename",
                sha256 = "a".repeat(64),
                size = 1_024,
                format = DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT,
                otaSlotCompatible = true
            ),
            factory = null
        )
        return DeviceFirmwareManifest(
            schema = DeviceFirmwareRuntimeContract.Manifest.SCHEMA,
            brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
            channel = DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL,
            version = "1.1.0",
            tag = "v1.1.0",
            releaseRepo = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY,
            generatedAt = "2026-08-08T00:00:00+00:00",
            platform = DeviceFirmwareManifestPlatform(
                framework = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_FRAMEWORK,
                core = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_CORE,
                platform = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_PACKAGE,
                partitionTable = DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE,
                normalOtaAssetType =
                    DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE
            ),
            releaseNotes = DeviceFirmwareReleaseNotes(
                schema = DeviceFirmwareRuntimeContract.ReleaseNotes.SCHEMA,
                defaultLocale = DeviceFirmwareRuntimeContract.ReleaseNotes.DEFAULT_LOCALE,
                items = emptyList()
            ),
            artifacts = listOf(artifact),
            signature = DeviceFirmwareManifestSignature(
                scheme = DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256,
                keyId = "release-key-1",
                payloadHash = "b".repeat(64),
                value = "signed-value"
            )
        )
    }
}
