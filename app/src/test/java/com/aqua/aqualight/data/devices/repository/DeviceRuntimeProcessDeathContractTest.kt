package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTokenProvider
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTransport
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceRuntimeProcessDeathContractTest {

    @Test
    fun processDeathEquivalentTerminalShutdownLeavesNoJobsCollectorsSocketsOrTokenAccess() =
        runBlocking {
            val transports = CopyOnWriteArrayList<RecordingTransport>()
            val repository = DeviceRuntimeRepository(
                tokenProvider = RecordingTokenProvider(),
                wsClientFactory = {
                    RecordingTransport().also { transport -> transports += transport }
                },
                dispatcher = Dispatchers.Unconfined
            )
            val first = snapshot("device-one", "192.168.1.10")
            val second = snapshot("device-two", "192.168.1.11")
            val observedEvents = CopyOnWriteArrayList<AqlWsEvent>()
            val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

            observerScope.launch {
                repository.events.collect { event: AqlWsEvent ->
                    observedEvents.add(event)
                }
            }

            repository.connect(first).getOrThrow()
            repository.connect(second).getOrThrow()
            transports[0].emit(AqlWsEvent.Opened(first.deviceUid))
            transports[1].emit(AqlWsEvent.Opened(second.deviceUid))
            assertEquals(2, observedEvents.size)

            repository.shutdown()

            transports[0].emit(AqlWsEvent.Opened(first.deviceUid))
            transports[1].emit(AqlWsEvent.Opened(second.deviceUid))

            assertEquals(2, observedEvents.size)
            assertEquals(listOf(1, 1), transports.map { transport -> transport.closeCount.get() })
            assertFalse(repository.connect(first).isSuccess)
            assertFalse(
                runCatching {
                    repository.saveToken(first.deviceUid, "late-token")
                }.isSuccess
            )
            assertFalse(
                runCatching {
                    repository.clearToken(first.deviceUid)
                }.isSuccess
            )

            observerScope.cancel()
        }

    private fun snapshot(
        uid: String,
        ip: String
    ): DeviceSnapshot {
        return DeviceSnapshot(
            identity = DeviceIdentity(uid = DeviceUid(uid)),
            product = DeviceProduct(),
            endpoint = DeviceRuntimeEndpoint(
                ip = ip,
                wsPort = 80
            )
        )
    }

    private class RecordingTokenProvider : AqlWsTokenProvider {
        override suspend fun getToken(deviceUid: DeviceUid): String? = null

        override suspend fun saveToken(
            deviceUid: DeviceUid,
            token: String
        ) = Unit

        override suspend fun clearToken(deviceUid: DeviceUid) = Unit
    }

    private class RecordingTransport : AqlWsTransport {
        private val _connectionState = MutableStateFlow<AqlWsConnectionState>(
            AqlWsConnectionState.Disconnected
        )
        override val connectionState: StateFlow<AqlWsConnectionState> =
            _connectionState.asStateFlow()

        private val _events = MutableSharedFlow<AqlWsEvent>(
            extraBufferCapacity = 8
        )
        override val events: SharedFlow<AqlWsEvent> = _events.asSharedFlow()

        val closeCount = AtomicInteger(0)

        override fun connect(
            deviceUid: DeviceUid,
            endpoint: DeviceRuntimeEndpoint
        ): Result<Unit> {
            _connectionState.value = AqlWsConnectionState.Connected(
                deviceUid = deviceUid,
                url = "ws://test.device.aql.local${endpoint.wsPath}",
                connectedAtMillis = 1L
            )
            return Result.success(Unit)
        }

        override fun send(message: AqlWsOutgoingMessage): Boolean = true

        override fun disconnect(
            code: Int,
            reason: String
        ) {
            _connectionState.value = AqlWsConnectionState.Disconnected
        }

        override fun close() {
            closeCount.incrementAndGet()
            disconnect(code = 1000, reason = "terminal close")
        }

        fun emit(event: AqlWsEvent) {
            _events.tryEmit(event)
        }
    }
}
