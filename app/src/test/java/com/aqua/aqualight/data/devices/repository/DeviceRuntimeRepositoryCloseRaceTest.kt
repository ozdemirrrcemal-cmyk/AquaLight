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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRuntimeRepositoryCloseRaceTest {

    @Test
    fun ownerShutdownWaitsForInFlightConnectAndLeavesNoActiveSession() {
        val transport = BlockingWsTransport()
        val repository = DeviceRuntimeRepository(
            wsClientFactory = { transport },
            dispatcher = Dispatchers.Unconfined
        )
        val snapshot = snapshot("device-close-race")
        val executor = Executors.newFixedThreadPool(2)
        val connectResult = AtomicReference<Result<Unit>>()
        val connectDone = CountDownLatch(1)
        val shutdownStarted = CountDownLatch(1)
        val shutdownDone = CountDownLatch(1)

        try {
            executor.execute {
                connectResult.set(repository.connect(snapshot))
                connectDone.countDown()
            }
            assertTrue(transport.connectEntered.await(5, TimeUnit.SECONDS))

            executor.execute {
                shutdownStarted.countDown()
                runBlocking { repository.shutdown() }
                shutdownDone.countDown()
            }
            assertTrue(shutdownStarted.await(5, TimeUnit.SECONDS))
            assertFalse(shutdownDone.await(250, TimeUnit.MILLISECONDS))

            transport.allowConnectToFinish.countDown()

            assertTrue(connectDone.await(5, TimeUnit.SECONDS))
            assertTrue(shutdownDone.await(5, TimeUnit.SECONDS))
            assertTrue(connectResult.get().isSuccess)
            assertEquals(1, transport.closeCount.get())
            assertNull(repository.currentConnectionState(snapshot.deviceUid))
            assertFalse(repository.connect(snapshot).isSuccess)
        } finally {
            transport.allowConnectToFinish.countDown()
            repository.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun deviceRetireWaitsForInFlightConnectAndPreventsProbeResurrection() {
        val transport = BlockingWsTransport()
        val repository = DeviceRuntimeRepository(
            wsClientFactory = { transport },
            dispatcher = Dispatchers.Unconfined
        )
        val snapshot = snapshot("device-retire-race")
        val executor = Executors.newFixedThreadPool(2)
        val retireDone = CountDownLatch(1)

        try {
            executor.execute {
                repository.connect(snapshot)
            }
            assertTrue(transport.connectEntered.await(5, TimeUnit.SECONDS))

            executor.execute {
                runBlocking { repository.retire(snapshot.deviceUid) }
                retireDone.countDown()
            }
            assertFalse(retireDone.await(250, TimeUnit.MILLISECONDS))

            transport.allowConnectToFinish.countDown()

            assertTrue(retireDone.await(5, TimeUnit.SECONDS))
            assertEquals(1, transport.closeCount.get())
            assertFalse(repository.connect(snapshot).isSuccess)
        } finally {
            transport.allowConnectToFinish.countDown()
            repository.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun ownerShutdownWaitsForInFlightTokenAccess() {
        val tokenProvider = BlockingTokenProvider()
        val repository = DeviceRuntimeRepository(
            tokenProvider = tokenProvider,
            dispatcher = Dispatchers.Unconfined
        )
        val deviceUid = DeviceUid("device-token-race")
        val executor = Executors.newFixedThreadPool(2)
        val saveError = AtomicReference<Throwable?>()
        val saveDone = CountDownLatch(1)
        val shutdownDone = CountDownLatch(1)

        try {
            executor.execute {
                runBlocking {
                    runCatching {
                        repository.saveToken(deviceUid, "secret")
                    }.onFailure(saveError::set)
                }
                saveDone.countDown()
            }
            assertTrue(tokenProvider.saveEntered.await(5, TimeUnit.SECONDS))

            executor.execute {
                runBlocking { repository.shutdown() }
                shutdownDone.countDown()
            }
            assertFalse(shutdownDone.await(250, TimeUnit.MILLISECONDS))

            tokenProvider.allowSaveToFinish.countDown()

            assertTrue(saveDone.await(5, TimeUnit.SECONDS))
            assertTrue(shutdownDone.await(5, TimeUnit.SECONDS))
            assertTrue(saveError.get() is IllegalStateException)
            assertFalse(
                runBlocking {
                    runCatching { repository.saveToken(deviceUid, "new-secret") }.isSuccess
                }
            )
        } finally {
            tokenProvider.allowSaveToFinish.countDown()
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

    private class BlockingTokenProvider : AqlWsTokenProvider {
        val saveEntered = CountDownLatch(1)
        val allowSaveToFinish = CountDownLatch(1)

        override suspend fun getToken(deviceUid: DeviceUid): String? = null

        override suspend fun saveToken(deviceUid: DeviceUid, token: String) {
            saveEntered.countDown()
            check(allowSaveToFinish.await(5, TimeUnit.SECONDS)) {
                "token save test timed out"
            }
        }

        override suspend fun clearToken(deviceUid: DeviceUid) = Unit
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
                url = "ws://test.device.aql.local${endpoint.wsPath}",
                connectedAtMillis = 1L
            )
            return Result.success(Unit)
        }

        override fun send(message: AqlWsOutgoingMessage): Boolean = true

        override fun disconnect(code: Int, reason: String) {
            _connectionState.value = AqlWsConnectionState.Disconnected
        }

        override fun close() {
            closeCount.incrementAndGet()
            disconnect(code = 1000, reason = "closed")
        }
    }
}
