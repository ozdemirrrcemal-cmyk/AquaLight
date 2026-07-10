package com.aqua.aqualight.data.aquarium.devices

import android.content.Context
import android.util.Log
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Repairs relationships after an authenticated session becomes active.
 *
 * A failed cleanup after a tank/device deletion is therefore recoverable on the
 * next app/session start instead of leaving a permanent dangling relationship.
 */
object TankDeviceAssignmentStartupRepair {

    private const val TAG = "TankDeviceRepair"

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

    private val lock = Any()

    @Volatile
    private var scheduledOwnerUid: String = ""

    @Volatile
    private var repairJob: Job? = null

    fun schedule(
        context: Context
    ) {
        val ownerUid = UserDataScope.normalizeOwnerUid(
            UserDataScope.currentUid()
        )

        if (ownerUid.isBlank()) {
            return
        }

        synchronized(lock) {
            if (
                scheduledOwnerUid == ownerUid &&
                repairJob?.isActive == true
            ) {
                return
            }

            if (
                scheduledOwnerUid == ownerUid &&
                repairJob?.isCompleted == true
            ) {
                return
            }

            repairJob?.cancel()
            scheduledOwnerUid = ownerUid

            val appContext = context.applicationContext
            repairJob = scope.launch {
                runCatching {
                    TankDeviceAssignmentRepositoryProvider
                        .get(appContext)
                        .repairStaleAssignments()
                }.onSuccess { report ->
                    if (report.removedCount > 0) {
                        Log.i(
                            TAG,
                            "Removed ${report.removedCount} stale tank-device assignments."
                        )
                    }
                }.onFailure { error ->
                    Log.e(
                        TAG,
                        "Tank-device assignment startup repair failed.",
                        error
                    )
                }
            }
        }
    }

    fun reset() {
        synchronized(lock) {
            repairJob?.cancel()
            repairJob = null
            scheduledOwnerUid = ""
        }
    }
}
