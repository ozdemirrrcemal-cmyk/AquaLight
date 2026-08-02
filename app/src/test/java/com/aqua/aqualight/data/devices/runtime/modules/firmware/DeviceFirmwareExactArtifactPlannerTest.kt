package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareExactArtifactPlannerTest {

    private val planner = DeviceFirmwareUpdatePlanner {
        listOf("tr-TR", "tr", "en")
    }

    @Test
    fun `exactly one artifact produces model-complete plan and localized signed content`() {
        val snapshot = product("DOSING_DOSE_PRO_2").toSnapshot()
        val availability = planner.evaluateUpdate(snapshot, manifest()).getOrThrow()
            as DeviceFirmwareAvailability.UpdateAvailable
        val plan = availability.plan

        assertEquals("dosing_dose_pro_2", plan.env)
        assertEquals("dose_pro_2", plan.model)
        assertEquals("dose_pro_2", plan.payload.model)
        assertEquals(7L, plan.runtimeMetadataGeneration)
        assertEquals("v2.0.0", plan.manifestTag)
        assertEquals("tr", plan.releaseContent.localeTag)
        assertEquals("", plan.releaseContent.title)
        assertEquals(listOf("Kalibrasyon doğrulaması geliştirildi."), plan.releaseContent.changes)
        assertEquals("dose_pro_2", plan.payload.toJson().getString("model"))
    }

    @Test
    fun `duplicate exact artifacts fail closed instead of selecting first`() {
        val snapshot = product("DOSING_DOSE_PRO_2").toSnapshot()
        val exact = artifact()
        val duplicate = manifest(artifacts = listOf(exact, exact.copy()))

        val failure = planner.evaluateUpdate(snapshot, duplicate).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("Ambiguous OTA manifest"))
    }

    @Test
    fun `zero compatible artifacts mean no update is published for this product`() {
        val snapshot = product("DOSING_DOSE_PRO_2").toSnapshot()
        val other = artifact("DOSING_DOSE_PRO_4")

        val availability = planner.evaluateUpdate(
            snapshot,
            manifest(artifacts = listOf(other))
        ).getOrThrow()

        assertEquals(
            DeviceFirmwareAvailability.NoUpdateAvailable("1.0.0"),
            availability
        )
    }

    @Test
    fun `matching identity with wrong environment is rejected`() {
        val snapshot = product("DOSING_DOSE_PRO_2").toSnapshot()
        val wrongEnv = artifact().copy(env = "dosing_dose_pro_4")

        val availability = planner.evaluateUpdate(
            snapshot,
            manifest(artifacts = listOf(wrongEnv))
        ).getOrThrow()

        assertTrue(availability is DeviceFirmwareAvailability.NoUpdateAvailable)
    }

    @Test
    fun `same version resolves up to date while preserving release content`() {
        val snapshot = product("DOSING_DOSE_PRO_2").toSnapshot().copy(
            firmwareVersion = "2.0.0"
        )
        val availability = planner.evaluateUpdate(snapshot, manifest()).getOrThrow()
            as DeviceFirmwareAvailability.UpToDate

        assertEquals("2.0.0", availability.currentVersion)
        assertEquals(
            listOf("Kalibrasyon doğrulaması geliştirildi."),
            availability.releaseContent.changes
        )
    }

    private fun product(productKey: String): AqlCommercialCatalogProduct =
        AqlCommercialDeviceCatalog.products.single { product ->
            product.productKey.value == productKey
        }

    private fun AqlCommercialCatalogProduct.toSnapshot(): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(
            uid = DEVICE_UID,
            customName = "Salon Dozaj"
        ),
        product = DeviceProduct(
            brand = "AquaLight",
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
        firmwareVersion = "1.0.0",
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
        releaseRepo = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_REPOSITORY,
        generatedAt = "2026-07-30T00:00:00Z",
        artifacts = artifacts,
        signature = DeviceFirmwareManifestSignature(
            scheme = DeviceFirmwareRuntimeContract.Signature.SCHEME_ECDSA_P256_SHA256,
            keyId = "release-key-1",
            payloadHash = "b".repeat(64),
            value = "signed-value"
        )
    )

    private fun artifact(
        productKey: String = "DOSING_DOSE_PRO_2"
    ): DeviceFirmwareManifestArtifact {
        val product = product(productKey)
        val env = product.productKey.value.lowercase(Locale.ROOT)
        val filename = "AquaLight-$env-v2.0.0-ota.bin"
        return DeviceFirmwareManifestArtifact(
            env = env,
            product = product.toManifestProduct(),
            compatibility = DeviceFirmwareCompatibility(
                productKey = product.productKey.value,
                productId = product.productId.value,
                family = product.family.wireValue,
                line = product.line.value,
                model = product.model.value,
                hardwareRevision = product.hardwareRevision.value
            ),
            platform = DeviceFirmwarePlatform(
                framework = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_FRAMEWORK,
                core = "3.3.9",
                platform = "pioarduino/platform-espressif32#55.03.39",
                partitionTable = DeviceFirmwareRuntimeContract.Manifest.PLATFORM_PARTITION_TABLE,
                normalOtaAssetType =
                    DeviceFirmwareRuntimeContract.Manifest.PLATFORM_OTA_ASSET_TYPE
            ),
            release = DeviceFirmwareRelease(
                version = "2.0.0",
                tag = "v2.0.0",
                generatedAt = "2026-07-30T00:00:00Z",
                releaseNotes = DeviceFirmwareReleaseNotes(
                    defaultLocale = "tr",
                    mandatory = false,
                    locales = linkedMapOf(
                        "tr" to DeviceFirmwareLocalizedReleaseNotes(
                            title = "",
                            summary = "",
                            changes = listOf("Kalibrasyon doğrulaması geliştirildi."),
                            warnings = emptyList()
                        ),
                        "en" to DeviceFirmwareLocalizedReleaseNotes(
                            title = "",
                            summary = "",
                            changes = listOf("Calibration validation improved."),
                            warnings = emptyList()
                        )
                    )
                )
            ),
            firmware = DeviceFirmwareAsset(
                filename = filename,
                url = DeviceFirmwareRuntimeContract.OFFICIAL_RELEASE_URL_PREFIX +
                    "v2.0.0/$filename",
                sha256 = "a".repeat(64),
                size = 1_048_576,
                format = DeviceFirmwareRuntimeContract.Manifest.FIRMWARE_FORMAT,
                otaSlotCompatible = true
            )
        )
    }

    private fun AqlCommercialCatalogProduct.toManifestProduct() =
        DeviceFirmwareManifestProduct(
            productKey = productKey.value,
            productId = productId.value,
            brand = "AquaLight",
            family = family.wireValue,
            line = line.value,
            model = model.value,
            displayName = displayName,
            skuCode = skuCode.value,
            hardwareRevision = hardwareRevision.value,
            capabilities = profile.capabilities,
            limits = limits
        )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DP2-OTA-TEST")
    }
}
