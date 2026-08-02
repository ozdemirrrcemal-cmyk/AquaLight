package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingTypedEventReducerTest {
    @Test
    fun `status event seeds one device Dosing state and config baseline`() {
        val store = DeviceDosingRuntimeStateStore()
        val reducer = supportedReducer(store)

        val result = reducer.apply(statusEvent(DEVICE_A, uptimeMs = 12_000L))
        val state = store.states.value.getValue(DEVICE_A)

        assertEquals(DeviceDosingEventApplyResult.Applied, result)
        assertEquals(12_000L, state.status?.uptimeMs)
        assertEquals("Morning Nutrients", state.config?.schedules?.single()?.name)
        assertFalse(state.requiresStatusRefresh)
    }

    @Test
    fun `older duplicate and uptime half range events are ignored`() {
        val store = DeviceDosingRuntimeStateStore()
        val reducer = supportedReducer(store)
        reducer.apply(statusEvent(DEVICE_A, uptimeMs = 12_000L))

        val older = reducer.apply(statusEvent(DEVICE_A, uptimeMs = 11_999L))
        val duplicate = reducer.apply(statusEvent(DEVICE_A, uptimeMs = 12_000L))
        val ambiguous = reducer.apply(
            statusEvent(DEVICE_A, uptimeMs = 12_000L + 0x8000_0000L)
        )

        assertEquals(DeviceDosingEventApplyResult.Ignored, older)
        assertEquals(DeviceDosingEventApplyResult.Ignored, duplicate)
        assertEquals(DeviceDosingEventApplyResult.Ignored, ambiguous)
        assertEquals(12_000L, store.states.value.getValue(DEVICE_A).status?.uptimeMs)
    }

    @Test
    fun `status freshness accepts firmware millis wraparound`() {
        val store = DeviceDosingRuntimeStateStore()
        val reducer = supportedReducer(store)
        reducer.apply(statusEvent(DEVICE_A, uptimeMs = DOSING_DEVICE_UPTIME_MAX_MS - 10L))

        val result = reducer.apply(statusEvent(DEVICE_A, uptimeMs = 20L))

        assertEquals(DeviceDosingEventApplyResult.Applied, result)
        assertEquals(20L, store.states.value.getValue(DEVICE_A).status?.uptimeMs)
    }

    @Test
    fun `command events reduce config pump calibration and reservoir results`() {
        val store = DeviceDosingRuntimeStateStore()
        val reducer = supportedReducer(store)
        reducer.apply(statusEvent(DEVICE_A))

        val config = reducer.apply(
            commandEvent(
                DEVICE_A,
                DeviceDosingRuntimeContract.Action.CONFIG_APPLY,
                DeviceDosingRuntimeFixtures.configApply(
                    channelOneDisplayNameOverride = "Macro Pump"
                )
            )
        )
        val prime = reducer.apply(
            commandEvent(
                DEVICE_A,
                DeviceDosingRuntimeContract.Action.PRIME_START,
                DeviceDosingRuntimeFixtures.pump(
                    DeviceDosingRuntimeContract.Action.PRIME_START,
                    active = true,
                    displayName = "Macro Pump"
                )
            )
        )
        val calibration = reducer.apply(
            commandEvent(
                DEVICE_A,
                DeviceDosingRuntimeContract.Action.CALIBRATION_CONFIRM,
                DeviceDosingRuntimeFixtures.calibrationConfirm()
                    .also { result ->
                        result.getJSONObject("channel").put("displayName", "Macro Pump")
                    }
            )
        )
        val reservoir = reducer.apply(
            commandEvent(
                DEVICE_A,
                DeviceDosingRuntimeContract.Action.RESERVOIR_REFILL,
                DeviceDosingRuntimeFixtures.reservoirRefill()
                    .also { result ->
                        result.getJSONObject("channel").put("displayName", "Macro Pump")
                    }
            )
        )
        val state = store.states.value.getValue(DEVICE_A)

        assertEquals(DeviceDosingEventApplyResult.Applied, config)
        assertEquals(DeviceDosingEventApplyResult.Applied, prime)
        assertEquals(DeviceDosingEventApplyResult.Applied, calibration)
        assertEquals(DeviceDosingEventApplyResult.Applied, reservoir)
        assertEquals("Macro Pump", state.status?.channels?.first()?.displayName)
        assertEquals(500.0, state.status?.channels?.first()?.dosing?.reservoirRemainingMl)
        assertTrue(state.requiresStatusRefresh)
    }

    @Test
    fun `same event sequence remains device isolated`() {
        val store = DeviceDosingRuntimeStateStore()
        val reducer = supportedReducer(store)
        reducer.apply(statusEvent(DEVICE_A))
        reducer.apply(statusEvent(DEVICE_B))
        reducer.apply(
            commandEvent(
                DEVICE_A,
                DeviceDosingRuntimeContract.Action.PRIME_START,
                DeviceDosingRuntimeFixtures.pump(
                    DeviceDosingRuntimeContract.Action.PRIME_START,
                    active = true
                )
            )
        )

        assertEquals(
            1.0,
            store.states.value.getValue(DEVICE_A).status?.channels?.first()?.valueManual
        )
        assertEquals(
            -1.0,
            store.states.value.getValue(DEVICE_B).status?.channels?.first()?.valueManual
        )
    }

    @Test
    fun `wrong Dosing command module is rejected as malformed`() {
        val store = DeviceDosingRuntimeStateStore()
        val reducer = supportedReducer(store)
        reducer.apply(statusEvent(DEVICE_A))
        val event = commandEvent(
            DEVICE_A,
            DeviceDosingRuntimeContract.Action.PRIME_START,
            DeviceDosingRuntimeFixtures.pump(
                DeviceDosingRuntimeContract.Action.PRIME_START,
                active = true
            ),
            commandModule = "timer"
        )

        assertTrue(reducer.apply(event) is DeviceDosingEventApplyResult.Malformed)
    }

    @Test
    fun `unsupported product event is ignored before strict parsing`() {
        val store = DeviceDosingRuntimeStateStore()
        val reducer = DeviceDosingTypedEventReducer(store) {
            DeviceDosingRuntimeAccess.UNAVAILABLE
        }
        val malformed = DeviceDosingRuntimeFixtures.status().put("unexpected", true)
        val event = statusEvent(DEVICE_A).copy(
            payload = DeviceRuntimeEventPayload.Snapshot(malformed)
        )

        assertEquals(DeviceDosingEventApplyResult.Ignored, reducer.apply(event))
        assertTrue(store.states.value.isEmpty())
    }

    @Test
    fun `unknown Dosing command event is ignored without changing state`() {
        val store = DeviceDosingRuntimeStateStore()
        val reducer = supportedReducer(store)
        reducer.apply(statusEvent(DEVICE_A))
        val before = store.states.value.getValue(DEVICE_A)

        val result = reducer.apply(
            commandEvent(DEVICE_A, "future.action", JSONObject())
        )

        assertEquals(DeviceDosingEventApplyResult.Ignored, result)
        assertEquals(before, store.states.value.getValue(DEVICE_A))
    }

    private fun supportedReducer(
        store: DeviceDosingRuntimeStateStore
    ) = DeviceDosingTypedEventReducer(store) { SUPPORTED_ACCESS }

    private fun statusEvent(
        deviceUid: DeviceUid,
        uptimeMs: Long = 12_000L
    ) = DeviceRuntimeTypedEvent(
        deviceUid = deviceUid,
        generation = GENERATION,
        messageId = "evt-status-${deviceUid.value}-$uptimeMs",
        type = DeviceRuntimeTypedEvent.Type.DOSING_STATUS_CHANGED,
        payload = DeviceRuntimeEventPayload.Snapshot(
            DeviceDosingRuntimeFixtures.status(uptimeMs = uptimeMs)
        )
    )

    private fun commandEvent(
        deviceUid: DeviceUid,
        action: String,
        result: JSONObject,
        commandModule: String = DeviceDosingRuntimeContract.MODULE
    ) = DeviceRuntimeTypedEvent(
        deviceUid = deviceUid,
        generation = GENERATION,
        messageId = "evt-$action-${deviceUid.value}",
        type = DeviceRuntimeTypedEvent.Type.DOSING_STATUS_CHANGED,
        payload = DeviceRuntimeEventPayload.CommandResult(
            commandId = "cmd-$action",
            commandModule = commandModule,
            commandAction = action,
            sessionId = "session-1",
            publishedAtMillis = 20_000L,
            result = result
        )
    )

    private companion object {
        val DEVICE_A = DeviceUid("AQL-DOSING-A")
        val DEVICE_B = DeviceUid("AQL-DOSING-B")
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
        val SUPPORTED_ACCESS = DeviceDosingRuntimeAccess(
            supportsApi = true,
            channelCount = 2,
            supportsSchedules = true,
            supportsPrime = true,
            supportsManualDose = true,
            supportsCalibrationWorkflow = true,
            supportsReservoirRefill = true,
            supportsChannelDisplayName = true
        )
    }
}
