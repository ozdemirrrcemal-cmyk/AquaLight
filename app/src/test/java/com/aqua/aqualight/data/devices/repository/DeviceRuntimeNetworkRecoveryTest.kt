package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTransport
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeNetworkRecoveryTest {

    @Test
    fun `network recovery replaces only the stale device session`() = runBlocking {
        val transports = CopyOnWriteArrayList<FakeTransport>()
        val repository = DeviceRuntimeRepository(
            wsClientFactory = {
                FakeTransport().also(transports::add)
            },
            dispatcher = Dispatchers.Unconfined
        )
        val snapshot = snapshot("device-recovery")

        repository.connect(snapshot).getOrThrow()
        val first = transports.single()
        first.markAuthenticated(snapshot.deviceUid)

        repository.reconnectAfterNetworkRestore(snapshot).getOrThrow()

        assertEquals(2, transports.size)
        assertEquals(1, first.closeCount.get())
        assertEquals(1, transports[1].connectCount.get())
        assertTrue(
            repository.currentConnectionState(snapshot.deviceUid) is AqlWsConnectionState.Connected
        )

        repository.shutdown()
    }

    private fun snapshot(uid: String): DeviceSnapshot {
        return DeviceSnapshot(
            identity = DeviceIdentity(uid = DeviceUid(uid)),
            product = DeviceProduct(),
            endpoint = DeviceRuntimeEndpoint(
                ip = "192.168.1.10",
                wsPort = 80
            )
        )
    }

    private class FakeTransport : AqlWsTransport {
        private val _connectionState = MutableStateFlow<AqlWsConnectionState>(
            AqlWsConnectionState.Disconnected
        )
        override val connectionState: StateFlow<AqlWsConnectionState> =
            _connectionState.asStateFlow()
        override val events: Flow<AqlWsEvent> = emptyFlow()

        val connectCount = AtomicInteger(0)
        val closeCount = AtomicInteger(0)

        override fun connect(
            deviceUid: DeviceUid,
            endpoint: DeviceRuntimeEndpoint
        ): Result<Unit> {
            connectCount.incrementAndGet()
            _connectionState.value = AqlWsConnectionState.Connected(
                deviceUid = deviceUid,
                url = endpoint.toWebSocketUrl().orEmpty(),
                connectedAtMillis = 1L
            )
            return Result.success(Unit)
        }

        override fun send(message: AqlWsOutgoingMessage): Boolean = true

        override fun sendRaw(raw: String): Boolean = true

        override fun markAuthenticated(deviceUid: DeviceUid) {
            _connectionState.value = AqlWsConnectionState.Authenticated(
                deviceUid = deviceUid,
                authenticatedAtMillis = 2L
            )
        }

        override fun markAuthRequired(deviceUid: DeviceUid, message: String) {
            _connectionState.value = AqlWsConnectionState.AuthRequired(
                deviceUid = deviceUid,
                message = message
            )
        }

        override fun disconnect(code: Int, reason: String) {
            _connectionState.value = AqlWsConnectionState.Disconnected
        }

        override fun close() {
            closeCount.incrementAndGet()
            disconnect()
        }
    }
}
