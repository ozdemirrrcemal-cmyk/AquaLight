package com.aqua.aqualight.base

import java.util.UUID

/**
 * Distinguishes a configuration recreation inside the current process from UI state
 * restored after Android has terminated and recreated the application process.
 *
 * Owner-scoped fragments depend on an in-memory runtime that must be committed again
 * after process death. Restoring an old owner graph before that commit is unsafe, while
 * restoring inside the same process is safe and preserves rotations/theme changes.
 */
internal object AppProcessIdentity {
    val token: String = UUID.randomUUID().toString()
}

internal object ProcessUiStateRestorePolicy {
    const val STATE_PROCESS_TOKEN = "aqualight.activity.process_token"

    fun canRestore(
        savedProcessToken: String?,
        currentProcessToken: String
    ): Boolean {
        return savedProcessToken?.isNotBlank() == true &&
            currentProcessToken.isNotBlank() &&
            savedProcessToken == currentProcessToken
    }
}
