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
            otaSupported = false,
            actionable = false
        )
    }

    @Test
    fun staleIntentForDeletedDeviceIsRejected() {
        assertDecision(
            expected = DeviceFirmwareNotificationRouteDecision.REJECT,
            repositoryReady = true,
            deviceExists = false,
            otaSupported = false,
            actionable = false
        )
    }

    @Test
    fun deviceWithoutOtaCapabilityIsRejected() {
        assertDecision(
            expected = DeviceFirmwareNotificationRouteDecision.REJECT,
            repositoryReady = true,
            deviceExists = true,
            otaSupported = false,
            actionable = false
        )
    }

    @Test
    fun staleNonActionableFirmwareIntentIsRejected() {
        assertDecision(
            expected = DeviceFirmwareNotificationRouteDecision.REJECT,
            repositoryReady = true,
            deviceExists = true,
            otaSupported = true,
            actionable = false
        )
    }

    @Test
    fun registeredActionableOtaDeviceCanOpenFirmwareScreen() {
        assertDecision(
            expected = DeviceFirmwareNotificationRouteDecision.OPEN,
            repositoryReady = true,
            deviceExists = true,
            otaSupported = true,
            actionable = true
        )
    }

    private fun assertDecision(
        expected: DeviceFirmwareNotificationRouteDecision,
        repositoryReady: Boolean,
        deviceExists: Boolean,
        otaSupported: Boolean,
        actionable: Boolean
    ) {
        assertEquals(
            expected,
            DeviceFirmwareNotificationDestinationPolicy.evaluate(
                repositoryReady = repositoryReady,
                deviceExists = deviceExists,
                otaSupported = otaSupported,
                actionable = actionable
            )
        )
    }
}
