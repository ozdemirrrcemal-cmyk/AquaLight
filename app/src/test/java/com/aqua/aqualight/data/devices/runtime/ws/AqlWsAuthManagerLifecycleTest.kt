package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AqlWsAuthManagerLifecycleTest {

    @Test
    fun responseFromAnotherDeviceCannotConsumePendingAuthentication() = runBlocking {
        val ownerDevice = DeviceUid("owner-device")
        val otherDevice = DeviceUid("other-device")
        val transport = FakeWsTransport()
        val manager = AqlWsAuthManager(
            tokenProvider = FakeTokenProvider(token = "runtime-token")
        )

        val attempt = manager.authenticateIfTokenExists(
            deviceUid = ownerDevice,
            commandClient = AqlWsCommandClient(transport)
        ) as AqlWsAuthAttemptResult.AuthMessageSent
        val response = response(messageId = attempt.messageId)

        assertNull(
            manager.handleIncomingMessage(
                deviceUid = otherDevice,
                message = response,
                wsClient = transport
            )
        )

        val accepted = manager.handleIncomingMessage(
            deviceUid = ownerDevice,
            message = response,
            wsClient = transport
        )

        assertTrue(accepted is AqlWsAuthStateChange.Authenticated)
        assertEquals(ownerDevice, transport.authenticatedDeviceUid)
        manager.close()
    }

    @Test
    fun closeWhileTokenReadIsInFlightCannotSendOldOwnerAuthentication() {
        val deviceUid = DeviceUid("old-owner-device")
        val tokenProvider = BlockingTokenProvider()
        val transport = FakeWsTransport()
        val manager = AqlWsAuthManager(tokenProvider)
        val result = AtomicReference<AqlWsAuthAttemptResult>()
        val completed = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        try {
            executor.execute {
                result.set(
                    runBlocking {
                        manager.authenticateIfTokenExists(
                            deviceUid = deviceUid,
                            commandClient = AqlWsCommandClient(transport)
                        )
                    }
                )
                completed.countDown()
            }

            assertTrue(tokenProvider.readEntered.await(5, TimeUnit.SECONDS))
            manager.close()
            tokenProvider.allowReadToFinish.countDown()

            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertEquals(AqlWsAuthAttemptResult.SendFailed, result.get())
            assertEquals(0, transport.sendCount.get())
            assertNull(
                manager.handleIncomingMessage(
                    deviceUid = deviceUid,
                    message = response("auth-late"),
                    wsClient = transport
                )
            )
        } finally {
            tokenProvider.allowReadToFinish.countDown()
            manager.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun closedManagerRejectsFutureTokenOperations() = runBlocking {
        val deviceUid = DeviceUid("closed-owner-device")
        val manager = AqlWsAuthManager(FakeTokenProvider("token"))
        manager.close()

        assertEquals(
            AqlWsAuthAttemptResult.SendFailed,
            manager.authenticateIfTokenExists(
                deviceUid = deviceUid,
                commandClient = AqlWsCommandClient(FakeWsTransport())
            )
        )
        assertFalse(runCatching { manager.saveToken(deviceUid, "new-token") }.isSuccess)
        assertFalse(runCatching { manager.clearToken(deviceUid) }.isSuccess)
    }

    private fun response(messageId: String): AqlWsIncomingMessage.Response {
        return AqlWsIncomingMessage.Response(
            raw = "{}",
            id = messageId,
            type = "response",
            json = JSONObject(),
            ok = true,
            module = "security",
            action = "auth",
            statusCode = 200
        )
    }

    private class FakeTokenProvider(
        private val token: String
    ) : AqlWsTokenProvider {
        override suspend fun getToken(deviceUid: DeviceUid): String = token

        override suspend fun saveToken(deviceUid: DeviceUid, token: String) = Unit

        override suspend fun clearToken(deviceUid: DeviceUid) = Unit
    }

    private class BlockingTokenProvider : AqlWsTokenProvider {
        val readEntered = CountDownLatch(1)
        val allowReadToFinish = CountDownLatch(1)

        override suspend fun getToken(deviceUid: DeviceUid): String {
            readEntered.countDown()
            check(allowReadToFinish.await(5, TimeUnit.SECONDS)) {
                "token read test timed out"
            }
            return "old-owner-token"
        }

        override suspend fun saveToken(deviceUid: DeviceUid, token: String) = Unit

        override suspend fun clearToken(deviceUid: DeviceUid) = Unit
    }

    private class FakeWsTransport : AqlWsTransport {
        private val _connectionState = MutableStateFlow<AqlWsConnectionState>(
            AqlWsConnectionState.Disconnected
        )
        override val connectionState: StateFlow<AqlWsConnectionState> =
            _connectionState.asStateFlow()

        private val _events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = 1)
        override val events: SharedFlow<AqlWsEvent> = _events.asSharedFlow()

        val sendCount = AtomicInteger(0)
        var authenticatedDeviceUid: DeviceUid? = null
            private set

        override fun connect(
            deviceUid: DeviceUid,
            endpoint: DeviceRuntimeEndpoint
        ): Result<Unit> = Result.success(Unit)

        override fun send(message: AqlWsOutgoingMessage): Boolean {
            sendCount.incrementAndGet()
            return true
        }

        override fun sendRaw(raw: String): Boolean = true

        override fun markAuthenticated(deviceUid: DeviceUid) {
            authenticatedDeviceUid = deviceUid
        }

        override fun markAuthRequired(deviceUid: DeviceUid, message: String) = Unit

        override fun disconnect(code: Int, reason: String) = Unit

        override fun close() = Unit
    }
}
