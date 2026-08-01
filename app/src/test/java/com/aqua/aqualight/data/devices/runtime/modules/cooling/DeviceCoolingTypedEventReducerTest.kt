package com.aqua.aqualight.data.devices.runtime.modules.cooling

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `completed invalid sample replaces stale valid temperature`() {
        val store = DeviceCoolingRuntimeStateStore()
        val reducer = DeviceCoolingTypedEventReducer(store)
        reducer.apply(statusEvent(DEVICE_A))

        val result = reducer.apply(
            temperatureEvent(DEVICE_A, temperatureC = null, sampledAtMs = 15_000L)
        )
        val state = store.states.value.getValue(DEVICE_A)

        assertEquals(DeviceCoolingEventApplyResult.Applied, result)
        assertFalse(checkNotNull(state.temperature).readingValid)
        assertNull(state.temperature?.temperatureC)
        assertEquals(15_000L, state.temperature?.sampledAtMs)
        assertFalse(checkNotNull(state.status?.temperature).readingValid)
    }

    @Test
    fun `older and duplicate temperature events are ignored`() {
        val store = DeviceCoolingRuntimeStateStore()
        val reducer = DeviceCoolingTypedEventReducer(store)
        reducer.apply(statusEvent(DEVICE_A))
        reducer.apply(temperatureEvent(DEVICE_A, 30.5, 15_000L))

        val older = reducer.apply(temperatureEvent(DEVICE_A, 29.0, 14_000L))
        val duplicate = reducer.apply(temperatureEvent(DEVICE_A, 31.0, 15_000L))
        val state = store.states.value.getValue(DEVICE_A)

        assertEquals(DeviceCoolingEventApplyResult.Ignored, older)
        assertEquals(DeviceCoolingEventApplyResult.Ignored, duplicate)
        assertEquals(30.5, state.temperature?.temperatureC!!, 0.0001)
        assertEquals(15_000L, state.temperature?.sampledAtMs)
    }

    @Test
    fun `older status response preserves newer temperature event`() {
        val store = DeviceCoolingRuntimeStateStore()
        val reducer = DeviceCoolingTypedEventReducer(store)
        reducer.apply(statusEvent(DEVICE_A))
        reducer.apply(temperatureEvent(DEVICE_A, 30.5, 15_000L))

        val result = reducer.apply(statusEvent(DEVICE_A, sampledAtMs = 12_000L))
        val state = store.states.value.getValue(DEVICE_A)

        assertEquals(DeviceCoolingEventApplyResult.Applied, result)
        assertEquals(30.5, state.temperature?.temperatureC!!, 0.0001)
        assertEquals(15_000L, state.status?.temperature?.sampledAtMs)
    }

    @Test
    fun `temperature freshness accepts firmware uptime wraparound`() {
        val store = DeviceCoolingRuntimeStateStore()
        val reducer = DeviceCoolingTypedEventReducer(store)
        reducer.apply(
            statusEvent(
                deviceUid = DEVICE_A,
                temperatureC = 28.0,
                sampledAtMs = COOLING_DEVICE_UPTIME_MAX_MS - 10L
            )
        )

        val result = reducer.apply(
            temperatureEvent(DEVICE_A, temperatureC = 29.0, sampledAtMs = 20L)
        )
        val state = store.states.value.getValue(DEVICE_A)

        assertEquals(DeviceCoolingEventApplyResult.Applied, result)
        assertEquals(29.0, state.temperature?.temperatureC!!, 0.0001)
        assertEquals(20L, state.temperature?.sampledAtMs)
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

    private fun statusEvent(
        deviceUid: DeviceUid,
        temperatureC: Double? = 27.4,
        sampledAtMs: Long = 12_000L
    ) = DeviceRuntimeTypedEvent(
        deviceUid = deviceUid,
        generation = GENERATION,
        messageId = "evt-status-${deviceUid.value}-$sampledAtMs",
        type = DeviceRuntimeTypedEvent.Type.COOLING_STATUS_CHANGED,
        payload = DeviceRuntimeEventPayload.Snapshot(
            DeviceCoolingRuntimeFixtures.status(
                temperatureC = temperatureC,
                temperatureSampledAtMs = sampledAtMs,
                uptimeMs = sampledAtMs
            )
        )
    )

    private fun temperatureEvent(
        deviceUid: DeviceUid,
        temperatureC: Double?,
        sampledAtMs: Long
    ) = DeviceRuntimeTypedEvent(
        deviceUid = deviceUid,
        generation = GENERATION,
        messageId = "evt-temperature-${deviceUid.value}-$sampledAtMs",
        type = DeviceRuntimeTypedEvent.Type.TEMPERATURE_CHANGED,
        payload = DeviceRuntimeEventPayload.Snapshot(
            DeviceCoolingRuntimeFixtures.temperature(
                temperatureC = temperatureC,
                sampledAtMs = sampledAtMs
            )
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
