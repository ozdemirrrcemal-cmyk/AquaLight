package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
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

    private class FakeWsTransport : AqlWsTransport {
        private val _connectionState = MutableStateFlow<AqlWsConnectionState>(
            AqlWsConnectionState.Disconnected
        )
        override val connectionState: StateFlow<AqlWsConnectionState> =
            _connectionState.asStateFlow()

        private val _events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = 1)
        override val events: SharedFlow<AqlWsEvent> = _events.asSharedFlow()

        var authenticatedDeviceUid: DeviceUid? = null
            private set

        override fun connect(
            deviceUid: DeviceUid,
            endpoint: DeviceRuntimeEndpoint
        ): Result<Unit> = Result.success(Unit)

        override fun send(message: AqlWsOutgoingMessage): Boolean = true

        override fun sendRaw(raw: String): Boolean = true

        override fun markAuthenticated(deviceUid: DeviceUid) {
            authenticatedDeviceUid = deviceUid
        }

        override fun markAuthRequired(deviceUid: DeviceUid, message: String) = Unit

        override fun disconnect(code: Int, reason: String) = Unit

        override fun close() = Unit
    }
}
