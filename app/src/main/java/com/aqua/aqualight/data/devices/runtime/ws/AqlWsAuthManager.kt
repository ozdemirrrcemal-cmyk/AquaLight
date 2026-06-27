package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.concurrent.ConcurrentHashMap

class AqlWsAuthManager(
    private val tokenProvider: AqlWsTokenProvider
) {
    private val pendingAuthMessageIds = ConcurrentHashMap.newKeySet<String>()

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

        pendingAuthMessageIds.add(messageId)
        return AqlWsAuthAttemptResult.AuthMessageSent(messageId = messageId)
    }

    fun handleIncomingMessage(
        deviceUid: DeviceUid,
        message: AqlWsIncomingMessage?,
        wsClient: AqlWsClient
    ): AqlWsAuthStateChange? {
        if (message == null) {
            return null
        }

        val messageId = message.id.trim()
        if (messageId.isBlank() || !pendingAuthMessageIds.remove(messageId)) {
            return null
        }

        return when (message) {
            is AqlWsIncomingMessage.Response -> {
                if (message.ok) {
                    wsClient.markAuthenticated(deviceUid)
                    AqlWsAuthStateChange.Authenticated(messageId = messageId)
                } else {
                    AqlWsAuthStateChange.Rejected(
                        messageId = messageId,
                        message = "Authentication rejected with status ${message.statusCode}"
                    )
                }
            }

            is AqlWsIncomingMessage.Error -> {
                AqlWsAuthStateChange.Rejected(
                    messageId = messageId,
                    message = message.message.ifBlank { "Authentication failed" }
                )
            }

            else -> null
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
