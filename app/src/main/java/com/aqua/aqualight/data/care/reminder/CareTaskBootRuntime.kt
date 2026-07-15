package com.aqua.aqualight.data.care.reminder

import com.aqua.aqualight.data.care.model.CareTask

/**
 * Process-safe boot maintenance coordinator.
 *
 * The coordinator deliberately depends only on owner identity, owner-scoped
 * DataStore reads and reminder scheduling. Device repositories, UDP discovery
 * and WebSocket runtime cannot be supplied to this boundary.
 */
internal class CareTaskBootRuntime(
    private val currentOwnerUid: () -> String?,
    private val startOwnerMaintenance: (String) -> Unit,
    private val loadPendingTasks: suspend (String) -> List<CareTask>,
    private val scheduleReminder: (CareTask) -> Unit
) {

    suspend fun restore() {
        val ownerUid = currentOwnerUid()
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return

        startOwnerMaintenance(ownerUid)

        val pendingTasks = loadPendingTasks(ownerUid)

        // The Firebase owner can change while DataStore is being read. Old-owner
        // reminders must never be restored into the new foreground session.
        if (currentOwnerUid()?.trim() != ownerUid) {
            return
        }

        pendingTasks.forEach { task ->
            scheduleReminder(
                task.copy(
                    ownerUid = task.ownerUid.ifBlank { ownerUid }
                )
            )
        }
    }
}
