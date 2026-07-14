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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeRepositoryLifecycleTest {

    @Test
    fun concurrentConnectForSameDeviceCreatesOneSessionAndOneCollectorPair() {
        val transports = CopyOnWriteArrayList<FakeWsTransport>()
        val repository = repositoryWith(transports)
        val snapshot = snapshot("device-concurrent")
        val observedEvents = CopyOnWriteArrayList<AqlWsEvent>()
        val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        observerScope.launch {
            repository.events.collect(observedEvents::add)
        }

        val workers = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val completed = CountDownLatch(24)

        repeat(24) {
            workers.execute {
                start.await()
                repository.connect(snapshot).getOrThrow()
                completed.countDown()
            }
        }

        start.countDown()
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        workers.shutdownNow()

        assertEquals(1, transports.size)
        transports.single().emit(AqlWsEvent.Opened(snapshot.deviceUid))
        assertEquals(1, observedEvents.size)

        repository.close()
        observerScope.cancel()
    }

    @Test
    fun closingDeviceCancelsCollectorsAndClosesOnlyThatTransport() {
        val transports = CopyOnWriteArrayList<FakeWsTransport>()
        val repository = repositoryWith(transports)
        val first = snapshot("device-one")
        val second = snapshot("device-two", ip = "192.168.1.11")
        val observedEvents = CopyOnWriteArrayList<AqlWsEvent>()
        val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        observerScope.launch {
            repository.events.collect(observedEvents::add)
        }

        repository.connect(first).getOrThrow()
        repository.connect(second).getOrThrow()
        val firstTransport = transports[0]
        val secondTransport = transports[1]

        firstTransport.emit(AqlWsEvent.Opened(first.deviceUid))
        assertEquals(1, observedEvents.size)

        repository.close(first.deviceUid)
        firstTransport.emit(AqlWsEvent.Opened(first.deviceUid))
        secondTransport.emit(AqlWsEvent.Opened(second.deviceUid))

        assertEquals(2, observedEvents.size)
        assertEquals(second.deviceUid, observedEvents.last().deviceUid)
        assertEquals(1, firstTransport.closeCount.get())
        assertEquals(0, secondTransport.closeCount.get())
        assertNull(repository.currentConnectionState(first.deviceUid))

        repository.close()
        observerScope.cancel()
    }

    @Test
    fun closingOwnerRepositoryRejectsReconnectAndDropsOldOwnerEvents() {
        val oldOwnerTransports = CopyOnWriteArrayList<FakeWsTransport>()
        val newOwnerTransports = CopyOnWriteArrayList<FakeWsTransport>()
        val oldRepository = repositoryWith(oldOwnerTransports)
        val newRepository = repositoryWith(newOwnerTransports)
        val oldSnapshot = snapshot("old-owner-device")
        val newSnapshot = snapshot("new-owner-device", ip = "192.168.1.12")
        val newOwnerEvents = CopyOnWriteArrayList<AqlWsEvent>()
        val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        observerScope.launch {
            newRepository.events.collect(newOwnerEvents::add)
        }

        oldRepository.connect(oldSnapshot).getOrThrow()
        val oldTransport = oldOwnerTransports.single()
        oldRepository.close()

        assertEquals(1, oldTransport.closeCount.get())
        assertFalse(oldRepository.connect(oldSnapshot).isSuccess)

        newRepository.connect(newSnapshot).getOrThrow()
        oldTransport.emit(AqlWsEvent.Opened(oldSnapshot.deviceUid))
        newOwnerTransports.single().emit(AqlWsEvent.Opened(newSnapshot.deviceUid))

        assertEquals(1, newOwnerEvents.size)
        assertEquals(newSnapshot.deviceUid, newOwnerEvents.single().deviceUid)

        newRepository.close()
        observerScope.cancel()
    }

    private fun repositoryWith(
        transports: CopyOnWriteArrayList<FakeWsTransport>
    ): DeviceRuntimeRepository {
        return DeviceRuntimeRepository(
            wsClientFactory = {
                FakeWsTransport().also(transports::add)
            },
            dispatcher = Dispatchers.Unconfined
        )
    }

    private fun snapshot(
        uid: String,
        ip: String = "192.168.1.10"
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

    private class FakeWsTransport : AqlWsTransport {
        private val _connectionState = MutableStateFlow<AqlWsConnectionState>(
            AqlWsConnectionState.Disconnected
        )
        override val connectionState: StateFlow<AqlWsConnectionState> =
            _connectionState.asStateFlow()

        private val _events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = 16)
        override val events: SharedFlow<AqlWsEvent> = _events.asSharedFlow()

        val closeCount = AtomicInteger(0)
        private var activeDeviceUid: DeviceUid? = null

        override fun connect(
            deviceUid: DeviceUid,
            endpoint: DeviceRuntimeEndpoint
        ): Result<Unit> {
            activeDeviceUid = deviceUid
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
            activeDeviceUid = null
            _connectionState.value = AqlWsConnectionState.Disconnected
        }

        override fun close() {
            closeCount.incrementAndGet()
            disconnect()
        }

        fun emit(event: AqlWsEvent) {
            _events.tryEmit(event)
        }
    }
}
