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
    fun `direct status change applies runtime delta without replacing config snapshot`() {
        val store = seededStore(DEVICE_A)
        val reducer = supportedReducer(store)

        val result = reducer.apply(statusEvent(DEVICE_A, sequence = TIMER_TEST_EVENT_SEQUENCE))
        val state = store.states.value.getValue(DEVICE_A)
        val channel = state.status?.channels?.first()

        assertEquals(DeviceTimerEventApplyResult.Applied, result)
        assertEquals(TIMER_TEST_EVENT_SEQUENCE, state.lastEventSequence)
        assertEquals(DeviceTimerOperatingState.OFF, channel?.operatingState)
        assertEquals(DeviceTimerRuntimeReason.TEMPORARY_OVERRIDE_OFF, channel?.runtimeReason)
        assertEquals(DeviceTimerRegime.AUTO, channel?.regime)
        assertEquals(false, state.requiresStatusRefresh)
    }

    @Test
    fun `duplicate and older global event sequences are ignored`() {
        val store = seededStore(DEVICE_A)
        val reducer = supportedReducer(store)
        reducer.apply(statusEvent(DEVICE_A, sequence = TIMER_TEST_EVENT_SEQUENCE))

        val duplicate = reducer.apply(statusEvent(DEVICE_A, sequence = TIMER_TEST_EVENT_SEQUENCE))
        val older = reducer.apply(statusEvent(DEVICE_A, sequence = TIMER_TEST_OLDER_EVENT_SEQUENCE))

        assertEquals(DeviceTimerEventApplyResult.Ignored, duplicate)
        assertEquals(DeviceTimerEventApplyResult.Ignored, older)
        assertEquals(TIMER_TEST_EVENT_SEQUENCE, store.states.value.getValue(DEVICE_A).lastEventSequence)
    }

    @Test
    fun `global event sequence gap requests scoped recovery`() {
        val store = seededStore(DEVICE_A)
        val reducer = supportedReducer(store)
        reducer.apply(statusEvent(DEVICE_A, sequence = TIMER_TEST_EVENT_SEQUENCE))

        val result = reducer.apply(statusEvent(DEVICE_A, sequence = TIMER_TEST_GAPPED_EVENT_SEQUENCE))

        assertEquals(DeviceTimerEventApplyResult.RefreshRequired("channel1"), result)
        assertTrue(store.states.value.getValue(DEVICE_A).requiresStatusRefresh)
        assertEquals(TIMER_TEST_GAPPED_EVENT_SEQUENCE, store.states.value.getValue(DEVICE_A).lastEventSequence)
    }

    @Test
    fun `event revision mismatch requests scoped recovery`() {
        val store = seededStore(DEVICE_A)
        val reducer = supportedReducer(store)

        val result = reducer.apply(
            statusEvent(
                DEVICE_A,
                sequence = TIMER_TEST_EVENT_SEQUENCE,
                revision = TIMER_TEST_RECOVERY_REVISION
            )
        )

        assertEquals(DeviceTimerEventApplyResult.RefreshRequired("channel1"), result)
        assertTrue(store.states.value.getValue(DEVICE_A).requiresStatusRefresh)
    }

    @Test
    fun `event sequence wrap skips zero and remains ordered`() {
        val store = seededStore(DEVICE_A)
        val reducer = supportedReducer(store)

        assertEquals(
            DeviceTimerEventApplyResult.Applied,
            reducer.apply(statusEvent(DEVICE_A, sequence = TIMER_TEST_UINT32_MAX))
        )
        assertEquals(
            DeviceTimerEventApplyResult.Applied,
            reducer.apply(statusEvent(DEVICE_A, sequence = 1L))
        )
        assertEquals(1L, store.states.value.getValue(DEVICE_A).lastEventSequence)
    }

    @Test
    fun `same global sequence remains isolated by device`() {
        val store = DeviceTimerRuntimeStateStore()
        seed(store, DEVICE_A)
        seed(store, DEVICE_B)
        val reducer = supportedReducer(store)

        reducer.apply(statusEvent(DEVICE_A, sequence = TIMER_TEST_EVENT_SEQUENCE))

        assertEquals(TIMER_TEST_EVENT_SEQUENCE, store.states.value.getValue(DEVICE_A).lastEventSequence)
        assertEquals(null, store.states.value.getValue(DEVICE_B).lastEventSequence)
    }

    @Test
    fun `command-result envelope is ignored because firmware event is direct`() {
        val store = seededStore(DEVICE_A)
        val reducer = supportedReducer(store)
        val event = statusEvent(DEVICE_A, TIMER_TEST_EVENT_SEQUENCE).copy(
            payload = DeviceRuntimeEventPayload.CommandResult(
                commandId = "cmd-1",
                commandModule = DeviceTimerRuntimeContract.MODULE,
                commandAction = DeviceTimerRuntimeContract.Action.CONFIG_APPLY,
                sessionId = "session-1",
                publishedAtMillis = TIMER_TEST_PUBLISHED_AT_MILLIS,
                result = DeviceTimerRuntimeFixtures.configApply()
            )
        )

        assertEquals(DeviceTimerEventApplyResult.Ignored, reducer.apply(event))
    }

    @Test
    fun `malformed direct event fails closed and unavailable Timer is ignored`() {
        val malformedStore = seededStore(DEVICE_A)
        val malformed = statusEvent(DEVICE_A, TIMER_TEST_EVENT_SEQUENCE).copy(
            payload = DeviceRuntimeEventPayload.Snapshot(
                DeviceTimerRuntimeFixtures.statusChanged().put("unexpected", true)
            )
        )
        val unavailableStore = DeviceTimerRuntimeStateStore()
        val unavailableReducer = DeviceTimerTypedEventReducer(unavailableStore) {
            DeviceTimerRuntimeAccess.UNAVAILABLE
        }

        assertTrue(supportedReducer(malformedStore).apply(malformed) is
            DeviceTimerEventApplyResult.Malformed)
        assertEquals(
            DeviceTimerEventApplyResult.Ignored,
            unavailableReducer.apply(statusEvent(DEVICE_B, TIMER_TEST_EVENT_SEQUENCE))
        )
        assertTrue(unavailableStore.states.value.isEmpty())
    }

}

private fun supportedReducer(
    store: DeviceTimerRuntimeStateStore
) = DeviceTimerTypedEventReducer(store) { SUPPORTED_ACCESS }

private fun seededStore(deviceUid: DeviceUid): DeviceTimerRuntimeStateStore =
    DeviceTimerRuntimeStateStore().also { store -> seed(store, deviceUid) }

private fun seed(store: DeviceTimerRuntimeStateStore, deviceUid: DeviceUid) {
    store.beginGeneration(deviceUid, GENERATION)
    check(
        store.recordStatus(
            deviceUid,
            GENERATION,
            DeviceTimerStatusParser.parse(
                DeviceTimerRuntimeFixtures.globalStatus(revision = TIMER_TEST_APPLIED_REVISION)
            )
        )
    )
}

private fun statusEvent(
    deviceUid: DeviceUid,
    sequence: Long,
    revision: Long = TIMER_TEST_APPLIED_REVISION
) = DeviceRuntimeTypedEvent(
    deviceUid = deviceUid,
    generation = GENERATION,
    messageId = "evt-" + deviceUid.value + "-" + sequence,
    type = DeviceRuntimeTypedEvent.Type.TIMER_STATUS_CHANGED,
    payload = DeviceRuntimeEventPayload.Snapshot(
        DeviceTimerRuntimeFixtures.statusChanged(
            sequence = sequence,
            revision = revision
        )
    )
)

private val DEVICE_A = DeviceUid("AQL-TIMER-A")
private val DEVICE_B = DeviceUid("AQL-TIMER-B")
private val GENERATION = DeviceRuntimeConnectionGeneration(1L)
private val SUPPORTED_ACCESS = DeviceTimerRuntimeAccess(
    supportsApi = true,
    channelCount = TIMER_TEST_CHANNEL_COUNT,
    supportsSchedules = true,
    supportsChannelState = true,
    supportsChannelDisplayName = true
)
