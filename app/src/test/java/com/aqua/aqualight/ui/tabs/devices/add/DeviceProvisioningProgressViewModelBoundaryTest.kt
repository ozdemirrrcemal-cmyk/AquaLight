package com.aqua.aqualight.ui.tabs.devices.add

import com.aqua.aqualight.R
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
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceProvisioningProgressViewModelBoundaryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `missing session renders expired state without opening transport`() {
        val operations = FakeProvisioningOperations(session = null)
        val viewModel = viewModel(operations)

        viewModel.bind("missing-session")

        assertEquals(1, operations.getSessionCalls)
        assertEquals(
            text(R.string.device_provisioning_session_expired_title),
            viewModel.uiState.value.title
        )
        assertFalse(viewModel.uiState.value.canStart)
        assertEquals(0, operations.startTransportCalls)
        assertEquals(0, operations.prepareCalls)
    }

    @Test
    fun `existing session renders ready state through one application boundary`() {
        val session = session()
        val operations = FakeProvisioningOperations(session)
        val viewModel = viewModel(operations)

        viewModel.bind(session.sessionId)

        val state = viewModel.uiState.value
        assertEquals(1, operations.getSessionCalls)
        assertEquals(session.deviceTitle, state.deviceName)
        assertEquals(session.deviceSerial, state.deviceSerial)
        assertEquals(session.bleAddress, state.bleAddress)
        assertEquals(session.wifiSsid, state.wifiSsid)
        assertTrue(state.canStart)
        assertFalse(state.showProgress)
    }

    @Test
    fun `binding the same session twice remains idempotent`() {
        val operations = FakeProvisioningOperations(session())
        val viewModel = viewModel(operations)

        viewModel.bind("session-1")
        viewModel.bind("session-1")

        assertEquals(1, operations.getSessionCalls)
        assertEquals(0, operations.startTransportCalls)
    }

    @Test
    fun `start delegates session id and resolved BLE address`() {
        val operations = FakeProvisioningOperations(session())
        val viewModel = viewModel(operations)
        viewModel.bind("session-1")

        viewModel.startProvisioning()

        assertEquals(1, operations.startTransportCalls)
        assertEquals("session-1", operations.startedSessionId)
        assertEquals("AA:BB:CC:DD:EE:FF", operations.startedBleAddress)
    }

    @Test
    fun `runtime handoff and completion commit prepared registration`() = runTest {
        val operations = FakeProvisioningOperations(session())
        val viewModel = viewModel(operations)
        viewModel.bind("session-1")
        viewModel.startProvisioning()

        operations.emit(ProvisioningTransportEvent.RuntimeHandoffReceived(handoff()))
        operations.emit(ProvisioningTransportEvent.Completed)
        val event = viewModel.events.first() as DeviceProvisioningProgressEvent.OpenAddedDevice

        assertEquals(1, operations.prepareCalls)
        assertEquals(1, operations.finalizeCalls)
        assertEquals(1, operations.commitCalls)
        assertEquals("device-1", event.device.deviceUid)
        assertEquals(OwnerDeviceFamily.LIGHT, event.device.family)
        assertTrue(operations.removedSessions.contains("session-1"))
    }

    @Test
    fun `transport failure after prepare rolls back pending registration`() = runTest {
        val operations = FakeProvisioningOperations(session())
        val viewModel = viewModel(operations)
        viewModel.bind("session-1")
        viewModel.startProvisioning()

        operations.emit(ProvisioningTransportEvent.RuntimeHandoffReceived(handoff()))
        operations.emit(ProvisioningTransportEvent.Failed("GATT connection is not active"))

        assertEquals(listOf("device-1"), operations.rollbackDeviceUids)
        assertTrue(viewModel.uiState.value.requiresFreshDeviceSelection)
        assertFalse(viewModel.uiState.value.showProgress)
    }

    private fun viewModel(operations: FakeProvisioningOperations) =
        DeviceProvisioningProgressViewModel(
            operations = operations,
            textResolver = FakeTextResolver
        )

    private fun session() = ProvisioningSessionSnapshot(
        sessionId = "session-1",
        candidateId = "candidate-1",
        bleAddress = "AA:BB:CC:DD:EE:FF",
        bleName = "AquaLight-Setup",
        deviceTitle = "AquaLight Test",
        deviceSerial = "AQL-TEST-001",
        deviceModel = "AQL-Light",
        wifiSsid = "Test WiFi",
        createdAtMillis = 1L
    )

    private fun handoff() = ProvisioningRuntimeHandoff(
        deviceUid = "device-1",
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
        ),
        webSocketToken = "a".repeat(64)
    )

    private class FakeProvisioningOperations(
        private val session: ProvisioningSessionSnapshot?
    ) : ProvisioningProgressOperations {
        override val ownerUid: String = "owner-a"
        private val eventFlow = MutableSharedFlow<ProvisioningTransportEvent>(
            extraBufferCapacity = 16
        )
        override val events: Flow<ProvisioningTransportEvent> = eventFlow

        var getSessionCalls = 0
        var startTransportCalls = 0
        var prepareCalls = 0
        var finalizeCalls = 0
        var commitCalls = 0
        var startedSessionId = ""
        var startedBleAddress = ""
        val removedSessions = mutableListOf<String>()
        val rollbackDeviceUids = mutableListOf<String>()

        override fun getSession(sessionId: String): ProvisioningSessionSnapshot? {
            getSessionCalls += 1
            return session?.takeIf { it.sessionId == sessionId }
        }

        override fun removeSession(sessionId: String) {
            removedSessions += sessionId
        }

        override suspend fun resolveBleAddress(sessionId: String): Result<String> =
            Result.success(requireNotNull(session).bleAddress)

        override fun startTransport(
            sessionId: String,
            bleAddress: String
        ): Result<Unit> {
            startTransportCalls += 1
            startedSessionId = sessionId
            startedBleAddress = bleAddress
            return Result.success(Unit)
        }

        override fun finalizeSetup(
            handoff: ProvisioningRuntimeHandoff
        ): Result<Unit> {
            finalizeCalls += 1
            return Result.success(Unit)
        }

        override fun closeTransport() = Unit

        override suspend fun prepareRegistration(
            sessionId: String,
            verifiedDeviceInfo: ProvisioningVerifiedDeviceInfo?,
            handoff: ProvisioningRuntimeHandoff
        ): Result<PreparedProvisioningRegistration> {
            prepareCalls += 1
            return Result.success(
                PreparedProvisioningRegistration(
                    registrationId = "registration-1",
                    device = ProvisionedDevice(
                        deviceUid = handoff.deviceUid,
                        title = verifiedDeviceInfo?.title ?: "AquaLight Test",
                        family = OwnerDeviceFamily.LIGHT
                    )
                )
            )
        }

        override suspend fun commitPreparedRegistration(
            registration: PreparedProvisioningRegistration
        ): Result<Unit> {
            commitCalls += 1
            return Result.success(Unit)
        }

        override suspend fun rollbackProvisioningRegistration(
            deviceUid: String
        ): Result<Unit> {
            rollbackDeviceUids += deviceUid
            return Result.success(Unit)
        }

        override suspend fun rollbackProvisioningRegistrationForOwner(
            ownerUid: String,
            deviceUid: String
        ): Result<Unit> {
            rollbackDeviceUids += deviceUid
            return Result.success(Unit)
        }

        suspend fun emit(event: ProvisioningTransportEvent) {
            eventFlow.emit(event)
        }
    }

    private object FakeTextResolver : AppTextResolver {
        override fun get(resId: Int, vararg args: Any): String = text(resId, *args)
    }

    class MainDispatcherRule(
        private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }

    private companion object {
        fun text(resId: Int, vararg args: Any): String = buildString {
            append("text:")
            append(resId)
            if (args.isNotEmpty()) {
                append(":")
                append(args.joinToString(separator = "|"))
            }
        }
    }
}
