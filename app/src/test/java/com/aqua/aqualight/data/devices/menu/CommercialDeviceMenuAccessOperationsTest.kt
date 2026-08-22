package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuPresentationPreparationOperations
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialDeviceMenuAccessOperationsTest {

    @Test
    fun `validated catalog family replaces untrusted liveness family`() = runTest {
        val snapshot = product("LIGHT_RGB_PRO_SLIM").toSnapshot()
        val operations = operations(
            snapshot,
            DeviceMenuAccessResult.Available(
                deviceUid = snapshot.deviceUid.value,
                title = snapshot.title,
                family = OwnerDeviceFamily.DOSING
            )
        )

        val result = operations.resolve(snapshot.deviceUid.value)
            as DeviceMenuAccessResult.Available

        assertEquals(OwnerDeviceFamily.LIGHT, result.family)
        assertEquals(snapshot.deviceUid.value, result.deviceUid)
        assertTrue(result.presentationPrepared)
    }

    @Test
    fun `current generation is required even after successful liveness proof`() = runTest {
        val snapshot = product("LIGHT_RGB_PRO_SLIM").toSnapshot().copy(
            runtimeMetadataGeneration = 0L
        )
        val operations = operations(
            snapshot,
            DeviceMenuAccessResult.Available(
                deviceUid = snapshot.deviceUid.value,
                title = snapshot.title,
                family = OwnerDeviceFamily.LIGHT
            )
        )

        val result = operations.resolve(snapshot.deviceUid.value)
            as DeviceMenuAccessResult.Unavailable

        assertEquals(DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN, result.reason)
    }

    @Test
    fun `catalog mismatch blocks family routing after successful liveness proof`() = runTest {
        val product = product("TIMER_RELAY_PRO_2")
        val snapshot = product.toSnapshot().copy(
            capabilities = product.toSnapshot().capabilities.copy(dosing = true)
        )
        val operations = operations(
            snapshot,
            DeviceMenuAccessResult.Available(
                deviceUid = snapshot.deviceUid.value,
                title = snapshot.title,
                family = OwnerDeviceFamily.TIMER
            )
        )

        val result = operations.resolve(snapshot.deviceUid.value)
            as DeviceMenuAccessResult.Unavailable

        assertEquals(DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH, result.reason)
    }

    @Test
    fun `liveness rejection passes through without catalog access`() = runTest {
        var snapshotReads = 0
        val unavailable = DeviceMenuAccessResult.Unavailable(
            title = "Offline device",
            reason = DeviceMenuUnavailableReason.DEVICE_UNRESPONSIVE
        )
        val operations = CommercialDeviceMenuAccessOperations(
            livenessOperations = fixedLiveness(unavailable),
            currentSnapshot = {
                snapshotReads += 1
                null
            },
            presentationPreparationOperations = alwaysPrepared()
        )

        val result = operations.resolve("device-offline")

        assertTrue(result === unavailable)
        assertEquals(0, snapshotReads)
    }

    @Test
    fun `current presentation preparation failure blocks commercial routing`() = runTest {
        val snapshot = product("DOSING_DOSE_PRO_2").toSnapshot()
        val operations = CommercialDeviceMenuAccessOperations(
            livenessOperations = fixedLiveness(
                DeviceMenuAccessResult.Available(
                    deviceUid = snapshot.deviceUid.value,
                    title = snapshot.title,
                    family = OwnerDeviceFamily.DOSING
                )
            ),
            currentSnapshot = { snapshot },
            presentationPreparationOperations = DeviceMenuPresentationPreparationOperations {
                false
            }
        )

        val result = operations.resolve(snapshot.deviceUid.value)
            as DeviceMenuAccessResult.Unavailable

        assertEquals(DeviceMenuUnavailableReason.CURRENT_DATA_NOT_READY, result.reason)
    }

    private fun operations(
        snapshot: DeviceSnapshot,
        liveness: DeviceMenuAccessResult
    ) = CommercialDeviceMenuAccessOperations(
        livenessOperations = fixedLiveness(liveness),
        currentSnapshot = { snapshot },
        presentationPreparationOperations = alwaysPrepared()
    )

    private fun alwaysPrepared() = DeviceMenuPresentationPreparationOperations { true }

    private fun fixedLiveness(result: DeviceMenuAccessResult): DeviceMenuAccessOperations =
        object : DeviceMenuAccessOperations {
            override suspend fun resolve(deviceUid: String): DeviceMenuAccessResult = result
        }

    private fun product(productKey: String): AqlCommercialCatalogProduct =
        AqlCommercialDeviceCatalog.products.single { it.productKey.value == productKey }

    private fun AqlCommercialCatalogProduct.toSnapshot(): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(
            uid = DeviceUid("menu-${model.value}"),
            customName = "Fixture $displayName"
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
        runtimeMetadataGeneration = 1L
    )
}
