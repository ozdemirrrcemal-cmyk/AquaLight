package com.aqua.aqualight.ui.navigation

import com.aqua.aqualight.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRouteNavigatorDosingPolicyTest {

    @Test
    fun `same channel and destination are not opened twice`() {
        assertTrue(
            AppRouteNavigator.DosingChannelRouteIdempotencyPolicy.isAlreadyOpen(
                current = routeIdentity(R.id.deviceDosingChannelDetailFragment),
                requested = routeIdentity(R.id.deviceDosingChannelDetailFragment)
            )
        )
    }

    @Test
    fun `a different channel or destination requires a new route`() {
        assertFalse(
            AppRouteNavigator.DosingChannelRouteIdempotencyPolicy.isAlreadyOpen(
                current = routeIdentity(R.id.deviceDosingChannelDetailFragment),
                requested = routeIdentity(
                    destinationId = R.id.deviceDosingChannelDetailFragment,
                    slotId = "dosing:channel2"
                )
            )
        )
        assertFalse(
            AppRouteNavigator.DosingChannelRouteIdempotencyPolicy.isAlreadyOpen(
                current = routeIdentity(R.id.deviceDosingChannelCalibrationFragment),
                requested = routeIdentity(R.id.deviceDosingChannelDetailFragment)
            )
        )
    }

    private fun routeIdentity(
        destinationId: Int,
        slotId: String = SLOT_ID
    ) = AppRouteNavigator.DosingChannelRouteIdentity(
        destinationId = destinationId,
        deviceUid = DEVICE_UID,
        slotId = slotId
    )

    private companion object {
        const val DEVICE_UID = "device-1"
        const val SLOT_ID = "dosing:channel1"
    }
}
