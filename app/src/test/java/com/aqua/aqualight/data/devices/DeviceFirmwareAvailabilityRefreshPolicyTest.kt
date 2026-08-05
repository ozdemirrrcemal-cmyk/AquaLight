package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareAvailabilityRefreshPolicyTest {

    @Test
    fun `passive refresh is fresh once per device within the application window`() {
        var nowMillis = 10_000L
        val policy = DeviceFirmwareAvailabilityRefreshPolicy(
            nowMillis = { nowMillis },
            freshnessMillis = 1_000L
        )
        val deviceUid = DeviceUid("device-one")
        val idle = DeviceOtaState.Idle(deviceUid.value)

        assertTrue(policy.shouldRefresh(deviceUid, idle))

        policy.recordAttempt(deviceUid)
        assertFalse(policy.shouldRefresh(deviceUid, idle))

        nowMillis += 999L
        assertFalse(policy.shouldRefresh(deviceUid, idle))

        nowMillis += 1L
        assertTrue(policy.shouldRefresh(deviceUid, idle))
    }

    @Test
    fun `freshness is isolated per device`() {
        val policy = DeviceFirmwareAvailabilityRefreshPolicy(
            nowMillis = { 20_000L },
            freshnessMillis = DEVICE_FIRMWARE_AVAILABILITY_FRESHNESS_MILLIS
        )
        val first = DeviceUid("device-one")
        val second = DeviceUid("device-two")

        policy.recordAttempt(first)

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

        policy.recordAttempt(deviceUid)
        assertFalse(policy.shouldRefresh(deviceUid, upToDate))

        nowMillis += 500L
        assertTrue(policy.shouldRefresh(deviceUid, upToDate))
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
