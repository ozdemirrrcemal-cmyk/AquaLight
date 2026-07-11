package com.aqua.aqualight.ui.common.devicepresence

import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePresencePresentationMapperTest {

    @Test
    fun authenticatedDevice_isShownOnline() {
        assertTrue(
            DevicePresencePresentationMapper.isReachable(
                DeviceOnlineState.AUTHENTICATED
            )
        )
        assertEquals(
            "Online",
            DevicePresencePresentationMapper.availabilityLabel(
                DeviceOnlineState.AUTHENTICATED
            )
        )
    }

    @Test
    fun everyNonUsableTechnicalState_isShownOffline() {
        DeviceOnlineState.entries
            .filterNot { state -> state == DeviceOnlineState.AUTHENTICATED }
            .forEach { state ->
                assertFalse(
                    "Expected $state to be user-facing Offline.",
                    DevicePresencePresentationMapper.isReachable(state)
                )
                assertEquals(
                    "Offline",
                    DevicePresencePresentationMapper.availabilityLabel(state)
                )
            }
    }
}
