package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class AqlWsAuthManager(
    private val tokenProvider: AqlWsTokenProvider
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val pendingAuthOwners = ConcurrentHashMap<String, DeviceUid>()

    suspend fun authenticateIfTokenExists(
        deviceUid: DeviceUid,
        commandClient: AqlWsCommandClient
    ): AqlWsAuthAttemptResult {
        if (closed.get()) {
            return AqlWsAuthAttemptResult.SendFailed
        }

        val token = tokenProvider.getToken(deviceUid)?.trim().orEmpty()
        if (closed.get()) {
            return AqlWsAuthAttemptResult.SendFailed
        }
        if (token.isBlank()) {
            return AqlWsAuthAttemptResult.NoToken
        }

        val messageId = commandClient.authenticate(token)
            ?: return AqlWsAuthAttemptResult.SendFailed

        if (closed.get()) {
            return AqlWsAuthAttemptResult.SendFailed
        }

        pendingAuthOwners[messageId] = deviceUid
        if (closed.get()) {
            pendingAuthOwners.remove(messageId, deviceUid)
            return AqlWsAuthAttemptResult.SendFailed
        }

        return AqlWsAuthAttemptResult.AuthMessageSent(messageId = messageId)
    }

    fun handleIncomingMessage(
        deviceUid: DeviceUid,
        message: AqlWsIncomingMessage?,
        wsClient: AqlWsTransport
    ): AqlWsAuthStateChange? {
        if (closed.get() || message == null) {
            return null
        }

        val messageId = message.id.trim()
        if (messageId.isBlank() || !pendingAuthOwners.remove(messageId, deviceUid)) {
            return null
        }
        if (closed.get()) {
            return null
        }

        return when (message) {
            is AqlWsIncomingMessage.Response -> {
                if (message.ok) {
                    wsClient.markAuthenticated(deviceUid)
                    AqlWsAuthStateChange.Authenticated(messageId = messageId)
                } else {
                    val rejectionMessage = "Authentication rejected with status ${message.statusCode}"
                    wsClient.markAuthRequired(
                        deviceUid = deviceUid,
                        message = rejectionMessage
                    )
                    AqlWsAuthStateChange.Rejected(
                        messageId = messageId,
                        message = rejectionMessage
                    )
                }
            }

            is AqlWsIncomingMessage.Error -> {
                val rejectionMessage = message.message.ifBlank { "Authentication failed" }
                wsClient.markAuthRequired(
                    deviceUid = deviceUid,
                    message = rejectionMessage
                )
                AqlWsAuthStateChange.Rejected(
                    messageId = messageId,
                    message = rejectionMessage
                )
            }

            else -> null
        }
    }

    fun clear(deviceUid: DeviceUid) {
        pendingAuthOwners.entries.forEach { entry ->
            if (entry.value == deviceUid) {
                pendingAuthOwners.remove(entry.key, entry.value)
            }
        }
    }

    suspend fun saveToken(
        deviceUid: DeviceUid,
        token: String
    ) {
        check(!closed.get()) { "WebSocket auth manager is closed." }
        if (token.isNotBlank()) {
            tokenProvider.saveToken(deviceUid, token.trim())
        }
        check(!closed.get()) { "WebSocket auth manager closed during token save." }
    }

    suspend fun clearToken(deviceUid: DeviceUid) {
        check(!closed.get()) { "WebSocket auth manager is closed." }
        tokenProvider.clearToken(deviceUid)
        check(!closed.get()) { "WebSocket auth manager closed during token clear." }
    }

    override fun close() {
        closed.set(true)
        pendingAuthOwners.clear()
    }
}

sealed interface AqlWsAuthAttemptResult {
    data object NoToken : AqlWsAuthAttemptResult
    data class AuthMessageSent(val messageId: String) : AqlWsAuthAttemptResult
    data object SendFailed : AqlWsAuthAttemptResult
}

sealed interface AqlWsAuthStateChange {
    data class Authenticated(val messageId: String) : AqlWsAuthStateChange
    data class Rejected(
        val messageId: String,
        val message: String
    ) : AqlWsAuthStateChange
}
