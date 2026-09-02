package com.aqua.aqualight.data.devices.runtime.modules.cooling

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingTypedEventReducerTest {
    @Test
    fun `Cooling V1 telemetry remains disconnected from legacy presentation state`() {
        val store = DeviceCoolingRuntimeStateStore()
        val reducer = DeviceCoolingTypedEventReducer(store)

        val result = reducer.apply(
            event(
                DeviceRuntimeTypedEvent.Type.COOLING_TELEMETRY_CHANGED,
                JSONObject().put("schema", "aql.cooling.v1")
            )
        )

        assertEquals(DeviceCoolingEventApplyResult.Ignored, result)
        assertTrue(store.states.value.isEmpty())
    }

    @Test
    fun `non Cooling events remain ignored`() {
        val store = DeviceCoolingRuntimeStateStore()
        val reducer = DeviceCoolingTypedEventReducer(store)

        val result = reducer.apply(
            event(
                DeviceRuntimeTypedEvent.Type.LIGHT_THERMAL_TELEMETRY_CHANGED,
                JSONObject().put("schema", "aql.light-thermal.v1")
            )
        )

        assertEquals(DeviceCoolingEventApplyResult.Ignored, result)
        assertTrue(store.states.value.isEmpty())
    }

    private fun event(
        type: DeviceRuntimeTypedEvent.Type,
        data: JSONObject
    ) = DeviceRuntimeTypedEvent(
        deviceUid = DEVICE,
        generation = GENERATION,
        messageId = "evt-${type.name}",
        type = type,
        payload = DeviceRuntimeEventPayload.Snapshot(data)
    )

    private companion object {
        val DEVICE = DeviceUid("AQL-COOLING-A")
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
    }
}
