package com.aqua.aqualight.ui.tabs.devices.add

import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuOpenUseCase
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.provisioning.PreparedProvisioningRegistration
import com.aqua.aqualight.application.devices.provisioning.ProvisionedDevice
import com.aqua.aqualight.application.devices.provisioning.ProvisioningProgressOperations
import com.aqua.aqualight.application.devices.provisioning.ProvisioningRuntimeEndpoint
import com.aqua.aqualight.application.devices.provisioning.ProvisioningRuntimeHandoff
import com.aqua.aqualight.application.devices.provisioning.ProvisioningSessionSnapshot
import com.aqua.aqualight.application.devices.provisioning.ProvisioningTransportEvent
import com.aqua.aqualight.application.devices.provisioning.ProvisioningVerifiedDeviceInfo
import com.aqua.aqualight.application.text.AppTextResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceProvisioningCancellationBoundaryTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `back before runtime handoff closes transport removes session and exits`() = runTest {
        val operations = FakeOperations()
        val viewModel = viewModel(operations)
        viewModel.bind(SESSION_ID)
        viewModel.startProvisioning()

        viewModel.requestExit()
        val event = viewModel.events.first()

        assertEquals(DeviceProvisioningProgressEvent.ExitProvisioning, event)
        assertEquals(1, operations.closeTransportCalls)
        assertEquals(listOf(SESSION_ID), operations.removedSessions)
        assertTrue(operations.ownerRollbacks.isEmpty())
        assertEquals(0, operations.commitCalls)
    }

    @Test
    fun `back after prepared handoff rolls back captured owner before exit`() = runTest {
        val operations = FakeOperations()
        val viewModel = viewModel(operations)
        viewModel.bind(SESSION_ID)
        viewModel.startProvisioning()
        operations.emit(
            ProvisioningTransportEvent.RuntimeHandoffReceived(handoff())
        )

        viewModel.requestExit()
        val event = viewModel.events.first()

        assertEquals(DeviceProvisioningProgressEvent.ExitProvisioning, event)
        assertEquals(listOf(OWNER_UID to DEVICE_UID), operations.ownerRollbacks)
        assertEquals(listOf(SESSION_ID), operations.removedSessions)
        assertEquals(0, operations.commitCalls)
    }

    private fun viewModel(operations: FakeOperations) =
        DeviceProvisioningProgressViewModel(
            operations = operations,
            menuOpenUseCase = DeviceMenuOpenUseCase(
                menuAccessOperations = UnusedDeviceMenuAccessOperations,
                controlSurfacePreparationOperations = UnusedControlSurfacePreparationOperations
            ),
            textResolver = FakeTextResolver
        )

    private fun handoff() = ProvisioningRuntimeHandoff(
        handoffId = "handoff-1",
        deviceUid = DEVICE_UID,
        endpoint = ProvisioningRuntimeEndpoint(
            ip = "192.168.1.44",
            wifiMode = "station",
            wifiConnected = true,
            setupApActive = false,
            runtimeTransport = "websocket",
            webSocketPort = 81,
            webSocketPath = "/aql",
            webSocketProtocol = "aql.v1",
            webSocketProtocolVersion = 1,
            discoveryPort = 4210
        )
    )

    private class FakeOperations : ProvisioningProgressOperations {
        override val ownerUid: String = OWNER_UID
        private val eventFlow = MutableSharedFlow<ProvisioningTransportEvent>(
            extraBufferCapacity = 8
        )
        override val events: Flow<ProvisioningTransportEvent> = eventFlow

        var closeTransportCalls = 0
        var commitCalls = 0
        val removedSessions = mutableListOf<String>()
        val ownerRollbacks = mutableListOf<Pair<String, String>>()

        override fun getSession(sessionId: String): ProvisioningSessionSnapshot? =
            ProvisioningSessionSnapshot(
                sessionId = SESSION_ID,
                candidateId = "candidate-1",
                bleAddress = "AA:BB:CC:DD:EE:FF",
                bleName = "AQL-SETUP-0001",
                deviceTitle = "AquaLight Test",
                deviceSerial = "AQL-TEST-001",
                deviceModel = "AQL-Light",
                wifiSsid = "Home WiFi",
                createdAtMillis = 1L
            ).takeIf { sessionId == SESSION_ID }

        override fun removeSession(sessionId: String) {
            removedSessions += sessionId
        }

        override suspend fun resolveBleAddress(sessionId: String): Result<String> =
            Result.success("AA:BB:CC:DD:EE:FF")

        override fun startTransport(
            sessionId: String,
            bleAddress: String
        ): Result<Unit> = Result.success(Unit)

        override fun finalizeSetup(
            handoff: ProvisioningRuntimeHandoff
        ): Result<Unit> = Result.success(Unit)

        override fun closeTransport() {
            closeTransportCalls += 1
        }

        override suspend fun prepareRegistration(
            sessionId: String,
            verifiedDeviceInfo: ProvisioningVerifiedDeviceInfo?,
            handoff: ProvisioningRuntimeHandoff
        ): Result<PreparedProvisioningRegistration> = Result.success(
            PreparedProvisioningRegistration(
                registrationId = "registration-1",
                device = ProvisionedDevice(
                    deviceUid = handoff.deviceUid,
                    title = "AquaLight Test",
                    family = OwnerDeviceFamily.LIGHT
                )
            )
        )

        override suspend fun commitPreparedRegistration(
            registration: PreparedProvisioningRegistration
        ): Result<Unit> {
            commitCalls += 1
            return Result.success(Unit)
        }

        override suspend fun rollbackProvisioningRegistration(
            deviceUid: String
        ): Result<Unit> = Result.success(Unit)

        override suspend fun rollbackProvisioningRegistrationForOwner(
            ownerUid: String,
            deviceUid: String
        ): Result<Unit> {
            ownerRollbacks += ownerUid to deviceUid
            return Result.success(Unit)
        }

        suspend fun emit(event: ProvisioningTransportEvent) {
            eventFlow.emit(event)
        }
    }

    private object UnusedDeviceMenuAccessOperations : DeviceMenuAccessOperations {
        override suspend fun resolve(deviceUid: String): DeviceMenuAccessResult =
            error("Device menu access is not expected in cancellation tests.")
    }

    private object UnusedControlSurfacePreparationOperations :
        DeviceControlSurfacePreparationOperations {
        override suspend fun prepare(
            request: DeviceControlSurfacePreparationRequest
        ): DeviceControlSurfacePreparationResult =
            error("Control-surface preparation is not expected in cancellation tests.")
    }

    private object FakeTextResolver : AppTextResolver {
        override fun get(resId: Int, vararg args: Any): String = "text:$resId"
    }

    private companion object {
        const val OWNER_UID = "owner-a"
        const val SESSION_ID = "session-1"
        const val DEVICE_UID = "device-1"
    }
}
