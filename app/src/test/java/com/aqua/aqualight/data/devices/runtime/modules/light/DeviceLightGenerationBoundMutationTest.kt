package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightBoundDispatchFixtures.Companion.CONFIG
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightBoundDispatchFixtures.Companion.G1
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightBoundDispatchFixtures.Companion.G2
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightBoundDispatchFixtures.Companion.UID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightGenerationBoundMutationTest {
    @Test
    fun `Light reconnect after repository validation cannot dispatch against new session`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.hydrate()
        fixture.beforeCapture = fixture::replaceConnection
        val outcome = fixture.light.setControl(UID, DeviceLightControlSetPayload(DeviceLightMode.AUTO))

        assertTrue(outcome is DeviceRuntimeCommandOutcome.Cancelled)
        assertEquals(listOf(G1), fixture.expectedGenerations)
        assertTrue(fixture.sent.isEmpty())
        assertEquals(0, fixture.executor.pendingCount())
    }

    @Test
    fun `thermal reconnect after repository validation cannot dispatch against new session`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.hydrate()
        fixture.beforeCapture = fixture::replaceConnection
        val outcome = fixture.thermal.applyConfig(UID, CONFIG)

        assertTrue(outcome is DeviceRuntimeCommandOutcome.Cancelled)
        assertEquals(listOf(G1), fixture.expectedGenerations)
        assertTrue(fixture.sent.isEmpty())
    }

    @Test
    fun `thermal writes fail closed without a known connection generation`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures(beginGeneration = false)
        assertTrue(fixture.thermal.applyConfig(UID, CONFIG) is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        assertTrue(fixture.expectedGenerations.isEmpty())
        assertTrue(fixture.sent.isEmpty())
    }

    @Test
    fun `thermal writes require hydrated authority for the known connection`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        assertTrue(fixture.thermal.applyConfig(UID, CONFIG) is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        assertTrue(fixture.expectedGenerations.isEmpty())
        assertFalse(fixture.thermal.isAuthoritative(UID, G1))
    }

    @Test
    fun `thermal bootstrap read can establish first generation before any write`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures(beginGeneration = false)
        val read = fixture.thermal.requestStatus(UID)
        assertTrue(read is DeviceRuntimeCommandOutcome.Success)
        assertEquals(listOf(null), fixture.expectedGenerations)
        assertTrue(fixture.thermal.isAuthoritative(UID, G1))
        assertTrue(fixture.thermal.applyConfig(UID, CONFIG) is DeviceRuntimeCommandOutcome.Success)
        assertEquals(listOf(null, G1), fixture.expectedGenerations)
    }

    @Test
    fun `successful thermal mutation publishes status from matching generation`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.hydrate()
        fixture.responseData = { action ->
            DeviceLightBoundDispatchFixtures.defaultResponse(action).also { data ->
                data.getJSONObject("status").getJSONObject("config").put("minTemperatureC", 32.0)
            }
        }
        val outcome = fixture.thermal.applyConfig(UID, CONFIG)
        assertTrue(outcome is DeviceRuntimeCommandOutcome.Success)
        assertEquals(32.0, fixture.thermal.states.value[UID]?.status?.config?.minTemperatureC)
        assertEquals(listOf(G1), fixture.expectedGenerations)
        assertTrue(fixture.thermal.isAuthoritative(UID, G1))
    }

    @Test
    fun `unsupported thermal command leaves authoritative state untouched`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.hydrate()
        val before = fixture.thermal.states.value[UID]
        fixture.supported = false
        assertTrue(fixture.thermal.applyConfig(UID, CONFIG) is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        assertEquals(before, fixture.thermal.states.value[UID])
        assertTrue(fixture.sent.isEmpty())
    }

    @Test
    fun `late old thermal success neither overwrites replacement nor starts replacement readback`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.hydrate()
        fixture.automaticReply = false
        val mutation = async(start = CoroutineStart.UNDISPATCHED) { fixture.thermal.applyConfig(UID, CONFIG) }
        val oldCommand = fixture.sent.single()
        fixture.replaceConnection()
        val replacement = fixture.thermal.states.value[UID]
        fixture.responseData = { DeviceLightRefreshTestFixtures.thermalConfigApply().also { data ->
            data.getJSONObject("status").getJSONObject("config").put("minTemperatureC", 32.0)
        } }
        fixture.reply(oldCommand, G1)

        val outcome = mutation.await() as DeviceRuntimeCommandOutcome.Success
        assertEquals(G1, outcome.generation)
        assertEquals(replacement, fixture.thermal.states.value[UID])
        assertTrue(fixture.thermal.isAuthoritative(UID, G2))
        assertEquals(1, fixture.sent.size)
    }

    @Test
    fun `malformed thermal mutation success preserves last authoritative status`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.hydrate()
        val before = fixture.thermal.states.value[UID]
        fixture.responseData = { JSONObject() }
        assertTrue(fixture.thermal.applyConfig(UID, CONFIG) is DeviceRuntimeCommandOutcome.ProtocolError)
        assertEquals(before, fixture.thermal.states.value[UID])
    }

    @Test
    fun `failed bootstrap read cannot create thermal authority`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.supported = false
        assertTrue(fixture.thermal.requestStatus(UID) is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        assertNull(fixture.thermal.states.value[UID])
        assertFalse(fixture.thermal.isAuthoritative(UID, G1))
    }

    @Test
    fun `late old Light manual ACK does not refresh the replacement connection`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.hydrate()
        fixture.automaticReply = false
        val mutation = async(start = CoroutineStart.UNDISPATCHED) { fixture.light.manualOff(UID) }
        val command = fixture.sent.single()
        fixture.replaceConnection()
        fixture.reply(command, G1)
        assertTrue(mutation.await() is DeviceRuntimeCommandOutcome.Success)
        assertEquals(1, fixture.sent.size)
    }

    @Test
    fun `thermal ACK cannot restore revoked authority or trigger readback`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.hydrate()
        fixture.automaticReply = false
        val mutation = async(start = CoroutineStart.UNDISPATCHED) { fixture.thermal.applyConfig(UID, CONFIG) }
        val command = fixture.sent.single()
        val before = fixture.thermal.states.value[UID]
        fixture.owner.invalidate(UID, G1)
        fixture.reply(command, G1)

        assertTrue(mutation.await() is DeviceRuntimeCommandOutcome.Success)
        assertFalse(fixture.thermal.isAuthoritative(UID, G1))
        assertEquals(before, fixture.thermal.states.value[UID])
        assertEquals(1, fixture.sent.size)
    }

    @Test
    fun `thermal ACK cannot recreate cleared state or trigger readback`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.hydrate()
        fixture.automaticReply = false
        val mutation = async(start = CoroutineStart.UNDISPATCHED) { fixture.thermal.applyConfig(UID, CONFIG) }
        val command = fixture.sent.single()
        fixture.owner.clear(UID)
        fixture.reply(command, G1)

        assertTrue(mutation.await() is DeviceRuntimeCommandOutcome.Success)
        assertFalse(fixture.thermal.isAuthoritative(UID, G1))
        assertNull(fixture.thermal.states.value[UID])
        assertEquals(1, fixture.sent.size)
    }

    @Test
    fun `older thermal ACK reads back only while current authority remains valid`() = runTest {
        val fixture = DeviceLightBoundDispatchFixtures()
        fixture.hydrate()
        fixture.owner.recordThermalStatus(UID, G1, checkNotNull(fixture.thermal.states.value[UID]?.status).copy(
            uptimeMs = 2_000L
        ))
        fixture.responseData = { action ->
            DeviceLightBoundDispatchFixtures.defaultResponse(action).also { data ->
                if (action == DeviceLightThermalV1Contract.Action.STATUS_GET) data.put("uptimeMs", 3_000L)
            }
        }
        assertTrue(fixture.thermal.applyConfig(UID, CONFIG) is DeviceRuntimeCommandOutcome.Success)
        assertEquals(3_000L, fixture.thermal.states.value[UID]?.status?.uptimeMs)
        assertEquals(listOf(
            DeviceLightThermalV1Contract.Action.CONFIG_APPLY,
            DeviceLightThermalV1Contract.Action.STATUS_GET
        ), fixture.sent.map { it.action })
    }

}
