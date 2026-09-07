package com.aqua.aqualight.data.devices.runtime.events

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeEventRouterTest {

    @Test
    fun `all firmware events route to exact typed state entries`() = runBlocking {
        val router = DeviceRuntimeEventRouter()
        router.activate(DEVICE_A, GENERATION_ONE)

        DeviceRuntimeTypedEvent.Type.values().forEachIndexed { index, type ->
            val result = router.route(
                deviceUid = DEVICE_A,
                generation = GENERATION_ONE,
                message = event(
                    id = "evt-$index",
                    type = type,
                    data = JSONObject().put("value", index)
                )
            )
            assertTrue(result is DeviceRuntimeEventRoutingResult.Routed)
            val routed = (result as DeviceRuntimeEventRoutingResult.Routed).event
            assertEquals(type, routed.type)
            assertEquals(index, (routed.payload as DeviceRuntimeEventPayload.Snapshot).data.getInt("value"))
        }

        assertEquals(13, router.states.value.getValue(DEVICE_A).size)
    }

    @Test
    fun `command event envelope is validated and defensively copied`() = runBlocking {
        val router = DeviceRuntimeEventRouter()
        router.activate(DEVICE_A, GENERATION_ONE)
        val commandResult = JSONObject()
            .put("event", "light.status.changed")
            .put("changed", true)
        val data = JSONObject()
            .put("commandId", "cmd-1")
            .put("module", AqlWsContract.MODULE_LIGHT)
            .put("action", AqlWsContract.ACTION_LIGHT_MANUAL_SET)
            .put("sessionId", "session-1")
            .put("publishedAtMs", 42L)
            .put("result", commandResult)

        val routed = router.route(
            DEVICE_A,
            GENERATION_ONE,
            event("evt-command", DeviceRuntimeTypedEvent.Type.LIGHT_STATUS_CHANGED, data)
        ) as DeviceRuntimeEventRoutingResult.Routed
        val payload = routed.event.payload as DeviceRuntimeEventPayload.CommandResult

        commandResult.put("changed", false)
        assertEquals("cmd-1", payload.commandId)
        assertEquals(AqlWsContract.MODULE_LIGHT, payload.commandModule)
        assertEquals(AqlWsContract.ACTION_LIGHT_MANUAL_SET, payload.commandAction)
        assertEquals("session-1", payload.sessionId)
        assertEquals(42L, payload.publishedAtMillis)
        assertTrue(payload.result.getBoolean("changed"))
    }

    @Test
    fun `partial command envelope is rejected instead of downgraded to snapshot`() = runBlocking {
        val router = DeviceRuntimeEventRouter()
        router.activate(DEVICE_A, GENERATION_ONE)

        val result = router.route(
            DEVICE_A,
            GENERATION_ONE,
            event(
                id = "evt-malformed",
                type = DeviceRuntimeTypedEvent.Type.DEVICE_STATUS_CHANGED,
                data = JSONObject().put("commandId", "cmd-only")
            )
        )

        assertEquals(
            DeviceRuntimeEventRoutingResult.Malformed("data"),
            result
        )
        assertNull(router.states.value[DEVICE_A])
    }

    @Test
    fun `generation replacement clears state and rejects stale event`() = runBlocking {
        val router = DeviceRuntimeEventRouter()
        router.activate(DEVICE_A, GENERATION_ONE)
        router.route(
            DEVICE_A,
            GENERATION_ONE,
            event(
                "evt-current",
                DeviceRuntimeTypedEvent.Type.NETWORK_STATE_CHANGED,
                JSONObject().put("connected", true)
            )
        )

        router.activate(DEVICE_A, GENERATION_TWO)
        val stale = router.route(
            DEVICE_A,
            GENERATION_ONE,
            event(
                "evt-stale",
                DeviceRuntimeTypedEvent.Type.NETWORK_STATE_CHANGED,
                JSONObject().put("connected", false)
            )
        )

        assertEquals(
            DeviceRuntimeEventRoutingResult.Stale(GENERATION_TWO, GENERATION_ONE),
            stale
        )
        assertNull(router.states.value[DEVICE_A])
    }

    @Test
    fun `same message id remains isolated across devices`() = runBlocking {
        val router = DeviceRuntimeEventRouter()
        router.activate(DEVICE_A, GENERATION_ONE)
        router.activate(DEVICE_B, GENERATION_ONE)

        val first = router.route(
            DEVICE_A,
            GENERATION_ONE,
            event(
                "evt-shared",
                DeviceRuntimeTypedEvent.Type.COOLING_TELEMETRY_CHANGED,
                JSONObject().put("celsius", 24.5)
            )
        )
        val second = router.route(
            DEVICE_B,
            GENERATION_ONE,
            event(
                "evt-shared",
                DeviceRuntimeTypedEvent.Type.COOLING_TELEMETRY_CHANGED,
                JSONObject().put("celsius", 26.0)
            )
        )

        assertTrue(first is DeviceRuntimeEventRoutingResult.Routed)
        assertTrue(second is DeviceRuntimeEventRoutingResult.Routed)
        assertEquals(2, router.states.value.size)
        assertFalse(router.states.value.getValue(DEVICE_A) === router.states.value.getValue(DEVICE_B))
    }

    @Test
    fun `unknown firmware event remains unmatched and does not mutate state`() = runBlocking {
        val router = DeviceRuntimeEventRouter()
        router.activate(DEVICE_A, GENERATION_ONE)
        val unknown = AqlWsIncomingMessage.Event(
            id = "evt-unknown",
            type = AqlWsContract.TYPE_EVENT,
            module = "future",
            action = "status.changed",
            data = JSONObject()
        )

        assertEquals(
            DeviceRuntimeEventRoutingResult.Unmatched("future", "status.changed"),
            router.route(DEVICE_A, GENERATION_ONE, unknown)
        )
        assertTrue(router.states.value.isEmpty())
    }

    private fun event(
        id: String,
        type: DeviceRuntimeTypedEvent.Type,
        data: JSONObject
    ): AqlWsIncomingMessage.Event = AqlWsIncomingMessage.Event(
        id = id,
        type = AqlWsContract.TYPE_EVENT,
        module = type.module,
        action = type.action,
        data = data
    )

    private companion object {
        val DEVICE_A = DeviceUid("AQL-EVENT-A")
        val DEVICE_B = DeviceUid("AQL-EVENT-B")
        val GENERATION_ONE = DeviceRuntimeConnectionGeneration(1L)
        val GENERATION_TWO = DeviceRuntimeConnectionGeneration(2L)
    }
}
