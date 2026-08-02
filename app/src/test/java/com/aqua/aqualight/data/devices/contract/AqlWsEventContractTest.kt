package com.aqua.aqualight.data.devices.contract

import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AqlWsEventContractTest {

    @Test
    fun `android event contract exactly matches firmware v1`() {
        val expected = linkedSetOf(
            "device.status.changed",
            "network.state.changed",
            "light.status.changed",
            "cooling.status.changed",
            "timer.status.changed",
            "dosing.status.changed",
            "temperature.changed",
            "time.status.changed",
            "firmware.ota.progress",
            "firmware.ota.completed",
            "system.restarting"
        )

        assertEquals(expected, AqlWsEventContract.qualifiedNames())
        assertEquals(
            expected,
            DeviceRuntimeTypedEvent.Type.values()
                .mapTo(linkedSetOf()) { type -> "${type.module}.${type.action}" }
        )
        assertEquals(11, AqlWsEventContract.definitions().size)
    }

    @Test
    fun `event registration uses exact module and action`() {
        assertTrue(
            AqlWsEventContract.isRegisteredEvent(
                AqlWsContract.MODULE_LIGHT,
                AqlWsEventContract.ACTION_STATUS_CHANGED
            )
        )
        assertFalse(
            AqlWsEventContract.isRegisteredEvent(
                AqlWsContract.MODULE_LIGHT,
                "status.change"
            )
        )
        assertFalse(
            AqlWsEventContract.isRegisteredEvent(
                "unknown",
                AqlWsEventContract.ACTION_STATUS_CHANGED
            )
        )
    }
}
