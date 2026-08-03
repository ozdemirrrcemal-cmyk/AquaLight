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

class DeviceFirmwareExactArtifactPlannerTest {

    private val planner = DeviceFirmwareUpdatePlanner {
        listOf("tr-TR", "tr", "en")
    }

    @Test
    fun `exact firmware manifest produces plan without requiring a custom device name`() {
        val snapshot = snapshot(customName = "")
        val availability = planner.evaluateUpdate(snapshot, manifest()).getOrThrow()
            as DeviceFirmwareAvailability.UpdateAvailable
        val plan = availability.plan

        assertEquals("dosing_dose_pro_2", plan.env)
        assertEquals("dose_pro_2", plan.model)
        assertEquals("dose_pro_2", plan.payload.model)
        assertEquals("2.0.0", plan.payload.version)
        assertEquals(7L, plan.runtimeMetadataGeneration)
        assertEquals("v2.0.0", plan.manifestTag)
        assertEquals("tr", plan.releaseContent.localeTag)
        assertEquals(listOf("Kalibrasyon doğrulaması geliştirildi."), plan.releaseContent.changes)
        assertEquals("Dose Pro 2", plan.displayName)
    }

    @Test
    fun `owner custom name cannot change OTA artifact identity or payload`() {
        val unnamedPlan = planner.planUpdate(snapshot(customName = ""), manifest()).getOrThrow()
        val namedPlan = planner.planUpdate(
            snapshot(customName = "Salon Dozaj Sistemi"),
            manifest()
        ).getOrThrow()

        assertEquals(unnamedPlan.env, namedPlan.env)
        assertEquals(unnamedPlan.firmware, namedPlan.firmware)
        assertEquals(unnamedPlan.payload, namedPlan.payload)
        assertEquals("Dose Pro 2", unnamedPlan.displayName)
        assertEquals("Salon Dozaj Sistemi", namedPlan.displayName)
    }

    @Test
    fun `duplicate exact artifacts fail closed instead of selecting first`() {
        val exact = artifact()
        val failure = planner.evaluateUpdate(
            snapshot(),
            manifest(artifacts = listOf(exact, exact.copy()))
        ).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("Ambiguous OTA manifest"))
    }

    @Test
    fun `zero compatible artifacts fail closed`() {
        val exact = artifact()
        val other = exact.copy(
            compatibility = exact.compatibility.copy(model = "dose_pro_4")
        )

        val failure = planner.evaluateUpdate(
            snapshot(),
            manifest(artifacts = listOf(other))
        ).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("No compatible OTA artifact"))
    }

    @Test
    fun `matching identity with wrong environment is rejected`() {
        val wrongEnv = artifact().copy(env = "dosing_dose_pro_4")

        val failure = planner.evaluateUpdate(
            snapshot(),
            manifest(artifacts = listOf(wrongEnv))
        ).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("No compatible OTA artifact"))
    }

    @Test
    fun `manifest capability drift from authenticated firmware fails closed`() {
        val exact = artifact()
        val drifted = exact.copy(
            product = exact.product.copy(
                capabilities = exact.product.capabilities.copy(dosing = false)
            )
        )

        val failure = planner.evaluateUpdate(
            snapshot(),
            manifest(artifacts = listOf(drifted))
        ).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("capabilities differ"))
    }

    @Test
    fun `same version resolves up to date while preserving exact release items`() {
        val availability = planner.evaluateUpdate(
            snapshot().copy(firmwareVersion = "2.0.0"),
            manifest()
        ).getOrThrow() as DeviceFirmwareAvailability.UpToDate

        assertEquals("2.0.0", availability.currentVersion)
        assertEquals(
            listOf("Kalibrasyon doğrulaması geliştirildi."),
            availability.releaseContent.changes
        )
    }

    private fun snapshot(customName: String = ""): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(
            uid = DEVICE_UID,
            customName = customName
        ),
        product = DeviceProduct(
            brand = "AquaLight",
            productId = PRODUCT_ID,
            productKey = PRODUCT_KEY,
            family = DeviceFamily.DOSING,
            familyRaw = "dosing",
            line = "dose_pro",
            model = "dose_pro_2",
            displayName = "Dose Pro 2",
            skuCode = "AQL-D-DP2-GLB-BLK",
            hardwareRevision = "2.0"
        ),
        firmwareVersion = "1.0.0",
        apiVersion = "1",
        protocolVersion = "1",
        capabilities = DOSING_CAPABILITIES,
        limits = DOSING_LIMITS,
        runtimeMetadataGeneration = 7L
    )

    private fun manifest(
        artifacts: List<DeviceFirmwareManifestArtifact> = listOf(artifact())
    ) = DeviceFirmwareManifest(
        schema = DeviceFirmwareRuntimeContract.Manifest.SCHEMA,
        brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
        channel = DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL,
        version = "2.0.0",
        tag = "v2.0.0",
        releaseRepo = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY,
        generatedAt = "2026-08-03T00:00:00+00:00",
        platform = OFFICIAL_PLATFORM,
        releaseNotes = DeviceFirmwareReleaseNotes(
            schema = DeviceFirmwareRuntimeContract.ReleaseNotes.SCHEMA,
            defaultLocale = DeviceFirmwareRuntimeContract.ReleaseNotes.DEFAULT_LOCALE,
            items = listOf(
                DeviceFirmwareReleaseNoteItem(
                    tr = "Kalibrasyon doğrulaması geliştirildi.",
                    en = "Calibration validation improved."
                )
            )
        ),
        artifacts = artifacts,
        signature = DeviceFirmwareManifestSignature(
            scheme = DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256,
            keyId = "release-key-1",
            payloadHash = "b".repeat(64),
            value = "signed-value"
        )
    )

    private fun artifact(): DeviceFirmwareManifestArtifact {
        val env = "dosing_dose_pro_2"
        val filename = "AquaLight-$env-v2.0.0-ota.bin"
        return DeviceFirmwareManifestArtifact(
            env = env,
            product = DeviceFirmwareManifestProduct(
                productKey = PRODUCT_KEY,
                productId = PRODUCT_ID,
                brand = "AquaLight",
                family = "dosing",
                line = "dose_pro",
                model = "dose_pro_2",
                displayName = "AquaLight Dose Pro 2",
                skuCode = "AQL-D-DP2-GLB-BLK",
                hardwareRevision = "2.0",
                capabilities = DOSING_CAPABILITIES,
                limits = DOSING_LIMITS
            ),
            compatibility = DeviceFirmwareCompatibility(
                productKey = PRODUCT_KEY,
                productId = PRODUCT_ID,
                family = "dosing",
                line = "dose_pro",
                model = "dose_pro_2",
                hardwareRevision = "2.0"
            ),
            firmware = DeviceFirmwareAsset(
                version = "2.0.0",
                filename = filename,
                url = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
                    "v2.0.0/$filename",
                sha256 = "a".repeat(64),
                size = 1_048_576,
                format = DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT,
                otaSlotCompatible = true
            ),
            factory = null
        )
    }

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DP2-OTA-TEST")
        const val PRODUCT_KEY = "DOSING_DOSE_PRO_2"
        const val PRODUCT_ID = "com.aqualight.dosing.dose_pro_2"
        val DOSING_CAPABILITIES = DeviceCapabilities(
            dosing = true,
            timeSync = true,
            ota = true
        )
        val DOSING_LIMITS = DeviceLimits(dosingChannelCount = 2)
        val OFFICIAL_PLATFORM = DeviceFirmwareManifestPlatform(
            framework = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_FRAMEWORK,
            core = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_CORE,
            platform = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_PACKAGE,
            partitionTable = DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE,
            normalOtaAssetType = DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE
        )
    }
}
