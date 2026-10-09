package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.currentAuthoritativeSurface
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.deviceUid
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.expectedAllRefreshActions
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.expectedManagedPlanRefreshActions
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.expectedRefreshActions
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.fixture
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.generationOne
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.generationTwo
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.isSuccess
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRefreshTestFixtures.status
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightRuntimeRefreshCoordinatorTest {

    @Test
    fun `reconnect retains presentation and central refresh restores full Light authority`() =
        runTest {
            val fixture = fixture(generationOne)
            assertTrue(fixture.coordinator.refreshGeneration(deviceUid, generationOne).isSuccess())

            assertNotNull(fixture.runtime.currentAuthoritativeSurface())
            fixture.owner.invalidate(deviceUid, generationOne)
            fixture.owner.beginGeneration(deviceUid, generationTwo)

            assertNotNull(
                fixture.runtime.currentLibrary(
                    deviceUid,
                    DeviceLightLibraryReadAuthority.PRESENTATION
                )
            )
            assertNull(fixture.runtime.currentAuthoritativeSurface())

            fixture.gateway.generation = generationTwo
            assertTrue(fixture.coordinator.refreshGeneration(deviceUid, generationTwo).isSuccess())
            assertNotNull(fixture.runtime.currentAuthoritativeSurface())
            assertEquals(expectedRefreshActions + expectedRefreshActions, fixture.gateway.actions)
        }

    @Test
    fun `bootstrap requests only status and full Light hydration remains on demand`() = runTest {
        val fixture = fixture(generationOne)
        assertTrue(
            fixture.coordinator.hydrateStatus(deviceUid, generationOne) is
                DeviceLightRuntimeBootstrapResult.Hydrated
        )
        assertEquals(listOf(DeviceLightRuntimeContract.Action.STATUS_GET), fixture.gateway.actions)
        assertNotNull(fixture.runtime.currentStatus(deviceUid))
        assertNull(
            fixture.runtime.currentDashboard(
                deviceUid, DeviceLightDashboardReadAuthority.AUTHORITATIVE
            )
        )
        assertNull(
            fixture.runtime.currentSystem(deviceUid, DeviceLightSystemReadAuthority.AUTHORITATIVE)
        )

        assertTrue(fixture.coordinator.refreshAll(deviceUid).isSuccess())
        assertNotNull(fixture.runtime.currentAuthoritativeSurface())
        assertNotNull(
            fixture.runtime.currentSystem(deviceUid, DeviceLightSystemReadAuthority.AUTHORITATIVE)
        )
        assertEquals(
            listOf(DeviceLightRuntimeContract.Action.STATUS_GET) + expectedAllRefreshActions,
            fixture.gateway.actions
        )
    }

    @Test
    fun `central refresh hydrates managed AUTO plan only when firmware reports it installed`() =
        runTest {
            val fixture = fixture(
                generation = generationOne,
                managedPlanInstalled = true
            )

            assertTrue(fixture.coordinator.refreshGeneration(deviceUid, generationOne).isSuccess())
            assertEquals(expectedManagedPlanRefreshActions, fixture.gateway.actions)
            assertNotNull(
                fixture.runtime.currentManagedAutoPlan(
                    deviceUid,
                    DeviceLightManagedPlanReadAuthority.AUTHORITATIVE
                )
            )
        }

    @Test
    fun `concurrent Light refresh callers share one device scoped firmware flight`() = runTest {
        val statusGate = CompletableDeferred<Unit>()
        val fixture = fixture(generationOne, statusGate)

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.coordinator.refreshGeneration(deviceUid, generationOne)
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.coordinator.refreshGeneration(deviceUid, generationOne)
        }

        assertEquals(listOf(DeviceLightRuntimeContract.Action.STATUS_GET), fixture.gateway.actions)
        statusGate.complete(Unit)

        assertTrue(first.await().isSuccess())
        assertTrue(second.await().isSuccess())
        assertEquals(expectedRefreshActions, fixture.gateway.actions)
    }

    @Test
    fun `new connection generation does not reuse stale Light refresh flight`() = runTest {
        val statusGate = CompletableDeferred<Unit>()
        val fixture = fixture(generationOne, statusGate)
        val oldRefresh = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.coordinator.refreshGeneration(deviceUid, generationOne)
        }
        assertEquals(
            listOf(DeviceLightRuntimeContract.Action.STATUS_GET),
            fixture.gateway.actions
        )

        fixture.owner.invalidate(deviceUid, generationOne)
        fixture.owner.beginGeneration(deviceUid, generationTwo)
        fixture.gateway.generation = generationTwo
        val newRefresh = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.coordinator.refreshGeneration(deviceUid, generationTwo)
        }
        statusGate.complete(Unit)

        assertTrue(oldRefresh.await() is DeviceLightRuntimeRefreshResult.RejectedStale)
        assertTrue(newRefresh.await().isSuccess())
        assertEquals(
            listOf(DeviceLightRuntimeContract.Action.STATUS_GET) + expectedRefreshActions,
            fixture.gateway.actions
        )
    }

    @Test
    fun `Light mutation and central refresh share one device operation gate`() = runTest {
        val mutationGate = CompletableDeferred<Unit>()
        val fixture = fixture(generationOne, mutationGate = mutationGate)
        assertTrue(fixture.runtime.requestStatus(deviceUid) is DeviceRuntimeCommandOutcome.Success)
        fixture.gateway.actions.clear()

        val mutation = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.runtime.productCommand(
                deviceUid = deviceUid,
                action = DeviceLightRuntimeContract.Action.MANUAL_OFF,
                parser = { _, _ -> Unit }
            )
        }
        assertEquals(listOf(DeviceLightRuntimeContract.Action.MANUAL_OFF), fixture.gateway.actions)
        val refresh = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.coordinator.refreshAll(deviceUid)
        }
        assertEquals(listOf(DeviceLightRuntimeContract.Action.MANUAL_OFF), fixture.gateway.actions)

        mutationGate.complete(Unit)
        assertTrue(mutation.await() is DeviceRuntimeCommandOutcome.Success)
        assertTrue(refresh.await().isSuccess())
        assertEquals(
            listOf(DeviceLightRuntimeContract.Action.MANUAL_OFF) + expectedAllRefreshActions,
            fixture.gateway.actions
        )
    }

    @Test
    fun `queued Light write from an invalidated generation is never sent`() = runTest {
        val statusGate = CompletableDeferred<Unit>()
        val fixture = fixture(generationOne, statusGate)
        val firstStatus = DeviceLightStatusParser.parse(status(managedPlanInstalled = false))
        assertTrue(fixture.owner.recordStatus(deviceUid, generationOne, firstStatus))

        val refresh = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.coordinator.refreshAll(deviceUid)
        }
        assertEquals(listOf(DeviceLightRuntimeContract.Action.STATUS_GET), fixture.gateway.actions)
        val queuedMutation = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.runtime.productCommand(
                deviceUid = deviceUid,
                action = DeviceLightRuntimeContract.Action.MANUAL_OFF,
                parser = { _, _ -> Unit }
            )
        }
        fixture.owner.invalidate(deviceUid, generationOne)
        fixture.owner.beginGeneration(deviceUid, generationTwo)
        fixture.gateway.generation = generationTwo
        statusGate.complete(Unit)

        assertTrue(refresh.await() is DeviceLightRuntimeRefreshResult.RejectedStale)
        assertTrue(queuedMutation.await() is DeviceRuntimeCommandOutcome.UnsupportedByDevice)
        assertEquals(
            listOf(DeviceLightRuntimeContract.Action.STATUS_GET), fixture.gateway.actions
        )
    }

    @Test
    fun `bootstrap preserves complete authority already hydrated in the same generation`() = runTest {
        val fixture = fixture(generationOne)
        assertTrue(fixture.coordinator.refreshAll(deviceUid).isSuccess())
        val surface = fixture.runtime.currentAuthoritativeSurface()
        val system = fixture.runtime.currentSystem(deviceUid, DeviceLightSystemReadAuthority.AUTHORITATIVE)

        assertEquals(
            DeviceLightRuntimeBootstrapResult.Hydrated(generationOne),
            fixture.coordinator.hydrateStatus(deviceUid, generationOne)
        )
        assertNotNull(surface)
        assertEquals(surface, fixture.runtime.currentAuthoritativeSurface())
        assertNotNull(system)
        assertEquals(system, fixture.runtime.currentSystem(deviceUid, DeviceLightSystemReadAuthority.AUTHORITATIVE))
        assertEquals(expectedAllRefreshActions, fixture.gateway.actions)
    }

    @Test
    fun `bootstrap queued behind full refresh preserves the completed dashboard`() = runTest {
        val statusGate = CompletableDeferred<Unit>()
        val fixture = fixture(generationOne, statusGate)
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { fixture.coordinator.refreshAll(deviceUid) }
        val bootstrap = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.coordinator.hydrateStatus(deviceUid, generationOne)
        }
        statusGate.complete(Unit)

        assertTrue(refresh.await().isSuccess())
        assertEquals(DeviceLightRuntimeBootstrapResult.Hydrated(generationOne), bootstrap.await())
        assertNotNull(fixture.runtime.currentAuthoritativeSurface())
        assertEquals(expectedAllRefreshActions, fixture.gateway.actions)
    }

    @Test
    fun `full refresh queued behind minimal bootstrap hydrates all projections`() = runTest {
        val statusGate = CompletableDeferred<Unit>()
        val fixture = fixture(generationOne, statusGate)
        val bootstrap = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.coordinator.hydrateStatus(deviceUid, generationOne)
        }
        val refresh = async(start = CoroutineStart.UNDISPATCHED) { fixture.coordinator.refreshAll(deviceUid) }
        statusGate.complete(Unit)

        assertEquals(DeviceLightRuntimeBootstrapResult.Hydrated(generationOne), bootstrap.await())
        assertTrue(refresh.await().isSuccess())
        assertNotNull(fixture.runtime.currentAuthoritativeSurface())
        assertEquals(
            listOf(DeviceLightRuntimeContract.Action.STATUS_GET) + expectedAllRefreshActions,
            fixture.gateway.actions
        )
    }

    @Test
    fun `concurrent bootstrap requests use the first authoritative status`() = runTest {
        val statusGate = CompletableDeferred<Unit>()
        val fixture = fixture(generationOne, statusGate)
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.coordinator.hydrateStatus(deviceUid, generationOne)
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.coordinator.hydrateStatus(deviceUid, generationOne)
        }
        statusGate.complete(Unit)

        assertEquals(DeviceLightRuntimeBootstrapResult.Hydrated(generationOne), first.await())
        assertEquals(DeviceLightRuntimeBootstrapResult.Hydrated(generationOne), second.await())
        assertEquals(listOf(DeviceLightRuntimeContract.Action.STATUS_GET), fixture.gateway.actions)
    }

    @Test
    fun `bootstrap rejects stale generation and rereads only status after reconnect`() = runTest {
        val fixture = fixture(generationOne)
        assertTrue(fixture.coordinator.refreshAll(deviceUid).isSuccess())
        fixture.owner.invalidate(deviceUid, generationOne)
        fixture.owner.beginGeneration(deviceUid, generationTwo)
        fixture.gateway.generation = generationTwo
        fixture.gateway.actions.clear()

        assertEquals(
            DeviceLightRuntimeBootstrapResult.RejectedStale,
            fixture.coordinator.hydrateStatus(deviceUid, generationOne)
        )
        assertTrue(fixture.gateway.actions.isEmpty())
        assertEquals(
            DeviceLightRuntimeBootstrapResult.Hydrated(generationTwo),
            fixture.coordinator.hydrateStatus(deviceUid, generationTwo)
        )
        assertEquals(listOf(DeviceLightRuntimeContract.Action.STATUS_GET), fixture.gateway.actions)
        assertNull(fixture.runtime.currentDashboard(deviceUid, DeviceLightDashboardReadAuthority.AUTHORITATIVE))
        assertNotNull(fixture.runtime.currentDashboard(deviceUid, DeviceLightDashboardReadAuthority.PRESENTATION))
    }

    @Test
    fun `delayed bootstrap response cannot hydrate a replacement connection`() = runTest {
        val statusGate = CompletableDeferred<Unit>()
        val fixture = fixture(generationOne, statusGate)
        val oldBootstrap = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.coordinator.hydrateStatus(deviceUid, generationOne)
        }
        fixture.owner.invalidate(deviceUid, generationOne)
        fixture.owner.beginGeneration(deviceUid, generationTwo)
        fixture.gateway.generation = generationTwo
        val newBootstrap = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.coordinator.hydrateStatus(deviceUid, generationTwo)
        }
        statusGate.complete(Unit)

        assertEquals(DeviceLightRuntimeBootstrapResult.RejectedStale, oldBootstrap.await())
        assertEquals(DeviceLightRuntimeBootstrapResult.Hydrated(generationTwo), newBootstrap.await())
        assertEquals(List(2) { DeviceLightRuntimeContract.Action.STATUS_GET }, fixture.gateway.actions)
        assertTrue(fixture.runtime.isAuthoritative(deviceUid, generationTwo))
    }

}
