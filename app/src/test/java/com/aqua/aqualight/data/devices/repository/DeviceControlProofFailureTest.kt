package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceControlProofFailureTest {

    @Test
    fun `failed proof publishes offline and replaces the stale device session`() = runTest {
        val transports = CopyOnWriteArrayList<FakeTransport>()
        val runtime = DeviceRuntimeRepository(
            wsClientFactory = {
                FakeTransport().also(transports::add)
            },
            dispatcher = Dispatchers.Unconfined
        )
        val repository = DevicesRepository(runtimeRepository = runtime)
        val snapshot = DeviceSnapshot(
            identity = DeviceIdentity(uid = DeviceUid("device-control-failure")),
            endpoint = DeviceRuntimeEndpoint(ip = "192.168.1.91", wsPort = 80)
        )
        repository.registerSnapshot(snapshot)
        transports.single().markAuthenticated(snapshot.deviceUid)

        repository.recordControlFailure(snapshot.deviceUid)

        assertEquals(
            DeviceOnlineState.OFFLINE,
            repository.currentDevice(snapshot.deviceUid)?.connectionState?.onlineState
        )
        assertEquals(2, transports.size)
        assertEquals(1, transports.first().closeCount.get())
        assertEquals(1, transports.last().connectCount.get())

        repository.shutdown()
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
                url = "ws://test.device.aql.local${endpoint.wsPath}",
                connectedAtMillis = 1L
            )
            return Result.success(Unit)
        }

        override fun send(message: AqlWsOutgoingMessage): Boolean = true

        fun markAuthenticated(deviceUid: DeviceUid) {
            _connectionState.value = AqlWsConnectionState.Authenticated(
                deviceUid = deviceUid,
                authenticatedAtMillis = 2L
            )
        }

        override fun disconnect(code: Int, reason: String) {
            _connectionState.value = AqlWsConnectionState.Disconnected
        }

        override fun close() {
            closeCount.incrementAndGet()
            disconnect(code = 1000, reason = "closed")
        }
    }
}
