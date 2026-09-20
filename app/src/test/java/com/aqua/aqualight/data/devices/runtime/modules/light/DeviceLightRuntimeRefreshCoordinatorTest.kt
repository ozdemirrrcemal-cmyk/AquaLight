package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
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

    private fun fixture(
        generation: DeviceRuntimeConnectionGeneration,
        statusGate: CompletableDeferred<Unit>? = null
    ): RefreshFixture {
        val gateway = FixtureGateway(generation, statusGate)
        val owner = DeviceLightRuntimeStateOwner()
        owner.beginGeneration(deviceUid, generation)
        val runtime = DeviceLightRuntimeRepository(gateway, owner)
        return RefreshFixture(
            gateway = gateway,
            owner = owner,
            runtime = runtime,
            coordinator = DeviceLightRuntimeRefreshCoordinator(
                runtime = runtime,
                thermal = DeviceLightThermalRuntimeRepository(gateway, owner),
                protection = DeviceLightTemperatureProtectionRuntimeRepository(gateway, owner)
            )
        )
    }

    private data class RefreshFixture(
        val gateway: FixtureGateway,
        val owner: DeviceLightRuntimeStateOwner,
        val runtime: DeviceLightRuntimeRepository,
        val coordinator: DeviceLightRuntimeRefreshCoordinator
    )

    private class FixtureGateway(
        var generation: DeviceRuntimeConnectionGeneration,
        private val statusGate: CompletableDeferred<Unit>?
    ) : DeviceRuntimeCommandGateway {
        val actions = mutableListOf<String>()

        override suspend fun <T> execute(
            deviceUid: DeviceUid,
            command: DeviceRuntimeCommand<T>,
            timeoutMillis: Long
        ): DeviceRuntimeCommandOutcome<T> {
            actions += command.action
            if (command.action == DeviceLightRuntimeContract.Action.STATUS_GET) {
                statusGate?.await()
            }
            val response = AqlWsIncomingMessage.Response(
                id = "refresh-${actions.size}",
                type = "res",
                module = command.module,
                action = command.action,
                data = responseData(command.action),
                ok = true,
                statusCode = HTTP_OK
            )
            return DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = command.module,
                action = command.action,
                messageId = response.id,
                generation = generation,
                statusCode = response.statusCode,
                value = command.parseSuccess(response)
            )
        }

        private fun responseData(action: String): JSONObject = when (action) {
            DeviceLightRuntimeContract.Action.STATUS_GET -> DeviceLightRuntimeFixtures.status()
            DeviceLightRuntimeContract.Action.CUSTOM_GET -> customDocument()
            DeviceLightRuntimeContract.Action.AUTO_PROGRAMS_GET -> automaticPrograms()
            DeviceLightRuntimeContract.Action.GRAPH_GET -> DeviceLightRuntimeFixtures.graph()
            else -> error("Unexpected Light refresh action: $action")
        }
    }

    private companion object {
        val deviceUid = DeviceUid("central-light-refresh")
        val generationOne = DeviceRuntimeConnectionGeneration(1L)
        val generationTwo = DeviceRuntimeConnectionGeneration(2L)
        const val HTTP_OK = 200
        const val LIGHT_SURFACE_PART_COUNT = 4
        val expectedRefreshActions = listOf(
            DeviceLightRuntimeContract.Action.STATUS_GET,
            DeviceLightRuntimeContract.Action.CUSTOM_GET,
            DeviceLightRuntimeContract.Action.AUTO_PROGRAMS_GET,
            DeviceLightRuntimeContract.Action.GRAPH_GET
        )

        fun customDocument(): JSONObject = JSONObject()
            .put("revision", 1)
            .put("installed", false)
            .put("weekdaysMask", 0)
            .put("pointCount", 0)
            .put("points", JSONArray())

        fun automaticPrograms(): JSONObject = JSONObject()
            .put("revision", 1)
            .put("capacity", DeviceLightRuntimeContract.Limit.AUTO_PROGRAM_CAPACITY)
            .put("programCount", 0)
            .put("enabledCount", 0)
            .put("programs", JSONArray())

        fun DeviceLightRuntimeRepository.currentAuthoritativeSurface(): List<Any>? {
            val surface = listOfNotNull(
                currentStatus(deviceUid),
                currentLibrary(deviceUid, DeviceLightLibraryReadAuthority.AUTHORITATIVE),
                currentAutomatic(deviceUid, DeviceLightAutomaticReadAuthority.AUTHORITATIVE),
                currentDashboard(deviceUid, DeviceLightDashboardReadAuthority.AUTHORITATIVE)
            )
            return surface.takeIf { entries -> entries.size == LIGHT_SURFACE_PART_COUNT }
        }

        fun DeviceLightRuntimeRefreshResult.isSuccess(): Boolean =
            this is DeviceLightRuntimeRefreshResult.Success
    }
}
