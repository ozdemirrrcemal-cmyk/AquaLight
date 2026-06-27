package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.model.DeviceUid

class AqlWsAuthManager(
    private val tokenProvider: AqlWsTokenProvider
) {
    suspend fun authenticateIfTokenExists(
        deviceUid: DeviceUid,
        commandClient: AqlWsCommandClient
    ): AqlWsAuthAttemptResult {
        val token = tokenProvider.getToken(deviceUid)?.trim().orEmpty()
        if (token.isBlank()) {
            return AqlWsAuthAttemptResult.NoToken
        }

        return if (commandClient.authenticate(token)) {
            AqlWsAuthAttemptResult.AuthMessageSent
        } else {
            AqlWsAuthAttemptResult.SendFailed
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
    data object AuthMessageSent : AqlWsAuthAttemptResult
    data object SendFailed : AqlWsAuthAttemptResult
}
