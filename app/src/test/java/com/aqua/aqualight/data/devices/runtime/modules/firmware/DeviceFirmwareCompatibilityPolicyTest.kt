package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareUpdatePolicy
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdatePolicyLevel
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareCompatibilityPolicyTest {

    @Test
    fun `newer required transport contract is rejected before an OTA plan exists`() {
        val manifest = manifest(
            artifact().copy(
                contracts = baseContracts().copy(wsProtocolVersion = 2)
            )
        )

        val failure = DeviceFirmwareUpdatePlanner()
            .planUpdate(snapshot(), manifest)
            .exceptionOrNull()

        assertTrue(failure is DeviceFirmwareClientIncompatibleException)
    }

    @Test
    fun `unknown optional domain is additive and does not block base firmware update`() {
        val manifest = manifest(
            artifact().copy(
                contracts = baseContracts().copy(
                    optionalDomains = setOf("aqualight.dosing.future-telemetry.v9")
                )
            )
        )

        val plan = DeviceFirmwareUpdatePlanner().planUpdate(snapshot(), manifest).getOrThrow()

        assertEquals(TARGET_VERSION, plan.targetVersion)
    }

    @Test
    fun `feature required policy is preserved without making the whole release mandatory`() {
        val policy = DeviceFirmwareUpdatePolicy(
            level = DeviceFirmwareUpdatePolicyLevel.FEATURE_REQUIRED,
            requiredFeatures = setOf("DOSING_CALIBRATION")
        )
        val manifest = manifest(
            artifact().copy(
                features = setOf("DOSING_CONTROL", "DOSING_CALIBRATION", "OTA_UPDATE"),
                updatePolicy = policy
            )
        )

        val plan = DeviceFirmwareUpdatePlanner().planUpdate(snapshot(), manifest).getOrThrow()

        assertEquals(policy, plan.updatePolicy)
        assertEquals(
            setOf("DOSING_CONTROL", "DOSING_CALIBRATION", "OTA_UPDATE"),
            plan.targetFeatures
        )
        assertFalse(plan.releaseContent.mandatory)
    }

    @Test
    fun `compatibility required policy is explicit global requirement but keeps OTA plan available`() {
        val policy = DeviceFirmwareUpdatePolicy(
            level = DeviceFirmwareUpdatePolicyLevel.COMPATIBILITY_REQUIRED
        )
        val manifest = manifest(artifact().copy(updatePolicy = policy))

        val plan = DeviceFirmwareUpdatePlanner().planUpdate(snapshot(), manifest).getOrThrow()

        assertTrue(plan.updatePolicy.isGloballyRequired)
        assertTrue(plan.releaseContent.mandatory)
        assertEquals(TARGET_VERSION, plan.targetVersion)
    }

    private fun manifest(
        artifact: DeviceFirmwareManifestArtifact
    ): DeviceFirmwareManifest = DeviceFirmwareManifest(
        schema = DeviceFirmwareRuntimeContract.Manifest.SCHEMA,
        brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
        channel = DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL,
        version = TARGET_VERSION,
        tag = RELEASE_TAG,
        releaseRepo = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY,
        generatedAt = "2026-09-21T00:00:00+00:00",
        platform = DeviceFirmwareManifestPlatform(
            framework = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_FRAMEWORK,
            core = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_CORE,
            platform = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_PACKAGE,
            partitionTable = DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE,
            normalOtaAssetType = DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE
        ),
        releaseNotes = DeviceFirmwareReleaseNotes(
            schema = DeviceFirmwareRuntimeContract.ReleaseNotes.SCHEMA,
            defaultLocale = DeviceFirmwareRuntimeContract.ReleaseNotes.DEFAULT_LOCALE,
            items = listOf(
                DeviceFirmwareReleaseNoteItem(
                    tr = "Ticari uyumluluk güncellemesi.",
                    en = "Commercial compatibility update."
                )
            )
        ),
        artifacts = listOf(artifact),
        signature = DeviceFirmwareManifestSignature(
            scheme = DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256,
            keyId = "release-key-1",
            payloadHash = "b".repeat(64),
            value = "signed-value"
        )
    )

    private fun artifact(): DeviceFirmwareManifestArtifact {
        val filename = "AquaLight-$RELEASE_TAG-ota.bin"
        return DeviceFirmwareManifestArtifact(
            env = ENVIRONMENT,
            product = DeviceFirmwareManifestProduct(
                productKey = PRODUCT_KEY,
                productId = PRODUCT_ID,
                brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
                family = "dosing",
                line = "dose_pro",
                model = MODEL,
                displayName = "AquaLight Dose Pro 2",
                skuCode = "AQL-D-DP2-GLB-BLK",
                hardwareRevision = HARDWARE_REVISION,
                capabilities = CAPABILITIES,
                limits = LIMITS
            ),
            compatibility = DeviceFirmwareCompatibility(
                productKey = PRODUCT_KEY,
                productId = PRODUCT_ID,
                family = "dosing",
                line = "dose_pro",
                model = MODEL,
                hardwareRevision = HARDWARE_REVISION
            ),
            contracts = baseContracts(),
            features = setOf("DOSING_CONTROL", "OTA_UPDATE"),
            updatePolicy = DeviceFirmwareUpdatePolicy.RECOMMENDED,
            firmware = DeviceFirmwareAsset(
                version = TARGET_VERSION,
                filename = filename,
                url = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
                    "$RELEASE_TAG/$filename",
                sha256 = "a".repeat(64),
                size = 1_048_576,
                format = DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT,
                otaSlotCompatible = true
            ),
            factory = null
        )
    }

    private fun baseContracts() = DeviceFirmwareManifestContracts(
        wsSchema = "aql.ws.v1",
        wsProtocolVersion = 1,
        deviceApiVersion = 1,
        requiredDomains = setOf("aqualight.dosing.v1"),
        optionalDomains = emptySet()
    )

    private fun snapshot(): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(uid = DeviceUid(DEVICE_UID)),
        product = DeviceProduct(
            brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
            productId = PRODUCT_ID,
            productKey = PRODUCT_KEY,
            family = DeviceFamily.DOSING,
            familyRaw = "dosing",
            line = "dose_pro",
            model = MODEL,
            displayName = "Dose Pro 2",
            skuCode = "AQL-D-DP2-GLB-BLK",
            hardwareRevision = HARDWARE_REVISION
        ),
        firmwareVersion = CURRENT_VERSION,
        apiVersion = "1",
        protocolVersion = "1",
        capabilities = CAPABILITIES,
        limits = LIMITS,
        runtimeMetadataGeneration = 1L
    )

    private companion object {
        const val DEVICE_UID = "AQL-DP2-COMPAT-POLICY"
        const val PRODUCT_KEY = "DOSING_DOSE_PRO_2"
        const val PRODUCT_ID = "com.aqualight.dosing.dose_pro_2"
        const val MODEL = "dose_pro_2"
        const val HARDWARE_REVISION = "2.0"
        const val ENVIRONMENT = "dosing_dose_pro_2"
        const val CURRENT_VERSION = "1.0.0"
        const val TARGET_VERSION = "2.0.0"
        const val RELEASE_TAG = "dosing_dose_pro_2-v2.0.0"
        val CAPABILITIES = DeviceCapabilities(dosing = true, timeSync = true, ota = true)
        val LIMITS = DeviceLimits(dosingChannelCount = 2)
    }
}
