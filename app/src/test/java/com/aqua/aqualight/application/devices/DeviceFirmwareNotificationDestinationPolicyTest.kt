package com.aqua.aqualight.application.devices

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceFirmwareNotificationDestinationPolicyTest {

    @Test
    fun processDeathDefersUntilOwnerRepositoryIsReady() {
        assertDecision(
            expected = DeviceFirmwareNotificationRouteDecision.DEFER,
            repositoryReady = false,
            deviceExists = false,
            otaSupported = false
        )
    }

    @Test
    fun staleIntentForDeletedDeviceIsRejected() {
        assertDecision(
            expected = DeviceFirmwareNotificationRouteDecision.REJECT,
            repositoryReady = true,
            deviceExists = false,
            otaSupported = false
        )
    }

    @Test
    fun deviceWithoutOtaCapabilityIsRejected() {
        assertDecision(
            expected = DeviceFirmwareNotificationRouteDecision.REJECT,
            repositoryReady = true,
            deviceExists = true,
            otaSupported = false
        )
    }

    @Test
    fun registeredOtaDeviceCanOpenFirmwareScreen() {
        assertDecision(
            expected = DeviceFirmwareNotificationRouteDecision.OPEN,
            repositoryReady = true,
            deviceExists = true,
            otaSupported = true
        )
    }

    private fun assertDecision(
        expected: DeviceFirmwareNotificationRouteDecision,
        repositoryReady: Boolean,
        deviceExists: Boolean,
        otaSupported: Boolean
    ) {
        assertEquals(
            expected,
            DeviceFirmwareNotificationDestinationPolicy.evaluate(
                repositoryReady = repositoryReady,
                deviceExists = deviceExists,
                otaSupported = otaSupported
            )
        )
    }
}
