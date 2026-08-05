package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceOtaFailure
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.concurrent.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DeviceFirmwareAvailabilityRefreshPolicyTest {

    @Test
    fun `successful passive refresh is fresh once per device within the application window`() {
        var nowMillis = 10_000L
        val policy = DeviceFirmwareAvailabilityRefreshPolicy(
            nowMillis = { nowMillis },
            freshnessMillis = 1_000L,
            failureRetryMillis = 100L
        )
        val deviceUid = DeviceUid("device-one")
        val idle = DeviceOtaState.Idle(deviceUid.value)

        assertTrue(policy.shouldRefresh(deviceUid, idle))

        policy.recordResult(deviceUid, Result.success(idle))
        assertFalse(policy.shouldRefresh(deviceUid, idle))

        nowMillis += 999L
        assertFalse(policy.shouldRefresh(deviceUid, idle))

        nowMillis += 1L
        assertTrue(policy.shouldRefresh(deviceUid, idle))
    }

    @Test
    fun `failed passive refresh uses the short retry window`() {
        var nowMillis = 15_000L
        val policy = DeviceFirmwareAvailabilityRefreshPolicy(
            nowMillis = { nowMillis },
            freshnessMillis = 1_000L,
            failureRetryMillis = 100L
        )
        val deviceUid = DeviceUid("device-one")
        val recoverableFailure = DeviceOtaState.Failed(
            deviceUid = deviceUid.value,
            failure = DeviceOtaFailure(
                reason = DeviceOtaFailureReason.CONNECTION,
                recoverable = true
            )
        )

        policy.recordResult(
            deviceUid,
            Result.failure(IllegalStateException("Device is temporarily unavailable."))
        )
        assertFalse(policy.shouldRefresh(deviceUid, recoverableFailure))

        nowMillis += 99L
        assertFalse(policy.shouldRefresh(deviceUid, recoverableFailure))

        nowMillis += 1L
        assertTrue(policy.shouldRefresh(deviceUid, recoverableFailure))
    }

    @Test
    fun `freshness is isolated per device`() {
        val policy = DeviceFirmwareAvailabilityRefreshPolicy(
            nowMillis = { 20_000L },
            freshnessMillis = DEVICE_FIRMWARE_AVAILABILITY_FRESHNESS_MILLIS
        )
        val first = DeviceUid("device-one")
        val second = DeviceUid("device-two")

        policy.recordResult(
            first,
            Result.success(DeviceOtaState.Idle(first.value))
        )

        assertFalse(policy.shouldRefresh(first, DeviceOtaState.Idle(first.value)))
        assertTrue(policy.shouldRefresh(second, DeviceOtaState.Idle(second.value)))
    }

    @Test
    fun `passive refresh never replaces actionable or active ota state`() {
        val policy = DeviceFirmwareAvailabilityRefreshPolicy(nowMillis = { 30_000L })
        val deviceUid = DeviceUid("device-one")

        assertFalse(
            policy.shouldRefresh(
                deviceUid,
                DeviceOtaState.Checking(deviceUid.value, currentVersion = "1.0.3")
            )
        )
        assertFalse(
            policy.shouldRefresh(
                deviceUid,
                DeviceOtaState.UpdateAvailable(preparedPlan(deviceUid.value))
            )
        )
        assertFalse(
            policy.shouldRefresh(
                deviceUid,
                DeviceOtaState.Unsupported(deviceUid.value)
            )
        )
    }

    @Test
    fun `non recoverable failure is never retried automatically`() {
        val policy = DeviceFirmwareAvailabilityRefreshPolicy(
            nowMillis = { 35_000L },
            failureRetryMillis = 0L
        )
        val deviceUid = DeviceUid("device-one")
        val terminalFailure = DeviceOtaState.Failed(
            deviceUid = deviceUid.value,
            failure = DeviceOtaFailure(
                reason = DeviceOtaFailureReason.SECURITY_VALIDATION_FAILED,
                recoverable = false
            )
        )

        policy.recordResult(
            deviceUid,
            Result.failure(IllegalStateException("Signature validation failed."))
        )

        assertFalse(policy.shouldRefresh(deviceUid, terminalFailure))
    }

    @Test
    fun `up to date state may be refreshed after freshness expires`() {
        var nowMillis = 40_000L
        val policy = DeviceFirmwareAvailabilityRefreshPolicy(
            nowMillis = { nowMillis },
            freshnessMillis = 500L
        )
        val deviceUid = DeviceUid("device-one")
        val upToDate = DeviceOtaState.UpToDate(
            deviceUid = deviceUid.value,
            currentVersion = "1.0.3",
            latestVersion = "1.0.3",
            releaseContent = DeviceFirmwareReleaseContent.EMPTY
        )

        policy.recordResult(deviceUid, Result.success(upToDate))
        assertFalse(policy.shouldRefresh(deviceUid, upToDate))

        nowMillis += 500L
        assertTrue(policy.shouldRefresh(deviceUid, upToDate))
    }

    @Test
    fun `result boundary rethrows cancellation instead of swallowing it`() {
        val cancellation = CancellationException("cancelled")

        try {
            Result.failure<DeviceOtaState>(cancellation)
                .rethrowFatalOrCancellation()
            fail("CancellationException must be rethrown.")
        } catch (error: CancellationException) {
            assertSame(cancellation, error)
        }
    }

    @Test
    fun `prepared update mapping preserves the original operation failure`() {
        val operationFailure = IllegalStateException("Availability failed.")

        val result = Result.failure<DeviceOtaState>(operationFailure)
            .toPreparedUpdateResult()

        assertSame(operationFailure, result.exceptionOrNull())
    }

    private fun preparedPlan(deviceUid: String) = PreparedDeviceFirmwareUpdate(
        deviceUid = deviceUid,
        currentVersion = "1.0.3",
        targetVersion = "1.1.0",
        channel = "stable",
        environment = "light_wrgb_pro_elite",
        productKey = "LIGHT_WRGB_PRO_ELITE",
        productId = "com.aqualight.light.wrgb_pro_elite",
        model = "wrgb_pro_elite_120",
        hardwareRevision = "2.0",
        displayName = "WRGB Pro Elite 120",
        filename = "firmware.bin",
        downloadUrl = "https://example.invalid/firmware.bin",
        sha256 = "a".repeat(64),
        sizeBytes = 1_024,
        applyNow = true
    )
}
