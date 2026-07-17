package com.aqua.aqualight.data.care.reminder

import com.aqua.aqualight.data.user.UserDataScope

/** Testable owner-stability boundary used by the WorkManager reconciliation job. */
internal class CareReminderReconcileRuntime(
    private val currentOwnerUid: () -> String?,
    private val reconcileOwner: suspend (String) -> Unit,
    private val cancelOwner: suspend (String) -> Unit
) {

    suspend fun run(ownerUid: String): Result {
        val owner = UserDataScope.normalizeOwnerUid(ownerUid)
        if (owner.isBlank() || currentOwnerUid()?.trim() != owner) {
            return Result.OWNER_NOT_ACTIVE
        }

        reconcileOwner(owner)

        if (currentOwnerUid()?.trim() != owner) {
            cancelOwner(owner)
            return Result.OWNER_CHANGED
        }

        return Result.COMPLETED
    }

    enum class Result {
        COMPLETED,
        OWNER_NOT_ACTIVE,
        OWNER_CHANGED
    }
}
