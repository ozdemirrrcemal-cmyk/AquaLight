package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFailureCode
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGenerationState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTransport
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeMetadataTimeoutTest {

    @Test
    fun `incomplete authenticated bootstrap expires and closes current socket`() = runTest {
        val transport = RecordingWsTransport()
        val repository = DeviceRuntimeRepository(
            wsClientFactory = { transport },
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        val deviceUid = DeviceUid("metadata-timeout-device")

        repository.connect(snapshot(deviceUid)).getOrThrow()
        transport.emit(AqlWsEvent.Authenticated(deviceUid))
        runCurrent()

        assertEquals(3, transport.commands.size)
        assertTrue(
            repository.metadataBootstrapCoordinator.currentState(deviceUid) is
                DeviceRuntimeMetadataGenerationState.Collecting
        )

        advanceTimeBy(METADATA_TIMEOUT_MILLIS)
        runCurrent()

        val rejected = repository.metadataBootstrapCoordinator.currentState(deviceUid) as
            DeviceRuntimeMetadataGenerationState.Rejected
        assertEquals(DeviceRuntimeMetadataFailureCode.BOOTSTRAP_TIMEOUT, rejected.failure.code)
        assertEquals(METADATA_BOOTSTRAP_FAILED_REASON, transport.lastDisconnectReason)
        repository.close()
    }

    private fun snapshot(deviceUid: DeviceUid): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(uid = deviceUid),
        product = DeviceProduct(),
        endpoint = DeviceRuntimeEndpoint(ip = "192.168.1.20", wsPort = 80)
    )

    private class RecordingWsTransport : AqlWsTransport {
        private val _connectionState = MutableStateFlow<AqlWsConnectionState>(
            AqlWsConnectionState.Disconnected
        )
        override val connectionState: StateFlow<AqlWsConnectionState> =
            _connectionState.asStateFlow()

        private val _events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = 16)
        override val events: SharedFlow<AqlWsEvent> = _events.asSharedFlow()

        val commands = CopyOnWriteArrayList<AqlWsOutgoingMessage.Command>()
        var lastDisconnectReason: String? = null
            private set

        override fun connect(
            deviceUid: DeviceUid,
            endpoint: DeviceRuntimeEndpoint
        ): Result<Unit> {
            _connectionState.value = AqlWsConnectionState.Connected(
                deviceUid = deviceUid,
                url = "ws://${endpoint.ip}:${endpoint.wsPort}",
                connectedAtMillis = 1L
            )
            return Result.success(Unit)
        }

        override fun send(message: AqlWsOutgoingMessage): Boolean {
            val command = message as? AqlWsOutgoingMessage.Command ?: return false
            commands += command
            return true
        }

        override fun disconnect(code: Int, reason: String) {
            lastDisconnectReason = reason
            _connectionState.value = AqlWsConnectionState.Disconnected
        }

        override fun close() {
            disconnect(reason = "closed")
        }

        fun emit(event: AqlWsEvent) {
            _events.tryEmit(event)
        }
    }

    private companion object {
        const val METADATA_TIMEOUT_MILLIS = 10_000L
        const val METADATA_BOOTSTRAP_FAILED_REASON = "metadata bootstrap failed"
    }
}
