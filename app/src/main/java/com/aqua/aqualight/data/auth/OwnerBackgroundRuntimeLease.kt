package com.aqua.aqualight.data.auth

import android.content.Context
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Narrow background-only owner runtime boundary for firmware availability work.
 *
 * It delegates runtime creation to [OwnerSessionCoordinator], pins the immutable owner identity for
 * the whole operation, and releases only a runtime that was opened by background work. If the same
 * runtime is promoted to the foreground while work is running, release cannot close it.
 */
internal class OwnerBackgroundRuntimeLease private constructor(
    private val coordinator: OwnerSessionCoordinator
) {
    suspend fun <T> withOwner(
        ownerUid: String,
        block: suspend () -> T
    ): T {
        val normalizedOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)
        require(normalizedOwnerUid.isNotBlank()) { "ownerUid must not be blank" }

        return UserDataScope.withOwnerUid(normalizedOwnerUid) {
            val lease = coordinator.acquireBackgroundLease(normalizedOwnerUid).getOrThrow()
            try {
                block()
            } finally {
                withContext(NonCancellable) {
                    coordinator.releaseBackgroundLease(lease)
                }
            }
        }
    }

    companion object {
        fun create(context: Context): OwnerBackgroundRuntimeLease {
            return OwnerBackgroundRuntimeLease(
                coordinator = OwnerSessionCoordinator.create(context.applicationContext)
            )
        }
    }
}
