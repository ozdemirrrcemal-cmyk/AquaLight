package com.aqua.aqualight.application.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceDosingChannelDestinationPolicyTest {

    @Test
    fun `calibrated channel opens detail through authorized channel route`() {
        assertEquals(
            DeviceDosingChannelDestination.DETAIL,
            DeviceDosingChannelDestinationPolicy.resolve(
                calibrated = true,
                allowedRoutes = setOf(DeviceRootRoute.DOSING_CHANNELS)
            )
        )
    }

    @Test
    fun `uncalibrated channel opens calibration only when both routes are authorized`() {
        assertEquals(
            DeviceDosingChannelDestination.CALIBRATION,
            DeviceDosingChannelDestinationPolicy.resolve(
                calibrated = false,
                allowedRoutes = setOf(
                    DeviceRootRoute.DOSING_CHANNELS,
                    DeviceRootRoute.DOSING_CALIBRATION
                )
            )
        )
    }

    @Test
    fun `uncalibrated channel is rejected without calibration authorization`() {
        assertNull(
            DeviceDosingChannelDestinationPolicy.resolve(
                calibrated = false,
                allowedRoutes = setOf(DeviceRootRoute.DOSING_CHANNELS)
            )
        )
    }

    @Test
    fun `all channel destinations are rejected without central channel authorization`() {
        assertNull(
            DeviceDosingChannelDestinationPolicy.resolve(
                calibrated = true,
                allowedRoutes = setOf(DeviceRootRoute.DOSING_CALIBRATION)
            )
        )
        assertNull(
            DeviceDosingChannelDestinationPolicy.resolve(
                calibrated = false,
                allowedRoutes = setOf(DeviceRootRoute.DOSING_CALIBRATION)
            )
        )
    }
}
