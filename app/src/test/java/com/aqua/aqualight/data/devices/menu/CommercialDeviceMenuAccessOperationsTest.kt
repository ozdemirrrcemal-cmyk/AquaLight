package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DefaultDeviceAccessPolicy
import com.aqua.aqualight.application.devices.DeviceCompatibilityOperations
import com.aqua.aqualight.application.devices.DeviceCompatibilitySnapshot
import com.aqua.aqualight.application.devices.DeviceCompatibilityStatus
import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialDeviceMenuAccessOperationsTest {

    @Test
    fun `compatible runtime replaces untrusted liveness family`() = runTest {
        val operations = operations(
            liveness = available(OwnerDeviceFamily.DOSING),
            compatibility = DeviceCompatibilitySnapshot(
                deviceUid = DEVICE_UID,
                family = OwnerDeviceFamily.LIGHT,
                status = DeviceCompatibilityStatus.COMPATIBLE
            )
        )

        val result = operations.resolve(DEVICE_UID) as DeviceMenuAccessResult.Available

        assertEquals(OwnerDeviceFamily.LIGHT, result.family)
        assertEquals(DEVICE_UID, result.deviceUid)
    }

    @Test
    fun `validated liveness plus malformed metadata is not reported offline`() = runTest {
        val operations = operations(
            liveness = available(OwnerDeviceFamily.LIGHT),
            compatibility = DeviceCompatibilitySnapshot(
                deviceUid = DEVICE_UID,
                family = OwnerDeviceFamily.LIGHT,
                status = DeviceCompatibilityStatus.RUNTIME_METADATA_UNAVAILABLE
            )
        )

        val result = operations.resolve(DEVICE_UID) as DeviceMenuAccessResult.Unavailable

        assertEquals(DeviceMenuUnavailableReason.MALFORMED_DEVICE_STATE, result.reason)
    }

    @Test
    fun `newer application contract is classified as app update required`() = runTest {
        val operations = operations(
            liveness = available(OwnerDeviceFamily.LIGHT),
            compatibility = DeviceCompatibilitySnapshot(
                deviceUid = DEVICE_UID,
                family = OwnerDeviceFamily.LIGHT,
                status = DeviceCompatibilityStatus.APPLICATION_UPDATE_REQUIRED
            )
        )

        val result = operations.resolve(DEVICE_UID) as DeviceMenuAccessResult.Unavailable

        assertEquals(DeviceMenuUnavailableReason.APPLICATION_UPDATE_REQUIRED, result.reason)
    }

    @Test
    fun `liveness rejection passes through without compatibility evaluation`() = runTest {
        var compatibilityReads = 0
        val unavailable = DeviceMenuAccessResult.Unavailable(
            title = "Offline device",
            reason = DeviceMenuUnavailableReason.DEVICE_OFFLINE
        )
        val operations = CommercialDeviceMenuAccessOperations(
            livenessOperations = fixedLiveness(unavailable),
            compatibilityOperations = object : DeviceCompatibilityOperations {
                override fun current(deviceUid: String): DeviceCompatibilitySnapshot {
                    compatibilityReads += 1
                    error("Compatibility must not run when liveness is unavailable.")
                }
            },
            accessPolicy = DefaultDeviceAccessPolicy
        )

        val result = operations.resolve(DEVICE_UID)

        assertTrue(result === unavailable)
        assertEquals(0, compatibilityReads)
    }

    private fun operations(
        liveness: DeviceMenuAccessResult,
        compatibility: DeviceCompatibilitySnapshot
    ) = CommercialDeviceMenuAccessOperations(
        livenessOperations = fixedLiveness(liveness),
        compatibilityOperations = object : DeviceCompatibilityOperations {
            override fun current(deviceUid: String): DeviceCompatibilitySnapshot = compatibility
        },
        accessPolicy = DefaultDeviceAccessPolicy
    )

    private fun available(family: OwnerDeviceFamily) = DeviceMenuAccessResult.Available(
        deviceUid = DEVICE_UID,
        title = "Commercial device",
        family = family
    )

    private fun fixedLiveness(result: DeviceMenuAccessResult): DeviceMenuAccessOperations =
        object : DeviceMenuAccessOperations {
            override suspend fun resolve(deviceUid: String): DeviceMenuAccessResult = result
        }

    private companion object {
        const val DEVICE_UID = "AQL-COMPATIBILITY-TEST"
    }
}
