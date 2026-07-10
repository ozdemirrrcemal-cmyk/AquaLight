package com.aqua.aqualight.data.user

internal enum class OwnerDeviceCleanupOperation {
    STOP_ACTIVE_SESSION,
    CLEAR_ASSIGNMENTS,
    CLEAR_KNOWN_DEVICES,
    CLEAR_IGNORED_DEVICES,
    CLEAR_CREDENTIALS
}

internal data class OwnerDeviceCleanupFailure(
    val operation: OwnerDeviceCleanupOperation,
    val error: Throwable
)

/**
 * Executes every owner-scoped device cleanup operation even when one operation fails.
 */
internal class OwnerDeviceDataCleaner(
    private val stopActiveSession: suspend () -> Unit,
    private val clearAssignments: suspend (String) -> Unit,
    private val clearKnownDevices: suspend (String) -> Unit,
    private val clearIgnoredDevices: suspend (String) -> Unit,
    private val clearCredentials: suspend (String) -> Unit
) {

    suspend fun clear(
        ownerUid: String,
        activeOwnerMatchesTarget: Boolean
    ): List<OwnerDeviceCleanupFailure> {
        val normalizedOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)
        require(normalizedOwnerUid.isNotBlank()) {
            "Owner-scoped device cleanup requires a non-empty owner UID."
        }

        val failures = mutableListOf<OwnerDeviceCleanupFailure>()

        suspend fun attempt(
            operation: OwnerDeviceCleanupOperation,
            block: suspend () -> Unit
        ) {
            runCatching {
                block()
            }.onFailure { error ->
                failures += OwnerDeviceCleanupFailure(
                    operation = operation,
                    error = error
                )
            }
        }

        if (activeOwnerMatchesTarget) {
            attempt(OwnerDeviceCleanupOperation.STOP_ACTIVE_SESSION) {
                stopActiveSession()
            }
        }

        attempt(OwnerDeviceCleanupOperation.CLEAR_ASSIGNMENTS) {
            clearAssignments(normalizedOwnerUid)
        }
        attempt(OwnerDeviceCleanupOperation.CLEAR_KNOWN_DEVICES) {
            clearKnownDevices(normalizedOwnerUid)
        }
        attempt(OwnerDeviceCleanupOperation.CLEAR_IGNORED_DEVICES) {
            clearIgnoredDevices(normalizedOwnerUid)
        }
        attempt(OwnerDeviceCleanupOperation.CLEAR_CREDENTIALS) {
            clearCredentials(normalizedOwnerUid)
        }

        return failures
    }
}
