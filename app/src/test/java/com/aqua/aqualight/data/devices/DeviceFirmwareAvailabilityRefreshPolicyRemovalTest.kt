package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareAvailabilityRefreshPolicyRemovalTest {

    @Test
    fun `removing one device freshness leaves other devices untouched`() {
        val policy = DeviceFirmwareAvailabilityRefreshPolicy(
            nowMillis = { 10_000L },
            freshnessMillis = 60_000L
        )
        val removed = DeviceUid("device-removed")
        val retained = DeviceUid("device-retained")

        policy.recordResult(removed, Result.success(DeviceOtaState.Idle(removed.value)))
        policy.recordResult(retained, Result.success(DeviceOtaState.Idle(retained.value)))
        assertFalse(policy.shouldRefresh(removed, DeviceOtaState.Idle(removed.value)))
        assertFalse(policy.shouldRefresh(retained, DeviceOtaState.Idle(retained.value)))

        policy.remove(removed)

        assertTrue(policy.shouldRefresh(removed, DeviceOtaState.Idle(removed.value)))
        assertFalse(policy.shouldRefresh(retained, DeviceOtaState.Idle(retained.value)))
    }

    @Test
    fun `background refresh revalidates an available release after freshness expires`() {
        var nowMillis = 20_000L
        val policy = DeviceFirmwareAvailabilityRefreshPolicy(
            nowMillis = { nowMillis },
            freshnessMillis = 1_000L
        )
        val deviceUid = DeviceUid("device-available")
        val available = DeviceOtaState.UpdateAvailable(preparedPlan(deviceUid.value))

        policy.recordResult(deviceUid, Result.success(available))
        assertFalse(policy.shouldRefresh(deviceUid, available))
        assertFalse(policy.shouldRefreshForBackground(deviceUid, available))

        nowMillis += 1_000L
        assertFalse(policy.shouldRefresh(deviceUid, available))
        assertTrue(policy.shouldRefreshForBackground(deviceUid, available))
    }

    @Test
    fun `background refresh rechecks a completed device after freshness expires`() {
        var nowMillis = 30_000L
        val policy = DeviceFirmwareAvailabilityRefreshPolicy(
            nowMillis = { nowMillis },
            freshnessMillis = 1_000L
        )
        val deviceUid = DeviceUid("device-completed")
        val succeeded = DeviceOtaState.Succeeded(
            deviceUid = deviceUid.value,
            targetVersion = "1.1.0",
            releaseContent = DeviceFirmwareReleaseContent.EMPTY
        )

        policy.recordResult(deviceUid, Result.success(succeeded))
        assertFalse(policy.shouldRefreshForBackground(deviceUid, succeeded))

        nowMillis += 1_000L
        assertTrue(policy.shouldRefreshForBackground(deviceUid, succeeded))
    }

    private fun preparedPlan(deviceUid: String) = PreparedDeviceFirmwareUpdate(
        deviceUid = deviceUid,
        currentVersion = "1.0.0",
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
        applyNow = true,
        releaseContent = DeviceFirmwareReleaseContent.EMPTY
    )
}
