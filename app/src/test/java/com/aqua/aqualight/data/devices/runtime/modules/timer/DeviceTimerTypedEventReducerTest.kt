package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTimerTypedEventReducerTest {
    @Test
    fun `status event seeds one device Timer state`() {
        val store = DeviceTimerRuntimeStateStore()
        val reducer = supportedReducer(store)

        val result = reducer.apply(statusEvent(DEVICE_A, uptimeMs = 12_000L))
        val state = store.states.value.getValue(DEVICE_A)

        assertEquals(DeviceTimerEventApplyResult.Applied, result)
        assertEquals(12_000L, state.status?.uptimeMs)
        assertEquals(listOf("Day Filter"), state.config?.schedules?.map { it.name })
        assertEquals(false, state.requiresStatusRefresh)
    }

    @Test
    fun `older and duplicate status events are ignored`() {
        val store = DeviceTimerRuntimeStateStore()
        val reducer = supportedReducer(store)
        reducer.apply(statusEvent(DEVICE_A, uptimeMs = 12_000L))

        val older = reducer.apply(statusEvent(DEVICE_A, uptimeMs = 11_999L))
        val duplicate = reducer.apply(statusEvent(DEVICE_A, uptimeMs = 12_000L))

        assertEquals(DeviceTimerEventApplyResult.Ignored, older)
        assertEquals(DeviceTimerEventApplyResult.Ignored, duplicate)
        assertEquals(12_000L, store.states.value.getValue(DEVICE_A).status?.uptimeMs)
    }

    @Test
    fun `status freshness accepts firmware uptime wraparound`() {
        val store = DeviceTimerRuntimeStateStore()
        val reducer = supportedReducer(store)
        reducer.apply(statusEvent(DEVICE_A, uptimeMs = TIMER_DEVICE_UPTIME_MAX_MS - 10L))

        val result = reducer.apply(statusEvent(DEVICE_A, uptimeMs = 20L))

        assertEquals(DeviceTimerEventApplyResult.Applied, result)
        assertEquals(20L, store.states.value.getValue(DEVICE_A).status?.uptimeMs)
    }

    @Test
    fun `config and channel command events reduce correlated mutation results`() {
        val store = DeviceTimerRuntimeStateStore()
        val reducer = supportedReducer(store)
        reducer.apply(statusEvent(DEVICE_A))

        val configResult = reducer.apply(configEvent(DEVICE_A))
        val channelResult = reducer.apply(channelEvent(DEVICE_A, displayName = "Return Pump"))
        val state = store.states.value.getValue(DEVICE_A)

        assertEquals(DeviceTimerEventApplyResult.Applied, configResult)
        assertEquals(DeviceTimerEventApplyResult.Applied, channelResult)
        assertEquals("Return Pump", state.config?.channels?.first()?.displayNameOverride)
        assertEquals("Return Pump", state.status?.channels?.first()?.displayName)
        assertEquals(DeviceTimerRegime.ON, state.config?.channels?.first()?.regime)
        assertEquals(DeviceTimerRegime.ON, state.status?.channels?.first()?.regime)
        assertTrue(state.requiresStatusRefresh)
    }

    @Test
    fun `same event sequence remains device isolated`() {
        val store = DeviceTimerRuntimeStateStore()
        val reducer = supportedReducer(store)
        reducer.apply(statusEvent(DEVICE_A))
        reducer.apply(statusEvent(DEVICE_B))
        reducer.apply(channelEvent(DEVICE_A))

        assertEquals(
            DeviceTimerRegime.ON,
            store.states.value.getValue(DEVICE_A).status?.channels?.first()?.regime
        )
        assertEquals(
            DeviceTimerRegime.AUTO,
            store.states.value.getValue(DEVICE_B).status?.channels?.first()?.regime
        )
    }

    @Test
    fun `wrong Timer command module is rejected as malformed`() {
        val store = DeviceTimerRuntimeStateStore()
        val reducer = supportedReducer(store)
        val event = DeviceRuntimeTypedEvent(
            deviceUid = DEVICE_A,
            generation = GENERATION,
            messageId = "evt-invalid",
            type = DeviceRuntimeTypedEvent.Type.TIMER_STATUS_CHANGED,
            payload = DeviceRuntimeEventPayload.CommandResult(
                commandId = "cmd-invalid",
                commandModule = "dosing",
                commandAction = DeviceTimerRuntimeContract.Action.CONFIG_APPLY,
                sessionId = "session-1",
                publishedAtMillis = 20_000L,
                result = DeviceTimerRuntimeFixtures.configApply()
            )
        )

        assertTrue(reducer.apply(event) is DeviceTimerEventApplyResult.Malformed)
    }

    @Test
    fun `dosing product Timer event is ignored before parsing`() {
        val store = DeviceTimerRuntimeStateStore()
        val reducer = DeviceTimerTypedEventReducer(store) {
            DeviceTimerRuntimeAccess.UNAVAILABLE
        }
        val malformedPayload = DeviceTimerRuntimeFixtures.status().put("unexpected", true)
        val event = statusEvent(DEVICE_A).copy(
            payload = DeviceRuntimeEventPayload.Snapshot(malformedPayload)
        )

        assertEquals(DeviceTimerEventApplyResult.Ignored, reducer.apply(event))
        assertTrue(store.states.value.isEmpty())
    }

    private fun supportedReducer(
        store: DeviceTimerRuntimeStateStore
    ) = DeviceTimerTypedEventReducer(store) { SUPPORTED_ACCESS }

    private fun statusEvent(
        deviceUid: DeviceUid,
        uptimeMs: Long = 12_000L
    ) = DeviceRuntimeTypedEvent(
        deviceUid = deviceUid,
        generation = GENERATION,
        messageId = "evt-status-${deviceUid.value}-$uptimeMs",
        type = DeviceRuntimeTypedEvent.Type.TIMER_STATUS_CHANGED,
        payload = DeviceRuntimeEventPayload.Snapshot(
            DeviceTimerRuntimeFixtures.status(uptimeMs = uptimeMs)
        )
    )

    private fun configEvent(deviceUid: DeviceUid) = DeviceRuntimeTypedEvent(
        deviceUid = deviceUid,
        generation = GENERATION,
        messageId = "evt-config-${deviceUid.value}",
        type = DeviceRuntimeTypedEvent.Type.TIMER_STATUS_CHANGED,
        payload = DeviceRuntimeEventPayload.CommandResult(
            commandId = "cmd-config",
            commandModule = DeviceTimerRuntimeContract.MODULE,
            commandAction = DeviceTimerRuntimeContract.Action.CONFIG_APPLY,
            sessionId = "session-1",
            publishedAtMillis = 20_000L,
            result = DeviceTimerRuntimeFixtures.configApply()
        )
    )

    private fun channelEvent(
        deviceUid: DeviceUid,
        displayName: String = "Filter"
    ) = DeviceRuntimeTypedEvent(
        deviceUid = deviceUid,
        generation = GENERATION,
        messageId = "evt-channel-${deviceUid.value}",
        type = DeviceRuntimeTypedEvent.Type.TIMER_STATUS_CHANGED,
        payload = DeviceRuntimeEventPayload.CommandResult(
            commandId = "cmd-channel",
            commandModule = DeviceTimerRuntimeContract.MODULE,
            commandAction = DeviceTimerRuntimeContract.Action.CHANNEL_SET,
            sessionId = "session-1",
            publishedAtMillis = 20_001L,
            result = DeviceTimerRuntimeFixtures.channelSet(displayName = displayName)
        )
    )

    private companion object {
        val DEVICE_A = DeviceUid("AQL-TIMER-A")
        val DEVICE_B = DeviceUid("AQL-TIMER-B")
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
        val SUPPORTED_ACCESS = DeviceTimerRuntimeAccess(
            supportsApi = true,
            channelCount = 2,
            supportsSchedules = true,
            supportsChannelState = true,
            supportsChannelDisplayName = true
        )
    }
}
