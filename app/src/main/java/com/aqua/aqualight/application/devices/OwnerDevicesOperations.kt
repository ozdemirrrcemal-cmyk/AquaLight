package com.aqua.aqualight.application.devices

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

/**
 * Application-facing device-list boundary.
 *
 * UI consumes stable primitives and application enums only. Repository, assignment,
 * persistence and runtime implementation types remain behind the data adapter.
 */
interface OwnerDevicesOperations {
    val devices: Flow<List<OwnerDeviceListItem>>

    fun start(scope: CoroutineScope): Job

    fun refreshVisibleDevices()

    suspend fun deleteDevices(deviceUids: Set<String>): DeleteOwnerDevicesResult
}

data class OwnerDeviceListItem(
    val deviceUid: String,
    val displayName: String,
    val serialText: String,
    val family: OwnerDeviceFamily,
    val availability: OwnerDeviceAvailability,
    val assignedTankName: String = ""
)

enum class OwnerDeviceFamily {
    LIGHT,
    TIMER,
    DOSING,
    COOLING,
    UNKNOWN
}

enum class OwnerDeviceAvailability {
    REACHABLE,
    UNREACHABLE
}

data class DeleteOwnerDevicesResult(
    val succeededDeviceUids: Set<String>,
    val failedDeviceUids: Set<String>,
    val notificationCleanupPendingDeviceUids: Set<String> = emptySet()
) {
    val requestedCount: Int
        get() = succeededDeviceUids.size + failedDeviceUids.size

    val succeededCount: Int
        get() = succeededDeviceUids.size

    val failedCount: Int
        get() = failedDeviceUids.size

    val isCompleteSuccess: Boolean
        get() = failedDeviceUids.isEmpty()

    val isCompleteFailure: Boolean
        get() = succeededDeviceUids.isEmpty() && failedDeviceUids.isNotEmpty()
}
