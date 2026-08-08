package com.aqua.aqualight.ui.navigation

import com.aqua.aqualight.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRouteNavigatorFirmwarePolicyTest {

    @Test
    fun repeatedRequestsForOpenDeviceRemainSingleDestination() {
        repeat(5) {
            assertTrue(
                AppRouteNavigator.DeviceFirmwareRouteIdempotencyPolicy.isAlreadyOpen(
                    currentDestinationId = R.id.deviceFirmwareUpdateFragment,
                    currentDeviceUid = DEVICE_UID,
                    requestedDeviceUid = DEVICE_UID
                )
            )
        }
    }

    @Test
    fun differentDeviceStillRequiresNavigation() {
        assertFalse(
            AppRouteNavigator.DeviceFirmwareRouteIdempotencyPolicy.isAlreadyOpen(
                currentDestinationId = R.id.deviceFirmwareUpdateFragment,
                currentDeviceUid = DEVICE_UID,
                requestedDeviceUid = "device-b"
            )
        )
    }

    @Test
    fun sameDeviceOnSettingsDestinationStillRequiresNavigation() {
        assertFalse(
            AppRouteNavigator.DeviceFirmwareRouteIdempotencyPolicy.isAlreadyOpen(
                currentDestinationId = R.id.deviceDosingSettingsFragment,
                currentDeviceUid = DEVICE_UID,
                requestedDeviceUid = DEVICE_UID
            )
        )
    }

    @Test
    fun blankRequestNeverMatches() {
        assertFalse(
            AppRouteNavigator.DeviceFirmwareRouteIdempotencyPolicy.isAlreadyOpen(
                currentDestinationId = R.id.deviceFirmwareUpdateFragment,
                currentDeviceUid = DEVICE_UID,
                requestedDeviceUid = " "
            )
        )
    }

    private companion object {
        const val DEVICE_UID = "device-a"
    }
}
