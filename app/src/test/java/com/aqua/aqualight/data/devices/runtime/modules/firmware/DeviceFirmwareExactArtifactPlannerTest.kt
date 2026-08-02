package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
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
    fun `exact release artifact produces model-complete plan and localized content`() {
        val snapshot = product(PRODUCT_KEY).toSnapshot()
        val availability = planner.evaluateUpdate(snapshot, manifest()).getOrThrow()
            as DeviceFirmwareAvailability.UpdateAvailable
        val plan = availability.plan

        assertEquals(ENVIRONMENT, plan.env)
        assertEquals("dose_pro_2", plan.model)
        assertEquals("dose_pro_2", plan.payload.model)
        assertEquals(7L, plan.runtimeMetadataGeneration)
        assertEquals(RELEASE_TAG, plan.manifestTag)
        assertEquals("tr", plan.releaseContent.localeTag)
        assertEquals(
            listOf("Kalibrasyon doğrulaması geliştirildi."),
            plan.releaseContent.changes
        )
        assertEquals("dose_pro_2", plan.payload.toJson().getString("model"))
    }

    @Test
    fun `user defined device name is not part of OTA selection or payload`() {
        val product = product(PRODUCT_KEY)
        val unnamed = planner.evaluateUpdate(
            product.toSnapshot(customName = ""),
            manifest()
        ).getOrThrow() as DeviceFirmwareAvailability.UpdateAvailable
        val renamed = planner.evaluateUpdate(
            product.toSnapshot(customName = "Salon Dozaj"),
            manifest()
        ).getOrThrow() as DeviceFirmwareAvailability.UpdateAvailable

        assertEquals(unnamed.plan, renamed.plan)
        assertTrue(!unnamed.plan.payload.toJson().has("deviceName"))
    }

    @Test
    fun `duplicate exact artifacts fail closed instead of selecting first`() {
        val snapshot = product(PRODUCT_KEY).toSnapshot()
        val exact = artifact()
        val duplicate = manifest(artifacts = listOf(exact, exact.copy()))

        val failure = planner.evaluateUpdate(snapshot, duplicate).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("Ambiguous OTA manifest"))
    }

    @Test
    fun `zero compatible artifacts fail closed`() {
        val snapshot = product(PRODUCT_KEY).toSnapshot()
        val other = artifact().copy(
            compatibility = artifact().compatibility.copy(model = "dose_pro_4")
        )

        val failure = planner.evaluateUpdate(
            snapshot,
            manifest(artifacts = listOf(other))
        ).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("No compatible OTA artifact"))
    }

    @Test
    fun `matching identity with wrong environment is rejected`() {
        val snapshot = product(PRODUCT_KEY).toSnapshot()
        val wrongEnv = artifact().copy(env = "dosing_dose_pro_4")

        val failure = planner.evaluateUpdate(
            snapshot,
            manifest(artifacts = listOf(wrongEnv))
        ).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("No compatible OTA artifact"))
    }

    @Test
    fun `same version resolves up to date while preserving release items`() {
        val snapshot = product(PRODUCT_KEY).toSnapshot().copy(
            firmwareVersion = TARGET_VERSION
        )
        val availability = planner.evaluateUpdate(snapshot, manifest()).getOrThrow()
            as DeviceFirmwareAvailability.UpToDate

        assertEquals(TARGET_VERSION, availability.currentVersion)
        assertEquals(
            listOf("Kalibrasyon doğrulaması geliştirildi."),
            availability.releaseContent.changes
        )
    }

    private fun product(productKey: String): AqlCommercialCatalogProduct =
        AqlCommercialDeviceCatalog.products.single { product ->
            product.productKey.value == productKey
        }

    private fun AqlCommercialCatalogProduct.toSnapshot(
        customName: String = "Salon Dozaj"
    ): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(
            uid = DEVICE_UID,
            customName = customName
        ),
        product = DeviceProduct(
            brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
            productId = productId.value,
            productKey = productKey.value,
            family = family,
            familyRaw = family.wireValue,
            line = line.value,
            model = model.value,
            displayName = displayName,
            skuId = skuId.value,
            skuCode = skuCode.value,
            hardwareRevision = hardwareRevision.value
        ),
        firmwareVersion = CURRENT_VERSION,
        apiVersion = "1",
        protocolVersion = "1",
        capabilities = DeviceCapabilities(
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
        ),
        limits = DeviceLimits(
            lightChannelCount = limits.lightChannelCount,
            fanOutputCount = limits.fanOutputCount,
            temperatureSensorCount = limits.temperatureSensorCount,
            timerChannelCount = limits.timerChannelCount,
            dosingChannelCount = limits.dosingChannelCount
        ),
        supportedFeatures = profile.supportedFeatures.map { it.wireValue },
        supportedScreens = profile.supportedScreens.map { it.wireValue },
        runtimeMetadataGeneration = 7L
    )

    private fun manifest(
        artifacts: List<DeviceFirmwareManifestArtifact> = listOf(artifact())
    ) = DeviceFirmwareManifest(
        schema = DeviceFirmwareRuntimeContract.Manifest.SCHEMA,
        brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
        channel = DeviceFirmwareRuntimeContract.Manifest.STABLE_CHANNEL,
        version = TARGET_VERSION,
        tag = RELEASE_TAG,
        releaseRepo = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY,
        generatedAt = GENERATED_AT,
        platform = manifestPlatform(),
        artifacts = artifacts,
        signature = manifestSignature(),
        releaseNotes = DeviceFirmwareReleaseNotes(
            schema = DeviceFirmwareRuntimeContract.Manifest.RELEASE_NOTES_SCHEMA,
            defaultLocale = "tr",
            items = listOf(
                DeviceFirmwareReleaseNoteItem(
                    tr = "Kalibrasyon doğrulaması geliştirildi.",
                    en = "Calibration validation improved."
                )
            )
        )
    )

    private fun artifact(): DeviceFirmwareManifestArtifact {
        val product = product(PRODUCT_KEY)
        val otaFilename = "AquaLight-$ENVIRONMENT-$RELEASE_TAG-ota.bin"
        val factoryFilename = "AquaLight-$ENVIRONMENT-$RELEASE_TAG-factory.zip"
        val releaseUrl = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
            "$RELEASE_TAG/"
        return DeviceFirmwareManifestArtifact(
            env = ENVIRONMENT,
            product = product.toManifestProduct(),
            compatibility = DeviceFirmwareCompatibility(
                productKey = product.productKey.value,
                productId = product.productId.value,
                family = product.family.wireValue,
                line = product.line.value,
                model = product.model.value,
                hardwareRevision = product.hardwareRevision.value
            ),
            firmware = DeviceFirmwareAsset(
                filename = otaFilename,
                url = releaseUrl + otaFilename,
                sha256 = "a".repeat(64),
                size = FIRMWARE_SIZE,
                format = DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT,
                otaSlotCompatible = true
            ),
            factory = DeviceFirmwareFactoryAsset(
                filename = factoryFilename,
                url = releaseUrl + factoryFilename,
                sha256 = "c".repeat(64),
                size = FACTORY_SIZE
            )
        )
    }

    private fun AqlCommercialCatalogProduct.toManifestProduct() =
        DeviceFirmwareManifestProduct(
            productKey = productKey.value,
            productId = productId.value,
            brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
            family = family.wireValue,
            line = line.value,
            model = model.value,
            displayName = displayName,
            skuCode = skuCode.value,
            hardwareRevision = hardwareRevision.value,
            capabilities = DeviceFirmwareManifestCapabilities(
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
            ),
            limits = DeviceFirmwareManifestLimits(
                lightChannelCount = limits.lightChannelCount,
                fanOutputCount = limits.fanOutputCount,
                temperatureSensorCount = limits.temperatureSensorCount,
                timerChannelCount = limits.timerChannelCount,
                dosingChannelCount = limits.dosingChannelCount
            )
        )

    private fun manifestPlatform() = DeviceFirmwareManifestPlatform(
        framework = "arduino-esp32",
        core = "3.3.9",
        platform = "pioarduino/platform-espressif32#55.03.39",
        partitionTable = DeviceFirmwareRuntimeContract.Manifest.PARTITION_TABLE,
        normalOtaAssetType = DeviceFirmwareRuntimeContract.Manifest.NORMAL_OTA_ASSET_TYPE
    )

    private fun manifestSignature() = DeviceFirmwareManifestSignature(
        scheme = DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256,
        keyId = "release-key-1",
        payloadHash = "b".repeat(64),
        value = "signed-value"
    )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DP2-OTA-TEST")
        const val PRODUCT_KEY = "DOSING_DOSE_PRO_2"
        const val ENVIRONMENT = "dosing_dose_pro_2"
        const val CURRENT_VERSION = "1.0.0"
        const val TARGET_VERSION = "2.0.0"
        const val RELEASE_TAG = "v2.0.0"
        const val GENERATED_AT = "2026-07-30T00:00:00Z"
        const val FIRMWARE_SIZE = 1_048_576
        const val FACTORY_SIZE = 2_097_152
    }
}
