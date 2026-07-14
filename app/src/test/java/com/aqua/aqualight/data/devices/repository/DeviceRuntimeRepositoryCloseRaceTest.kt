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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeRepositoryCloseRaceTest {

    @Test
    fun ownerCloseWaitsForInFlightConnectAndLeavesNoActiveSession() {
        val transport = BlockingWsTransport()
        val repository = DeviceRuntimeRepository(
            wsClientFactory = { transport },
            dispatcher = Dispatchers.Unconfined
        )
        val snapshot = snapshot("device-close-race")
        val executor = Executors.newFixedThreadPool(2)
        val connectResult = AtomicReference<Result<Unit>>()
        val connectDone = CountDownLatch(1)
        val closeStarted = CountDownLatch(1)
        val closeDone = CountDownLatch(1)

        try {
            executor.execute {
                connectResult.set(repository.connect(snapshot))
                connectDone.countDown()
            }
            assertTrue(transport.connectEntered.await(5, TimeUnit.SECONDS))

            executor.execute {
                closeStarted.countDown()
                repository.close()
                closeDone.countDown()
            }
            assertTrue(closeStarted.await(5, TimeUnit.SECONDS))
            assertFalse(closeDone.await(250, TimeUnit.MILLISECONDS))

            transport.allowConnectToFinish.countDown()

            assertTrue(connectDone.await(5, TimeUnit.SECONDS))
            assertTrue(closeDone.await(5, TimeUnit.SECONDS))
            assertTrue(connectResult.get().isSuccess)
            assertEquals(1, transport.closeCount.get())
            assertNull(repository.commandClient())
            assertNull(repository.currentConnectionState(snapshot.deviceUid))
            assertFalse(repository.connect(snapshot).isSuccess)
        } finally {
            transport.allowConnectToFinish.countDown()
            repository.close()
            executor.shutdownNow()
        }
    }

    private fun snapshot(uid: String): DeviceSnapshot {
        return DeviceSnapshot(
            identity = DeviceIdentity(uid = DeviceUid(uid)),
            product = DeviceProduct(),
            endpoint = DeviceRuntimeEndpoint(
                ip = "192.168.1.20",
                wsPort = 80
            )
        )
    }

    private class BlockingWsTransport : AqlWsTransport {
        private val _connectionState = MutableStateFlow<AqlWsConnectionState>(
            AqlWsConnectionState.Disconnected
        )
        override val connectionState: StateFlow<AqlWsConnectionState> =
            _connectionState.asStateFlow()

        private val _events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = 4)
        override val events: SharedFlow<AqlWsEvent> = _events.asSharedFlow()

        val connectEntered = CountDownLatch(1)
        val allowConnectToFinish = CountDownLatch(1)
        val closeCount = AtomicInteger(0)

        override fun connect(
            deviceUid: DeviceUid,
            endpoint: DeviceRuntimeEndpoint
        ): Result<Unit> {
            connectEntered.countDown()
            if (!allowConnectToFinish.await(5, TimeUnit.SECONDS)) {
                return Result.failure(IllegalStateException("connect test timed out"))
            }
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
            disconnect(code = 1000, reason = "closed")
        }
    }
}
