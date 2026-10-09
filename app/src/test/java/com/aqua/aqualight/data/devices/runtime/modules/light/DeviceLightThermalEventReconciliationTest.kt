package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightBoundDispatchFixtures.Companion.G1
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightBoundDispatchFixtures.Companion.UID
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightThermalEventReconciliationTest {
    @Test
    fun `status snapshot hydrates thermal authority without extra transport`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.thermal.consume(statusEvent(DeviceRuntimeEventPayload.Snapshot(status())))
        assertTrue(fixture.thermal.isAuthoritative(UID, G1))
        assertEquals(2_000L, fixture.thermal.states.value[UID]?.status?.uptimeMs)
        assertTrue(fixture.sent.isEmpty())
    }

    @Test
    fun `config result event publishes its embedded thermal status`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.thermal.consume(statusEvent(commandResult(
            DeviceLightThermalV1Contract.Action.CONFIG_APPLY,
            DeviceLightRefreshTestFixtures.thermalConfigApply().put("status", status())
        )))
        assertEquals(2_000L, fixture.thermal.states.value[UID]?.status?.uptimeMs)
        assertTrue(fixture.sent.isEmpty())
    }

    @Test
    fun `malformed status snapshot hydrates with exact status request`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.thermal.consume(statusEvent(DeviceRuntimeEventPayload.Snapshot(JSONObject())))
        assertTrue(fixture.thermal.isAuthoritative(UID, G1))
        assertEquals(listOf(DeviceLightThermalV1Contract.Action.STATUS_GET), fixture.sent.map { it.action })
    }

    @Test
    fun `unknown command result falls back to authoritative thermal readback`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.thermal.consume(statusEvent(commandResult("unknown", JSONObject())))
        assertTrue(fixture.thermal.isAuthoritative(UID, G1))
        assertEquals(1, fixture.sent.size)
    }

    @Test
    fun `malformed config result cannot publish and triggers readback`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.thermal.consume(statusEvent(commandResult(
            DeviceLightThermalV1Contract.Action.CONFIG_APPLY, JSONObject()
        )))
        assertTrue(fixture.thermal.isAuthoritative(UID, G1))
        assertEquals(1, fixture.sent.size)
    }

    @Test
    fun `telemetry reconciles into hydrated status without polling`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.hydrate()
        fixture.thermal.consume(telemetryEvent(DeviceRuntimeEventPayload.Snapshot(telemetry())))
        assertEquals(2_000L, fixture.thermal.states.value[UID]?.telemetry?.uptimeMs)
        assertTrue(fixture.sent.isEmpty())
    }

    @Test
    fun `telemetry before initial status hydrates then reconciles same-generation sample`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.thermal.consume(telemetryEvent(DeviceRuntimeEventPayload.Snapshot(telemetry())))
        assertNotNull(fixture.thermal.states.value[UID]?.status)
        assertEquals(2_000L, fixture.thermal.states.value[UID]?.telemetry?.uptimeMs)
        assertEquals(1, fixture.sent.size)
    }

    @Test
    fun `telemetry bootstrap from old event cannot attach to replacement status`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.beforeCapture = fixture::replaceConnection
        fixture.thermal.consume(telemetryEvent(DeviceRuntimeEventPayload.Snapshot(telemetry())))
        assertNull(fixture.thermal.states.value[UID]?.telemetry)
        assertTrue(fixture.sent.isEmpty())
    }

    @Test
    fun `malformed telemetry is read back without publishing invalid sample`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.thermal.consume(telemetryEvent(DeviceRuntimeEventPayload.Snapshot(JSONObject())))
        assertNotNull(fixture.thermal.states.value[UID]?.status)
        assertNull(fixture.thermal.states.value[UID]?.telemetry)
        assertEquals(1, fixture.sent.size)
    }

    @Test
    fun `command-shaped telemetry triggers readback and keeps telemetry empty`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.thermal.consume(telemetryEvent(commandResult("unknown", JSONObject())))
        assertNotNull(fixture.thermal.states.value[UID]?.status)
        assertNull(fixture.thermal.states.value[UID]?.telemetry)
        assertEquals(1, fixture.sent.size)
    }

    @Test
    fun `unrelated domain event leaves thermal untouched`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.thermal.consume(statusEvent(DeviceRuntimeEventPayload.Snapshot(status())).copy(
            type = DeviceRuntimeTypedEvent.Type.LIGHT_STATUS_CHANGED
        ))
        assertTrue(fixture.thermal.states.value.isEmpty())
        assertTrue(fixture.sent.isEmpty())
    }

    private fun statusEvent(payload: DeviceRuntimeEventPayload) = DeviceRuntimeTypedEvent(
        UID, G1, "thermal-event", DeviceRuntimeTypedEvent.Type.LIGHT_THERMAL_STATUS_CHANGED, payload
    )

    private fun telemetryEvent(payload: DeviceRuntimeEventPayload) = statusEvent(payload).copy(
        type = DeviceRuntimeTypedEvent.Type.LIGHT_THERMAL_TELEMETRY_CHANGED
    )

    private fun commandResult(action: String, data: JSONObject) = DeviceRuntimeEventPayload.CommandResult(
        "command", DeviceLightRuntimeContract.MODULE, action, "session", 0L, data
    )

    private fun status() = DeviceLightRefreshTestFixtures.thermalStatus().put("uptimeMs", 2_000L)

    private fun telemetry(): JSONObject {
        val status = status()
        val fans = status.getJSONArray("fans")
        return JSONObject()
            .put("schema", status.getString("schema"))
            .put("schemaVersion", 1)
            .put("productKey", status.getString("productKey"))
            .put("uptimeMs", 2_000L)
            .put("mode", "Auto")
            .put("sensorFailSafeActive", false)
            .put("automaticOutputCycleHealthy", true)
            .put("temperature", status.getJSONObject("temperature"))
            .put("lightProtection", JSONObject().put("active", false).put("thresholdC", 60.0))
            .put("fans", JSONArray().also { result ->
                repeat(fans.length()) { index ->
                    val fan = fans.getJSONObject(index)
                    result.put(JSONObject()
                        .put("fanKey", fan.getString("fanKey"))
                        .put("regime", "Auto")
                        .put("percentNow", 25.0)
                        .put("percentAuto", 25.0)
                        .put("pwmOutputHealth", "OK")
                        .put("health", "UNVERIFIED")
                        .put("physicalFeedbackAvailable", false))
                }
            })
    }
}
