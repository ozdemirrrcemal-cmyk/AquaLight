package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceOtaFailureStage
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceOtaFailureStageTest {

    @Test
    fun `availability failures are explicitly marked before notification mapping`() = runTest {
        val coordinator = DeviceOtaCoordinator(
            snapshotProvider = { snapshot() },
            connectRuntime = { Result.success(Unit) },
            updaterProvider = { null },
            runtimeLifecycleEvents = null
        )

        val result = coordinator.checkAvailability(DEVICE_UID, MANIFEST_URL, applyNow = true)
        val state = coordinator.observe(DEVICE_UID).value as DeviceOtaState.Failed

        assertTrue(result.isFailure)
        assertEquals(DeviceOtaFailureStage.AVAILABILITY_CHECK, state.failure.stage)
        coordinator.close()
    }

    @Test
    fun `execution failure mappers retain execution stage by default`() {
        val failure = DeviceOtaFailureMapper.connection("runtime disconnected")

        assertEquals(DeviceOtaFailureStage.UPDATE_EXECUTION, failure.stage)
    }

    private fun snapshot(): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(uid = DEVICE_UID),
        product = DeviceProduct(
            brand = DeviceFirmwareRuntimeContract.Manifest.BRAND,
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
        capabilities = DeviceCapabilities(dosing = true, timeSync = true, ota = true),
        runtimeMetadataGeneration = 1L
    )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DP4-FAILURE-STAGE")
        const val MANIFEST_URL =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/" +
                "releases/download/stable-dosing_dose_pro_4/manifest-stable.json"
    }
}
