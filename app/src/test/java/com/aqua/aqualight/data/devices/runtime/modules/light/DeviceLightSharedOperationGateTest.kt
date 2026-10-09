package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DEVICE_RUNTIME_DEFAULT_TIMEOUT_MILLIS
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.RefreshFixture
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.deviceUid
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.fixture
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.generationOne
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.generationTwo
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.isSuccess
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.lightMutationActions
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.status
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.temperatureProtectionStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.thermalStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses one injected gate across all three production facades, including each Light write action. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceLightSharedOperationGateTest {
    @Test
    fun `every mutation dispatches after stalled optional refresh is cancelled without advancing time`() = runTest {
        for (action in mutationActions) {
            val fixture = fixture(generationOne)
            assertTrue(fixture.coordinator.refreshAll(deviceUid).isSuccess())
            fixture.gateway.actions.clear()
            var refreshCancelled = false
            fixture.gateway.beforeResponse = { _, readAction, timeout ->
                if (readAction == DeviceLightRuntimeContract.Action.GRAPH_GET) {
                    assertEquals(DEVICE_RUNTIME_DEFAULT_TIMEOUT_MILLIS, timeout)
                    try {
                        delay(timeout)
                    } finally {
                        refreshCancelled = true
                    }
                }
            }
            val refresh = async(start = CoroutineStart.UNDISPATCHED) { fixture.coordinator.refreshAll(deviceUid) }
            val startedAt = testScheduler.currentTime
            val write = async(start = CoroutineStart.UNDISPATCHED) { fixture.write(action) }
            // Software bound: a runnable cooperative read is cancelled and the queued write is
            // dispatched in the current scheduler turn, with no timeout/timer advance. This does
            // not measure a Bluetooth/Wi-Fi round trip or physical light response.
            testScheduler.runCurrent()

            assertTrue("Write did not finish for $action", write.isCompleted)
            assertTrue("Write was not acknowledged for $action", write.await() is DeviceRuntimeCommandOutcome.Success)
            assertTrue(refreshCancelled)
            assertEquals(startedAt, testScheduler.currentTime)
            assertEquals(DeviceLightRuntimeRefreshResult.RejectedStale, refresh.await())
            assertEquals(action, fixture.gateway.actions.last())
            assertTrue(currentCoroutineContext().isActive)
            fixture.gateway.beforeResponse = { _, _, _ -> }
            assertTrue(fixture.coordinator.refreshAll(deviceUid).isSuccess())
        }
    }

    @Test
    fun `generation and committed readback flights also yield to a newer write`() = runTest {
        for (committed in listOf(false, true)) {
            val fixture = fixture(generationOne)
            assertTrue(fixture.coordinator.refreshAll(deviceUid).isSuccess())
            assertTrue(fixture.runtime.requestStatus(deviceUid) is DeviceRuntimeCommandOutcome.Success)
            fixture.gateway.responseGates[DeviceLightRuntimeContract.Action.GRAPH_GET] = CompletableDeferred()
            val refresh = async(start = CoroutineStart.UNDISPATCHED) {
                if (committed) {
                    fixture.coordinator.reconcileCommitted(deviceUid, DeviceLightMode.MANUAL, generationOne)
                } else {
                    fixture.coordinator.refreshGeneration(deviceUid, generationOne)
                }
            }

            assertTrue(
                fixture.write(DeviceLightRuntimeContract.Action.CONTROL_SET) is DeviceRuntimeCommandOutcome.Success
            )
            assertEquals(DeviceLightRuntimeRefreshResult.RejectedStale, refresh.await())
            fixture.gateway.responseGates.clear()
            assertTrue(fixture.coordinator.refreshAll(deviceUid).isSuccess())
        }
    }

    @Test
    fun `superseded coalesced flight settles all waiters and next refresh recovers`() = runTest {
        val fixture = fixture(generationOne)
        fixture.seedAuthority(deviceUid, generationOne)
        val readGate = CompletableDeferred<Unit>()
        fixture.gateway.responseGates[DeviceLightRuntimeContract.Action.GRAPH_GET] = readGate
        val owner = async(start = CoroutineStart.UNDISPATCHED) { fixture.coordinator.refreshAll(deviceUid) }
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { fixture.coordinator.refreshAll(deviceUid) }
        val cancelledWaiter = async(start = CoroutineStart.UNDISPATCHED) { fixture.coordinator.refreshAll(deviceUid) }
        cancelledWaiter.cancelAndJoin()
        assertTrue(owner.isActive)
        assertTrue(waiter.isActive)

        assertTrue(fixture.write(DeviceLightRuntimeContract.Action.MANUAL_OFF) is DeviceRuntimeCommandOutcome.Success)
        assertEquals(DeviceLightRuntimeRefreshResult.RejectedStale, owner.await())
        assertEquals(DeviceLightRuntimeRefreshResult.RejectedStale, waiter.await())
        assertTrue(cancelledWaiter.isCancelled)
        fixture.gateway.responseGates.clear()
        assertTrue(fixture.coordinator.refreshAll(deviceUid).isSuccess())
    }

    @Test
    fun `caller cancellation settles coalesced waiters and subsequent refresh recovers`() = runTest {
        val fixture = fixture(generationOne)
        val readGate = CompletableDeferred<Unit>()
        fixture.gateway.responseGates[DeviceLightRuntimeContract.Action.STATUS_GET] = readGate
        val owner = async(start = CoroutineStart.UNDISPATCHED) { fixture.coordinator.refreshAll(deviceUid) }
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { fixture.coordinator.refreshAll(deviceUid) }
        owner.cancelAndJoin()

        assertTrue(owner.isCancelled)
        assertEquals(DeviceLightRuntimeRefreshResult.RejectedStale, waiter.await())
        fixture.gateway.responseGates.clear()
        assertTrue(fixture.coordinator.refreshAll(deviceUid).isSuccess())
    }

    @Test
    fun `all queued old-generation mutations reject even after replacement authority is hydrated`() = runTest {
        val fixture = fixture(generationOne)
        fixture.seedAuthority(deviceUid, generationOne)
        val release = CompletableDeferred<Unit>()
        val owner = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.operationGate.withDevice(deviceUid) { release.await() }
        }
        val writes = mutationActions.map { action ->
            async(start = CoroutineStart.UNDISPATCHED) { fixture.write(action) }
        }
        fixture.owner.invalidate(deviceUid, generationOne)
        fixture.owner.beginGeneration(deviceUid, generationTwo)
        fixture.gateway.generation = generationTwo
        fixture.seedAuthority(deviceUid, generationTwo)
        release.complete(Unit)
        owner.await()

        writes.forEach { assertTrue(it.await() is DeviceRuntimeCommandOutcome.UnsupportedByDevice) }
        assertTrue(fixture.gateway.actions.isEmpty())
        assertTrue(fixture.write(DeviceLightRuntimeContract.Action.MANUAL_OFF) is DeviceRuntimeCommandOutcome.Success)
    }

    @Test
    fun `cancelling all queued mutation kinds sends no command and releases priority`() = runTest {
        val fixture = fixture(generationOne)
        fixture.seedAuthority(deviceUid, generationOne)
        val release = CompletableDeferred<Unit>()
        val owner = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.operationGate.withDevice(deviceUid) { release.await() }
        }
        val writes = mutationActions.map { action ->
            async(start = CoroutineStart.UNDISPATCHED) { fixture.write(action) }
        }
        writes.forEach { it.cancelAndJoin() }
        release.complete(Unit)
        owner.await()

        assertTrue(fixture.gateway.actions.isEmpty())
        assertTrue(fixture.coordinator.refreshAll(deviceUid).isSuccess())
    }

    @Test
    fun `new writes never cancel any in-flight mutation ACK and queued refresh cannot overtake`() = runTest {
        for (action in mutationActions) {
            val fixture = fixture(generationOne)
            fixture.seedAuthority(deviceUid, generationOne)
            val ackGate = CompletableDeferred<Unit>()
            fixture.gateway.responseGates[action] = ackGate
            val first = async(start = CoroutineStart.UNDISPATCHED) { fixture.write(action) }
            val refresh = async(start = CoroutineStart.UNDISPATCHED) { fixture.coordinator.refreshAll(deviceUid) }
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                fixture.write(DeviceLightRuntimeContract.Action.CONTROL_SET)
            }
            testScheduler.runCurrent()
            assertTrue(first.isActive)
            assertFalse(second.isCompleted)
            assertEquals(listOf(action), fixture.gateway.actions)
            ackGate.complete(Unit)

            assertTrue(first.await() is DeviceRuntimeCommandOutcome.Success)
            assertEquals(DeviceLightRuntimeRefreshResult.RejectedStale, refresh.await())
            assertTrue(second.await() is DeviceRuntimeCommandOutcome.Success)
            assertEquals(listOf(action, DeviceLightRuntimeContract.Action.CONTROL_SET), fixture.gateway.actions)
        }
    }

    @Test
    fun `unrelated device read and write progress while another device refresh is stalled`() = runTest {
        val fixture = fixture(generationOne)
        fixture.seedAuthority(deviceUid, generationOne)
        val otherDevice = DeviceUid("independent-light")
        fixture.owner.beginGeneration(otherDevice, generationOne)
        fixture.seedAuthority(otherDevice, generationOne)
        val readGate = CompletableDeferred<Unit>()
        fixture.gateway.beforeResponse = { uid, action, _ ->
            if (uid == deviceUid && action == DeviceLightRuntimeContract.Action.GRAPH_GET) readGate.await()
        }
        val stalled = async(start = CoroutineStart.UNDISPATCHED) { fixture.coordinator.refreshAll(deviceUid) }
        assertTrue(
            fixture.write(DeviceLightRuntimeContract.Action.MANUAL_OFF, otherDevice) is
                DeviceRuntimeCommandOutcome.Success
        )
        assertTrue(fixture.coordinator.refreshAll(otherDevice).isSuccess())
        assertTrue(stalled.isActive)
        readGate.complete(Unit)
        assertTrue(stalled.await().isSuccess())
    }

    private suspend fun RefreshFixture.write(
        action: String,
        uid: DeviceUid = deviceUid
    ): DeviceRuntimeCommandOutcome<*> = when (action) {
        DeviceLightThermalV1Contract.Action.CONFIG_APPLY ->
            thermal.applyConfig(uid, DeviceLightThermalConfigApplyPayload(mode = DeviceLightThermalMode.AUTO))
        DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_SET ->
            protection.setThreshold(uid, DeviceLightTemperatureProtectionSetPayload(thresholdC = 60.0))
        else -> runtime.productCommand(uid, action, parser = { _, _ -> Unit })
    }

    private fun RefreshFixture.seedAuthority(uid: DeviceUid, generation: DeviceRuntimeConnectionGeneration) {
        assertTrue(owner.recordStatus(uid, generation, DeviceLightStatusParser.parse(status(false))))
        val thermal = DeviceLightThermalV1ResponseParser.parseStatus(thermalStatus())
        assertTrue(owner.recordThermalStatus(uid, generation, thermal))
        val protection = DeviceLightTemperatureProtectionParser.parseStatus(temperatureProtectionStatus()).getOrThrow()
        assertTrue(owner.recordTemperatureProtection(uid, generation, protection))
    }

    private companion object {
        val mutationActions = lightMutationActions + listOf(
            DeviceLightThermalV1Contract.Action.CONFIG_APPLY,
            DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_SET
        )
    }
}
