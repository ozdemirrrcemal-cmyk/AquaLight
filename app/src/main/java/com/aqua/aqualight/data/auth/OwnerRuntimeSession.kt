package com.aqua.aqualight.data.auth

import android.content.Context

/**
 * Explicit foreground owner runtime boundary.
 *
 * UI/session code opens the device repository, UDP discovery and WebSocket runtime only through this
 * type. Background workers must not use it. Firmware availability work has one narrower exception:
 * [OwnerBackgroundRuntimeLease], which delegates to the same [OwnerSessionCoordinator], never opens
 * a parallel runtime, and closes only a runtime that remained background-owned for the full lease.
 */
class OwnerRuntimeSession private constructor(
    private val coordinator: OwnerSessionCoordinator
) {

    suspend fun open(
        ownerUid: String
    ): OwnerSessionCoordinator.OpenResult {
        return coordinator.openForeground(ownerUid)
    }

    suspend fun close(
        expectedOwnerUid: String? = null,
        cancelNotifications: Boolean = true
    ): OwnerSessionCoordinator.CloseResult {
        return coordinator.closeForeground(
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
