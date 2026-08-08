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

        assertEquals(CURRENT_VERSION, availability.currentVersion)
        assertEquals(CURRENT_VERSION, availability.latestVersion)
        assertTrue(!availability.releaseContent.isPresent)
    }

    private fun dosePro4Snapshot() = DeviceSnapshot(
        identity = DeviceIdentity(uid = DeviceUid("AQL-DP4-NO-ARTIFACT")),
        product = DeviceProduct(
            brand = BRAND,
            productId = "com.aqualight.dosing.dose_pro_4",
            productKey = "DOSING_DOSE_PRO_4",
            family = DeviceFamily.DOSING,
            familyRaw = FAMILY,
            line = LINE,
            model = "dose_pro_4",
            displayName = "Dose Pro 4",
            skuCode = "AQL-D-DP4-GLB-BLK",
            hardwareRevision = HARDWARE_REVISION
        ),
        firmwareVersion = CURRENT_VERSION,
        capabilities = dosingCapabilities(channelCount = 4),
        limits = DeviceLimits(dosingChannelCount = 4),
        runtimeMetadataGeneration = 1L
    )

    private fun manifestContainingOnlyDosePro2() = DeviceFirmwareManifest(
        schema = DeviceFirmwareRuntimeContract.Manifest.SCHEMA,
        brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
        channel = DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL,
        version = RELEASE_VERSION,
        tag = RELEASE_TAG,
        releaseRepo = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY,
        generatedAt = "2026-08-08T00:00:00+00:00",
        platform = officialPlatform(),
        releaseNotes = releaseNotes(),
        artifacts = listOf(dosePro2Artifact()),
        signature = manifestSignature()
    )

    private fun dosePro2Artifact(): DeviceFirmwareManifestArtifact {
        val filename = "AquaLight-$DOSE_PRO_2_ENV-$RELEASE_TAG-ota.bin"
        return DeviceFirmwareManifestArtifact(
            env = DOSE_PRO_2_ENV,
            product = dosePro2Product(),
            compatibility = dosePro2Compatibility(),
            firmware = DeviceFirmwareAsset(
                version = RELEASE_VERSION,
                filename = filename,
                url = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
                    "$RELEASE_TAG/$filename",
                sha256 = "a".repeat(64),
                size = 1_024,
                format = DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT,
                otaSlotCompatible = true
            ),
            factory = null
        )
    }

    private fun dosePro2Product() = DeviceFirmwareManifestProduct(
        productKey = DOSE_PRO_2_PRODUCT_KEY,
        productId = DOSE_PRO_2_PRODUCT_ID,
        brand = BRAND,
        family = FAMILY,
        line = LINE,
        model = DOSE_PRO_2_MODEL,
        displayName = "AquaLight Dose Pro 2",
        skuCode = "AQL-D-DP2-GLB-BLK",
        hardwareRevision = HARDWARE_REVISION,
        capabilities = dosingCapabilities(channelCount = 2),
        limits = DeviceLimits(dosingChannelCount = 2)
    )

    private fun dosePro2Compatibility() = DeviceFirmwareCompatibility(
        productKey = DOSE_PRO_2_PRODUCT_KEY,
        productId = DOSE_PRO_2_PRODUCT_ID,
        family = FAMILY,
        line = LINE,
        model = DOSE_PRO_2_MODEL,
        hardwareRevision = HARDWARE_REVISION
    )

    private fun dosingCapabilities(channelCount: Int) = DeviceCapabilities(
        dosing = channelCount > 0,
        timeSync = true,
        ota = true
    )

    private fun officialPlatform() = DeviceFirmwareManifestPlatform(
        framework = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_FRAMEWORK,
        core = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_CORE,
        platform = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_PACKAGE,
        partitionTable = DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE,
        normalOtaAssetType = DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE
    )

    private fun releaseNotes() = DeviceFirmwareReleaseNotes(
        schema = DeviceFirmwareRuntimeContract.ReleaseNotes.SCHEMA,
        defaultLocale = DeviceFirmwareRuntimeContract.ReleaseNotes.DEFAULT_LOCALE,
        items = listOf(
            DeviceFirmwareReleaseNoteItem(
                tr = "Dose Pro 2 güncellemesi.",
                en = "Dose Pro 2 update."
            )
        )
    )

    private fun manifestSignature() = DeviceFirmwareManifestSignature(
        scheme = DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256,
        keyId = "release-key-1",
        payloadHash = "b".repeat(64),
        value = "signed-value"
    )

    private companion object {
        const val BRAND = "AquaLight"
        const val FAMILY = "dosing"
        const val LINE = "dose_pro"
        const val HARDWARE_REVISION = "2.0"
        const val CURRENT_VERSION = "1.0.0"
        const val RELEASE_VERSION = "1.1.0"
        const val RELEASE_TAG = "v1.1.0"
        const val DOSE_PRO_2_ENV = "dosing_dose_pro_2"
        const val DOSE_PRO_2_PRODUCT_KEY = "DOSING_DOSE_PRO_2"
        const val DOSE_PRO_2_PRODUCT_ID = "com.aqualight.dosing.dose_pro_2"
        const val DOSE_PRO_2_MODEL = "dose_pro_2"
    }
}
