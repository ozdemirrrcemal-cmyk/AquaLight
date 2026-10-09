package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DEVICE_RUNTIME_DEFAULT_TIMEOUT_MILLIS
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandExecutor
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandSession
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCompletionDisposition
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.FixtureGateway
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.deviceUid
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.generationOne
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.isSuccess
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real command executor and pending registry; only the transport's response timing is faked. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceLightRefreshPreemptionTest {
    @Test
    fun `preempted firmware read unregisters before write and ignores its late reply`() = runTest {
        val fixture = ProtocolFixture()
        fixture.gateway.stalledAction = DeviceLightRuntimeContract.Action.GRAPH_GET
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { fixture.coordinator.refreshAll(deviceUid) }
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { fixture.coordinator.refreshAll(deviceUid) }
        assertEquals(1, fixture.gateway.executor.pendingCount())
        val stalled = checkNotNull(fixture.gateway.stalledCommand)
        val write = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.runtime.setControl(deviceUid, DeviceLightControlSetPayload(DeviceLightMode.MANUAL))
        }
        testScheduler.runCurrent()

        assertTrue(write.isCompleted)
        assertTrue(write.await() is DeviceRuntimeCommandOutcome.Success)
        assertEquals(0L, testScheduler.currentTime)
        assertEquals(0, fixture.gateway.executor.pendingCount())
        assertEquals(DeviceLightRuntimeRefreshResult.RejectedStale, refresh.await())
        assertEquals(DeviceLightRuntimeRefreshResult.RejectedStale, waiter.await())
        assertEquals(
            listOf(
                DeviceLightRuntimeContract.Action.STATUS_GET,
                DeviceLightRuntimeContract.Action.GRAPH_GET,
                DeviceLightRuntimeContract.Action.CONTROL_SET
            ),
            fixture.gateway.actions
        )
        assertEquals(DeviceRuntimeCompletionDisposition.DUPLICATE_OR_LATE, fixture.gateway.respond(stalled))
        assertNull(fixture.runtime.currentDashboard(deviceUid, DeviceLightDashboardReadAuthority.AUTHORITATIVE))
        fixture.gateway.stalledAction = null
        assertTrue(fixture.coordinator.refreshAll(deviceUid).isSuccess())
        assertEquals(0, fixture.gateway.executor.pendingCount())
    }

    @Test
    fun `pending real mutation ACK survives later refresh and mutation requests`() = runTest {
        val fixture = ProtocolFixture()
        assertTrue(fixture.coordinator.refreshAll(deviceUid).isSuccess())
        fixture.gateway.actions.clear()
        fixture.gateway.stalledAction = DeviceLightRuntimeContract.Action.CONTROL_SET
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.runtime.setControl(deviceUid, DeviceLightControlSetPayload(DeviceLightMode.MANUAL))
        }
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { fixture.coordinator.refreshAll(deviceUid) }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.thermal.applyConfig(
                deviceUid, DeviceLightThermalConfigApplyPayload(mode = DeviceLightThermalMode.AUTO)
            )
        }
        testScheduler.runCurrent()
        assertTrue(first.isActive)
        assertEquals(1, fixture.gateway.executor.pendingCount())
        assertEquals(listOf(DeviceLightRuntimeContract.Action.CONTROL_SET), fixture.gateway.actions)
        assertEquals(
            DeviceRuntimeCompletionDisposition.COMPLETED,
            fixture.gateway.respond(checkNotNull(fixture.gateway.stalledCommand))
        )

        assertTrue(first.await() is DeviceRuntimeCommandOutcome.Success)
        assertTrue(second.await() is DeviceRuntimeCommandOutcome.Success)
        assertEquals(DeviceLightRuntimeRefreshResult.RejectedStale, refresh.await())
        assertEquals(0, fixture.gateway.executor.pendingCount())
        assertEquals(
            listOf(DeviceLightRuntimeContract.Action.CONTROL_SET, DeviceLightThermalV1Contract.Action.CONFIG_APPLY),
            fixture.gateway.actions
        )
    }

    @Test
    fun `typed manual mutation retains inline readback without reentering shared mutex`() = runTest {
        val fixture = ProtocolFixture()
        assertTrue(fixture.coordinator.refreshAll(deviceUid).isSuccess())
        fixture.gateway.actions.clear()

        assertTrue(fixture.runtime.manualOff(deviceUid) is DeviceRuntimeCommandOutcome.Success)
        assertEquals(
            listOf(
                DeviceLightRuntimeContract.Action.MANUAL_OFF,
                DeviceLightRuntimeContract.Action.STATUS_GET,
                DeviceLightRuntimeContract.Action.GRAPH_GET
            ),
            fixture.gateway.actions
        )
        assertTrue(fixture.coordinator.refreshAll(deviceUid).isSuccess())
    }

    private class ProtocolFixture {
        val gateway = ProtocolGateway()
        private val owner = DeviceLightRuntimeStateOwner().apply { beginGeneration(deviceUid, generationOne) }
        private val gate = DeviceLightDeviceOperationGate()
        val runtime = DeviceLightRuntimeRepository(gateway, owner, gate)
        val thermal = DeviceLightThermalRuntimeRepository(gateway, owner, gate)
        private val protection = DeviceLightTemperatureProtectionRuntimeRepository(gateway, owner, gate)
        val coordinator = DeviceLightRuntimeRefreshCoordinator(runtime, thermal, protection)
    }

    private class ProtocolGateway : DeviceRuntimeCommandGateway {
        private val samples = FixtureGateway(generationOne, null, false, null)
        val actions = mutableListOf<String>()
        var stalledAction: String? = null
        var stalledCommand: AqlWsOutgoingMessage.Command? = null
        val executor = DeviceRuntimeCommandExecutor(
            sessionProvider = { uid -> DeviceRuntimeCommandSession(uid, generationOne, true, ::send) },
            supportChecker = { _, _, _ -> true }
        )

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            assertEquals(DEVICE_RUNTIME_DEFAULT_TIMEOUT_MILLIS, timeoutMillis)
            return executor.execute(deviceUid, command, timeoutMillis)
        }

        private fun send(message: AqlWsOutgoingMessage): Boolean {
            val command = message as AqlWsOutgoingMessage.Command
            actions += command.action
            if (command.action == stalledAction) {
                stalledCommand = command
            } else {
                respond(command)
            }
            return true
        }

        fun respond(command: AqlWsOutgoingMessage.Command): DeviceRuntimeCompletionDisposition = executor.complete(
            deviceUid,
            generationOne,
            AqlWsIncomingMessage.Response(
                id = command.id,
                type = "res",
                module = command.module,
                action = command.action,
                data = samples.responseData(command.action),
                ok = true,
                statusCode = HTTP_OK
            )
        )
    }

    private companion object {
        const val HTTP_OK = 200
    }
}
