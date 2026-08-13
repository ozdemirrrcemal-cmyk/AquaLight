package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeAccess
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.dosing.events.DeviceDosingEventApplyResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.events.DeviceDosingTypedEventReducer
import com.aqua.aqualight.data.devices.runtime.modules.dosing.parsers.DeviceDosingStatusParser
import com.aqua.aqualight.data.devices.runtime.modules.dosing.state.DeviceDosingRuntimeStateStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingTypedEventReducerTest {
    @Test
    fun `slim status change is parsed and delegated to authoritative refresh callback`() {
        val store = DeviceDosingRuntimeStateStore()
        var refreshCalls = 0
        val reducer = supportedReducer(store) { deviceUid, data ->
            refreshCalls++
            store.recordStatusChange(deviceUid, DeviceDosingStatusParser.parseStatusChange(data))
        }

        val result = reducer.apply(statusChangeEvent(DEVICE_A, revision = 9L))
        val state = store.states.value.getValue(DEVICE_A)

        assertEquals(DeviceDosingEventApplyResult.Applied, result)
        assertEquals(1, refreshCalls)
        assertEquals(9L, state.lastStatusChange?.revision)
        assertTrue(state.requiresStatusRefresh)
        assertTrue(state.globalStatus == null)
    }

    @Test
    fun `duplicate slim status change sequence is ignored by canonical state store`() {
        val store = DeviceDosingRuntimeStateStore()
        val reducer = supportedReducer(store) { deviceUid, data ->
            store.recordStatusChange(deviceUid, DeviceDosingStatusParser.parseStatusChange(data))
        }

        assertEquals(DeviceDosingEventApplyResult.Applied, reducer.apply(statusChangeEvent(DEVICE_A, 9L)))
        assertEquals(DeviceDosingEventApplyResult.Ignored, reducer.apply(statusChangeEvent(DEVICE_A, 9L)))
    }

    @Test
    fun `command result events reduce new program and reset mutations`() {
        val store = DeviceDosingRuntimeStateStore()
        val reducer = supportedReducer(store) { _, _ -> false }

        val program = reducer.apply(
            commandEvent(
                DEVICE_A,
                DeviceDosingRuntimeContract.Action.PROGRAM_APPLY,
                DeviceDosingRuntimeFixtures.programApply(revision = 8L)
            )
        )
        val reset = reducer.apply(
            commandEvent(
                DEVICE_A,
                DeviceDosingRuntimeContract.Action.CHANNEL_RESET,
                DeviceDosingRuntimeFixtures.channelReset(revision = 9L)
            )
        )

        assertEquals(DeviceDosingEventApplyResult.Applied, program)
        assertEquals(DeviceDosingEventApplyResult.Applied, reset)
        assertEquals(9L, store.states.value.getValue(DEVICE_A).lastMutation?.channel?.revision)
        assertTrue(store.states.value.getValue(DEVICE_A).lastMutation?.channel?.program == null)
    }

    @Test
    fun `wrong command module is malformed and unknown action is ignored`() {
        val store = DeviceDosingRuntimeStateStore()
        val reducer = supportedReducer(store) { _, _ -> false }

        val wrongModule = reducer.apply(
            commandEvent(
                DEVICE_A,
                DeviceDosingRuntimeContract.Action.PROGRAM_APPLY,
                DeviceDosingRuntimeFixtures.programApply(),
                commandModule = "timer"
            )
        )
        val unknown = reducer.apply(commandEvent(DEVICE_A, "future.action", JSONObject()))

        assertTrue(wrongModule is DeviceDosingEventApplyResult.Malformed)
        assertEquals(DeviceDosingEventApplyResult.Ignored, unknown)
    }

    @Test
    fun `unsupported product ignores malformed status event before parsing`() {
        val store = DeviceDosingRuntimeStateStore()
        var refreshCalls = 0
        val reducer = DeviceDosingTypedEventReducer(
            stateStore = store,
            accessProvider = { DeviceDosingRuntimeAccess.UNAVAILABLE },
            refreshStatusChange = { _, _ -> refreshCalls++; true }
        )

        val malformed = DeviceDosingRuntimeFixtures.statusChange().put("legacyFullStatus", true)
        val result = reducer.apply(statusChangeEvent(DEVICE_A, 8L, malformed))

        assertEquals(DeviceDosingEventApplyResult.Ignored, result)
        assertEquals(0, refreshCalls)
        assertTrue(store.states.value.isEmpty())
    }

    private fun supportedReducer(
        store: DeviceDosingRuntimeStateStore,
        refresh: suspend (DeviceUid, JSONObject) -> Boolean
    ) = DeviceDosingTypedEventReducer(store, { SUPPORTED_ACCESS }, refresh)

    private fun statusChangeEvent(
        deviceUid: DeviceUid,
        revision: Long,
        data: JSONObject = DeviceDosingRuntimeFixtures.statusChange(revision)
    ) = DeviceRuntimeTypedEvent(
        deviceUid = deviceUid,
        generation = GENERATION,
        messageId = "evt-status-${deviceUid.value}-$revision",
        type = DeviceRuntimeTypedEvent.Type.DOSING_STATUS_CHANGED,
        payload = DeviceRuntimeEventPayload.Snapshot(data)
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
        val GENERATION = DeviceRuntimeConnectionGeneration(1L)
        val SUPPORTED_ACCESS = DeviceDosingRuntimeAccess(
            supportsApi = true,
            channelCount = 2,
            supportsProgramEditing = true,
            supportsChannelReset = true,
            supportsPrime = true,
            supportsManualDose = true,
            supportsCalibrationWorkflow = true,
            supportsReservoirRefill = true,
            supportsChannelDisplayName = true
        )
    }
}
