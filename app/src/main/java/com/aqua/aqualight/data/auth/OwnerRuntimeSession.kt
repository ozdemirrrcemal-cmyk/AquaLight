package com.aqua.aqualight.data.auth

import android.content.Context

/**
 * Explicit foreground owner runtime boundary.
 *
 * This is the only session type allowed to open the device repository, UDP
 * discovery and WebSocket runtime. Background workers and receivers must use
 * [AuthenticatedOwnerProvider] plus owner-scoped stores instead.
 */
class OwnerRuntimeSession private constructor(
    private val coordinator: OwnerSessionCoordinator
) {

    suspend fun open(
        ownerUid: String
    ): OwnerSessionCoordinator.OpenResult {
        return coordinator.open(ownerUid)
    }

    suspend fun close(
        expectedOwnerUid: String? = null,
        cancelNotifications: Boolean = true
    ): OwnerSessionCoordinator.CloseResult {
        return coordinator.close(
            expectedOwnerUid = expectedOwnerUid,
            cancelNotifications = cancelNotifications
        )
    }

    fun snapshot(): OwnerSessionStateMachine.Snapshot {
        return coordinator.snapshot()
    }

    companion object {
        fun create(
            context: Context
        ): OwnerRuntimeSession {
            return OwnerRuntimeSession(
                coordinator = OwnerSessionCoordinator.create(
                    context.applicationContext
                )
            )
        }
    }
}
