package com.aqua.aqualight.data.auth

import com.aqua.aqualight.application.auth.SessionExitOperations
import com.aqua.aqualight.application.auth.SessionExitResult

internal class DefaultSessionExitOperations(
    private val logoutManager: LogoutManager
) : SessionExitOperations {

    override suspend fun logout(): SessionExitResult {
        return logoutManager.logout().toApplicationResult()
    }

    override suspend fun cleanupAfterSensitiveAction(
        cancelNotifications: Boolean
    ): SessionExitResult {
        return logoutManager.cleanupAfterLocalSensitiveAction(
            cancelNotifications = cancelNotifications
        ).toApplicationResult()
    }

    private fun LogoutManager.LogoutResult.toApplicationResult(): SessionExitResult {
        return SessionExitResult(
            blockingError = preferenceCleanupError
        )
    }
}
