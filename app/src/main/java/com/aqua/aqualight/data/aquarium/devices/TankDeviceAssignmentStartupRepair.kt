package com.aqua.aqualight.data.aquarium.devices

import android.content.Context
import android.util.Log
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object TankDeviceAssignmentStartupRepair {

    private const val TAG = "TankDeviceRepair"

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    @Volatile
    private var activeOwnerUid: String = ""

    @Volatile
    private var repairJob: Job? = null

    @Synchronized
    fun schedule(
        context: Context
    ) {
        val ownerUid = UserDataScope.normalizeOwnerUid(
            UserDataScope.currentUid()
        )

        if (ownerUid.isBlank()) {
            return
        }

        if (
            activeOwnerUid == ownerUid &&
            repairJob?.isActive == true
        ) {
            return
        }

        activeOwnerUid = ownerUid
        repairJob?.cancel()
        repairJob = scope.launch {
            runCatching {
                TankDeviceAssignmentRepositoryProvider
                    .get(context.applicationContext)
                    .repairStaleAssignments()
            }.onSuccess { result ->
                if (result.removedTotal > 0) {
                    Log.i(
                        TAG,
                        "Removed ${result.removedTotal} stale assignment record(s)."
                    )
                }
            }.onFailure { error ->
                Log.e(
                    TAG,
                    "Tank-device assignment repair failed.",
                    error
                )
            }
        }
    }

    @Synchronized
    fun reset() {
        repairJob?.cancel()
        repairJob = null
        activeOwnerUid = ""
    }
}
