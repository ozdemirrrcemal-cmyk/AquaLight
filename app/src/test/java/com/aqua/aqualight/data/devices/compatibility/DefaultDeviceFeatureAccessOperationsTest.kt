package com.aqua.aqualight.data.devices.compatibility

import com.aqua.aqualight.application.devices.DefaultDeviceAccessPolicy
import com.aqua.aqualight.application.devices.DeviceAccessDecision
import com.aqua.aqualight.application.devices.DeviceCompatibilityOperations
import com.aqua.aqualight.application.devices.DeviceCompatibilitySnapshot
import com.aqua.aqualight.application.devices.DeviceCompatibilityStatus
import com.aqua.aqualight.application.devices.DeviceFirmwareCommandResult
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceOtaFailure
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.application.devices.DeviceOtaFailureStage
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDeviceFeatureAccessOperationsTest {

    @Test
    fun `missing optional feature refreshes signed OTA once then becomes firmware required`() =
        runTest {
            var updateKnown = false
            val compatibility = object : DeviceCompatibilityOperations {
                override fun current(deviceUid: String): DeviceCompatibilitySnapshot =
                    DeviceCompatibilitySnapshot(
                        deviceUid = deviceUid,
                        family = OwnerDeviceFamily.LIGHT,
                        status = DeviceCompatibilityStatus.COMPATIBLE,
                        menuFeatures = setOf(DeviceRootMenuFeature.LIGHT_MANUAL),
                        firmwareUpdateRequiredFeatures = if (updateKnown) {
                            setOf(DeviceRootMenuFeature.LIGHT_QUICK_SETUP)
                        } else {
                            emptySet()
                        }
                    )
            }
            val firmware = FakeFirmwareOperations(
                onRefresh = {
                    updateKnown = true
                    Result.success(DeviceOtaState.Idle(DEVICE_UID))
                }
            )
            val operations = DefaultDeviceFeatureAccessOperations(
                compatibilityOperations = compatibility,
                accessPolicy = DefaultDeviceAccessPolicy,
                firmwareUpdateOperations = firmware
            )

            val result = operations.resolve(
                DEVICE_UID,
                DeviceRootMenuFeature.LIGHT_QUICK_SETUP
            )

            assertEquals(1, firmware.refreshCalls)
            assertEquals(
                DeviceMenuUnavailableReason.FIRMWARE_UPDATE_REQUIRED,
                (result as DeviceAccessDecision.Blocked).reason
            )
        }

    @Test
    fun `available feature does not perform an OTA lookup`() = runTest {
        val compatibility = fixedCompatibility(
            menuFeatures = setOf(
                DeviceRootMenuFeature.LIGHT_MANUAL,
                DeviceRootMenuFeature.LIGHT_QUICK_SETUP
            )
        )
        val firmware = FakeFirmwareOperations {
            error("OTA refresh must not run for an already available feature.")
        }
        val operations = DefaultDeviceFeatureAccessOperations(
            compatibilityOperations = compatibility,
            accessPolicy = DefaultDeviceAccessPolicy,
            firmwareUpdateOperations = firmware
        )

        val result = operations.resolve(
            DEVICE_UID,
            DeviceRootMenuFeature.LIGHT_QUICK_SETUP
        )

        assertTrue(result is DeviceAccessDecision.Allowed)
        assertEquals(0, firmware.refreshCalls)
    }

    @Test
    fun `signed target requiring newer app is reported as app update required`() = runTest {
        val compatibility = fixedCompatibility(
            menuFeatures = setOf(DeviceRootMenuFeature.LIGHT_MANUAL)
        )
        val appUpdateFailure = DeviceOtaState.Failed(
            deviceUid = DEVICE_UID,
            failure = DeviceOtaFailure(
                reason = DeviceOtaFailureReason.APPLICATION_UPDATE_REQUIRED,
                recoverable = false,
                stage = DeviceOtaFailureStage.AVAILABILITY_CHECK
            )
        )
        val firmware = FakeFirmwareOperations(
            initialState = appUpdateFailure,
            onRefresh = { Result.failure(IllegalStateException("client contract too old")) }
        )
        val operations = DefaultDeviceFeatureAccessOperations(
            compatibilityOperations = compatibility,
            accessPolicy = DefaultDeviceAccessPolicy,
            firmwareUpdateOperations = firmware
        )

        val result = operations.resolve(
            DEVICE_UID,
            DeviceRootMenuFeature.LIGHT_QUICK_SETUP
        )

        assertEquals(
            DeviceMenuUnavailableReason.APPLICATION_UPDATE_REQUIRED,
            (result as DeviceAccessDecision.Blocked).reason
        )
    }

    @Test
    fun `root incompatibility never triggers feature OTA discovery`() = runTest {
        val compatibility = object : DeviceCompatibilityOperations {
            override fun current(deviceUid: String) = DeviceCompatibilitySnapshot(
                deviceUid = deviceUid,
                family = OwnerDeviceFamily.LIGHT,
                status = DeviceCompatibilityStatus.BASE_CONTRACT_INCOMPATIBLE
            )
        }
        val firmware = FakeFirmwareOperations {
            error("OTA refresh must not run when the base contract is already incompatible.")
        }
        val operations = DefaultDeviceFeatureAccessOperations(
            compatibilityOperations = compatibility,
            accessPolicy = DefaultDeviceAccessPolicy,
            firmwareUpdateOperations = firmware
        )

        val result = operations.resolve(
            DEVICE_UID,
            DeviceRootMenuFeature.LIGHT_QUICK_SETUP
        )

        assertEquals(0, firmware.refreshCalls)
        assertEquals(
            DeviceMenuUnavailableReason.CONTRACT_INCOMPATIBLE,
            (result as DeviceAccessDecision.Blocked).reason
        )
    }

    private fun fixedCompatibility(
        menuFeatures: Set<DeviceRootMenuFeature>
    ): DeviceCompatibilityOperations = object : DeviceCompatibilityOperations {
        override fun current(deviceUid: String) = DeviceCompatibilitySnapshot(
            deviceUid = deviceUid,
            family = OwnerDeviceFamily.LIGHT,
            status = DeviceCompatibilityStatus.COMPATIBLE,
            menuFeatures = menuFeatures
        )
    }

    private class FakeFirmwareOperations(
        initialState: DeviceOtaState = DeviceOtaState.Idle(DEVICE_UID),
        private val onRefresh: suspend () -> Result<DeviceOtaState>
    ) : DeviceFirmwareUpdateOperations {
        private val state = MutableStateFlow(initialState)
        var refreshCalls: Int = 0
            private set

        override fun observe(deviceUid: String): StateFlow<DeviceOtaState> = state

        override suspend fun refreshAvailabilityIfStale(
            deviceUid: String,
            manifestUrl: String,
            applyNow: Boolean
        ): Result<DeviceOtaState> {
            refreshCalls += 1
            return onRefresh().also { result ->
                result.getOrNull()?.let { next -> state.value = next }
            }
        }

        override suspend fun prepareUpdate(
            deviceUid: String,
            manifestUrl: String,
            applyNow: Boolean
        ): Result<PreparedDeviceFirmwareUpdate> =
            Result.failure(UnsupportedOperationException("not used"))

        override suspend fun startUpdate(
            plan: PreparedDeviceFirmwareUpdate
        ): DeviceFirmwareCommandResult = DeviceFirmwareCommandResult(sent = false)

        override suspend fun requestStatus(deviceUid: String): DeviceFirmwareCommandResult =
            DeviceFirmwareCommandResult(sent = false)

        override suspend fun clearStatus(deviceUid: String): DeviceFirmwareCommandResult =
            DeviceFirmwareCommandResult(sent = false)
    }

    private companion object {
        const val DEVICE_UID = "AQL-LIGHT-FEATURE-GATE"
    }
}
