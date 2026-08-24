package com.aqua.aqualight.application.devices

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

/** Application boundary for tank-device listing, assignment and removal. */
interface TankDeviceAssignmentOperations {
    fun start(scope: CoroutineScope): Job

    fun assignedDevices(tankId: Long): Flow<List<TankDeviceListItem>>

    fun availableDevices(tankId: Long): Flow<AvailableTankDevicesSnapshot>

    suspend fun assignDevice(tankId: Long, deviceUid: String): AssignDeviceToTankResult

    suspend fun removeDevice(tankId: Long, deviceUid: String): RemoveDeviceFromTankResult
}

data class TankDeviceListItem(
    val deviceUid: String,
    val displayName: String,
    val serialText: String,
    val family: OwnerDeviceFamily,
    val availability: OwnerDeviceAvailability,
    /** Exact catalog-owned physical Dosing channel count; null when not applicable or unresolved. */
    val dosingChannelCount: Int? = null
)

data class AvailableTankDevicesSnapshot(
    val devices: List<TankDeviceListItem>,
    val hasRegisteredDevices: Boolean
)

sealed interface AssignDeviceToTankResult {
    data object Assigned : AssignDeviceToTankResult
    data object AlreadyAssigned : AssignDeviceToTankResult
    data class Conflict(val existingTankId: Long) : AssignDeviceToTankResult
    data object TankNotFound : AssignDeviceToTankResult
    data object DeviceNotFound : AssignDeviceToTankResult
    data object InvalidRequest : AssignDeviceToTankResult
    data object Failure : AssignDeviceToTankResult
}

enum class RemoveDeviceFromTankResult {
    REMOVED,
    NOT_ASSIGNED,
    INVALID_REQUEST,
    FAILURE
}
