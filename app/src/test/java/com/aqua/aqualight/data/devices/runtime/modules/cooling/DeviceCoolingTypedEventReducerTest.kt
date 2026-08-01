package com.aqua.aqualight.data.devices.runtime.modules.cooling

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCoolingTypedEventReducerTest {
    @Test
    fun `temperature event updates temperature without replacing config`() {
        val store = DeviceCoolingRuntimeStateStore()
        val reducer = DeviceCoolingTypedEventReducer(store)
        reducer.apply(statusEvent(DEVICE_A))
        val configBefore = store.states.value.getValue(DEVICE_A).config

        val result = reducer.apply(
            temperatureEvent(DEVICE_A, temperatureC = 30.5, sampledAtMs = 15_000L)
        )
        val state = store.states.value.getValue(DEVICE_A)

        assertEquals(DeviceCoolingEventApplyResult.Applied, result)
        assertEquals(configBefore, state.config)
        assertEquals(30.5, state.temperature?.temperatureC!!, 0.0001)
        assertEquals(30.5, state.status?.temperature?.temperatureC!!, 0.0001)
    }

    @Test
    fun `config command event preserves latest temperature`() {
        val store = DeviceCoolingRuntimeStateStore()
        val reducer = DeviceCoolingTypedEventReducer(store)
        reducer.apply(statusEvent(DEVICE_A))
        reducer.apply(temperatureEvent(DEVICE_A, 30.5, 15_000L))

        val result = reducer.apply(configEvent(DEVICE_A))
        val state = store.states.value.getValue(DEVICE_A)

        assertEquals(DeviceCoolingEventApplyResult.Applied, result)
        assertEquals(DeviceCoolingMode.ON, state.config?.mode)
        assertEquals(DeviceCoolingMode.ON, state.status?.mode)
        assertEquals(30.5, state.temperature?.temperatureC!!, 0.0001)
    }

    @Test
    fun `same event sequence remains device isolated`() {
        val store = DeviceCoolingRuntimeStateStore()
        val reducer = DeviceCoolingTypedEventReducer(store)
        reducer.apply(statusEvent(DEVICE_A))
        reducer.apply(statusEvent(DEVICE_B))
        reducer.apply(temperatureEvent(DEVICE_A, 31.0, 20_000L))

        assertEquals(31.0, store.states.value.getValue(DEVICE_A).temperature?.temperatureC!!, 0.0001)
        assertEquals(27.4, store.states.value.getValue(DEVICE_B).temperature?.temperatureC!!, 0.0001)
    }

    @Test
    fun `temperature command envelope is rejected as malformed`() {
        val store = DeviceCoolingRuntimeStateStore()
        val reducer = DeviceCoolingTypedEventReducer(store)
        val event = DeviceRuntimeTypedEvent(
            deviceUid = DEVICE_A,
            generation = GENERATION,
            messageId = "evt-invalid",
            type = DeviceRuntimeTypedEvent.Type.TEMPERATURE_CHANGED,
            payload = DeviceRuntimeEventPayload.CommandResult(
                commandId = "cmd-1",
                commandModule = "temperature",
                commandAction = "changed",
                sessionId = "session-1",
                publishedAtMillis = 1L,
                result = DeviceCoolingRuntimeFixtures.temperature()
            )
        )

        assertTrue(reducer.apply(event) is DeviceCoolingEventApplyResult.Malformed)
    }

    private fun statusEvent(deviceUid: DeviceUid) = DeviceRuntimeTypedEvent(
        deviceUid = deviceUid,
        generation = GENERATION,
        messageId = "evt-status-${deviceUid.value}",
        type = DeviceRuntimeTypedEvent.Type.COOLING_STATUS_CHANGED,
        payload = DeviceRuntimeEventPayload.Snapshot(DeviceCoolingRuntimeFixtures.status())
    )

    private fun temperatureEvent(
        deviceUid: DeviceUid,
        temperatureC: Double,
        sampledAtMs: Long
    ) = DeviceRuntimeTypedEvent(
        deviceUid = deviceUid,
        generation = GENERATION,
        messageId = "evt-temperature-${deviceUid.value}",
        type = DeviceRuntimeTypedEvent.Type.TEMPERATURE_CHANGED,
        payload = DeviceRuntimeEventPayload.Snapshot(
            DeviceCoolingRuntimeFixtures.temperature(temperatureC)
                .put("sampledAtMs", sampledAtMs)
        )
    )

    private fun configEvent(deviceUid: DeviceUid) = DeviceRuntimeTypedEvent(
        deviceUid = deviceUid,
        generation = GENERATION,
        messageId = "evt-config-${deviceUid.value}",
        type = DeviceRuntimeTypedEvent.Type.COOLING_STATUS_CHANGED,
        payload = DeviceRuntimeEventPayload.CommandResult(
            commandId = "cmd-config",
            commandModule = "cooling",
            commandAction = "config.apply",
            sessionId = "session-1",
            publishedAtMillis = 20_000L,
            result = DeviceCoolingRuntimeFixtures.configApply()
        )
    )

    private companion object {
        val DEVICE_A = DeviceUid("AQL-COOLING-A")
        val DEVICE_B = DeviceUid("AQL-COOLING-B")
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
    }
}
