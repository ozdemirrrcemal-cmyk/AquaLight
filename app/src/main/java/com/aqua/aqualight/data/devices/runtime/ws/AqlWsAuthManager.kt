package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.concurrent.ConcurrentHashMap

class AqlWsAuthManager(
    private val tokenProvider: AqlWsTokenProvider
) : AutoCloseable {
    private val pendingAuthOwners = ConcurrentHashMap<String, DeviceUid>()

    suspend fun authenticateIfTokenExists(
        deviceUid: DeviceUid,
        commandClient: AqlWsCommandClient
    ): AqlWsAuthAttemptResult {
        val token = tokenProvider.getToken(deviceUid)?.trim().orEmpty()
        if (token.isBlank()) {
            return AqlWsAuthAttemptResult.NoToken
        }

        val messageId = commandClient.authenticate(token)
            ?: return AqlWsAuthAttemptResult.SendFailed

        pendingAuthOwners[messageId] = deviceUid
        return AqlWsAuthAttemptResult.AuthMessageSent(messageId = messageId)
    }

    fun handleIncomingMessage(
        deviceUid: DeviceUid,
        message: AqlWsIncomingMessage?,
        wsClient: AqlWsTransport
    ): AqlWsAuthStateChange? {
        if (message == null) {
            return null
        }

        val messageId = message.id.trim()
        if (messageId.isBlank()) {
            return null
        }

        val pendingOwner = pendingAuthOwners.remove(messageId) ?: return null
        if (pendingOwner != deviceUid) {
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
        if (token.isNotBlank()) {
            tokenProvider.saveToken(deviceUid, token.trim())
        }
    }

    suspend fun clearToken(deviceUid: DeviceUid) {
        tokenProvider.clearToken(deviceUid)
    }

    override fun close() {
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
