package com.aqua.aqualight.data.aquarium.devices

import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TankDeviceAssignmentRepository(
    ownerUid: String,
    private val devicesRepository: DevicesRepository,
    private val assignmentStore: TankDeviceAssignmentStore,
    private val tankStore: AquariumTankDataStoreManager
) {

    private val ownerUid = ownerUid.trim().also { normalizedOwnerUid ->
        require(normalizedOwnerUid.isNotBlank()) {
            "ownerUid must not be blank"
        }
    }

    private val operationMutex = Mutex()
    private val ownerAssignments = assignmentStore.assignmentsForOwner(this.ownerUid)

    fun assignedDevicesForTank(
        tankId: Long
    ): Flow<List<DeviceSnapshot>> {
        return combine(
            devicesRepository.devices,
            ownerAssignments
        ) { devices, assignments ->
            if (tankId <= 0L) {
                return@combine emptyList()
            }

            val deviceByUid = devices.associateBy { snapshot ->
                snapshot.deviceUid
            }

            assignments
                .asSequence()
                .filter { assignment ->
                    assignment.tankId == tankId
                }
                .mapNotNull { assignment ->
                    deviceByUid[assignment.deviceUid]
                }
                .toList()
        }
    }

    fun availableDevicesForTank(
        tankId: Long
    ): Flow<List<DeviceSnapshot>> {
        return combine(
            devicesRepository.devices,
            ownerAssignments
        ) { devices, assignments ->
            if (tankId <= 0L) {
                return@combine emptyList()
            }

            val assignedDeviceUids = assignments
                .map { assignment ->
                    assignment.deviceUid
                }
                .toSet()

            devices
                .asSequence()
                .filter { snapshot ->
                    snapshot.deviceUid !in assignedDeviceUids
                }
                .sortedBy { snapshot ->
                    snapshot.title.lowercase()
                }
                .toList()
        }
    }

    fun assignedTankNamesByDevice(): Flow<Map<DeviceUid, String>> {
        return combine(
            ownerAssignments,
            tankStore.tanksFlow
        ) { assignments, tanks ->
            val tankNamesById = tanks.associate { tank ->
                tank.id to tank.name
            }

            assignments.mapNotNull { assignment ->
                tankNamesById[assignment.tankId]
                    ?.takeIf(String::isNotBlank)
                    ?.let { tankName ->
                        assignment.deviceUid to tankName
                    }
            }.toMap()
        }
    }

    suspend fun assignmentForDevice(
        deviceUid: DeviceUid
    ): TankDeviceAssignment? {
        return ownerAssignments.first().firstOrNull { assignment ->
            assignment.deviceUid == deviceUid
        }
    }

    suspend fun assignmentsSnapshotForTanks(
        tankIds: Set<Long>
    ): List<TankDeviceAssignment> {
        if (tankIds.isEmpty()) return emptyList()
        val assignmentsByDevice = ownerAssignments.first().associateBy { assignment ->
            assignment.deviceUid
        }
        return devicesRepository.currentDevices().mapNotNull { snapshot ->
            assignmentsByDevice[snapshot.deviceUid]
                ?.takeIf { assignment -> assignment.tankId in tankIds }
        }
    }

    suspend fun assignDeviceToTank(
        tankId: Long,
        deviceUid: DeviceUid
    ): TankDeviceAssignmentResult {
        if (tankId <= 0L || deviceUid.value.isBlank()) {
            return TankDeviceAssignmentResult.InvalidRequest
        }

        return operationMutex.withLock {
            try {
                val tankExists = tankStore.tanksSnapshotForOwner(ownerUid)
                    .any { tank ->
                        tank.id == tankId
                    }

                if (!tankExists) {
                    return@withLock TankDeviceAssignmentResult.TankNotFound
                }

                if (devicesRepository.currentDevice(deviceUid) == null) {
                    return@withLock TankDeviceAssignmentResult.DeviceNotFound
                }

                when (
                    val decision = assignmentStore.assignDeviceToTank(
                        ownerUid = ownerUid,
                        tankId = tankId,
                        deviceUid = deviceUid
                    )
                ) {
                    is TankDeviceStoreAssignDecision.Assigned -> {
                        TankDeviceAssignmentResult.Assigned(
                            assignment = decision.assignment
                        )
                    }

                    is TankDeviceStoreAssignDecision.AlreadyAssigned -> {
                        TankDeviceAssignmentResult.AlreadyAssigned(
                            assignment = decision.assignment
                        )
                    }

                    is TankDeviceStoreAssignDecision.Conflict -> {
                        TankDeviceAssignmentResult.Conflict(
                            existingAssignment = decision.existingAssignment
                        )
                    }
                }
            } catch (error: Throwable) {
                error.throwIfCancellation()
                TankDeviceAssignmentResult.Failure(error)
            }
        }
    }

    suspend fun removeDeviceFromTank(
        tankId: Long,
        deviceUid: DeviceUid
    ): TankDeviceRemovalResult {
        if (tankId <= 0L || deviceUid.value.isBlank()) {
            return TankDeviceRemovalResult.InvalidRequest
        }

        return operationMutex.withLock {
            try {
                val removed = assignmentStore.removeDeviceFromTank(
                    ownerUid = ownerUid,
                    tankId = tankId,
                    deviceUid = deviceUid
                )

                if (removed) {
                    TankDeviceRemovalResult.Removed
                } else {
                    TankDeviceRemovalResult.NotAssigned
                }
            } catch (error: Throwable) {
                error.throwIfCancellation()
                TankDeviceRemovalResult.Failure(error)
            }
        }
    }

    suspend fun removeDeviceFromAnyTank(
        deviceUid: DeviceUid
    ): TankDeviceRemovalResult {
        if (deviceUid.value.isBlank()) {
            return TankDeviceRemovalResult.InvalidRequest
        }

        return operationMutex.withLock {
            try {
                val removed = assignmentStore.removeDeviceFromAnyTank(
                    ownerUid = ownerUid,
                    deviceUid = deviceUid
                )

                if (removed) {
                    TankDeviceRemovalResult.Removed
                } else {
                    TankDeviceRemovalResult.NotAssigned
                }
            } catch (error: Throwable) {
                error.throwIfCancellation()
                TankDeviceRemovalResult.Failure(error)
            }
        }
    }

    suspend fun removeAssignmentsForTank(
        tankId: Long
    ): TankAssignmentCleanupResult {
        if (tankId <= 0L) {
            return TankAssignmentCleanupResult.InvalidRequest
        }

        return operationMutex.withLock {
            try {
                TankAssignmentCleanupResult.Completed(
                    removedCount = assignmentStore.removeAssignmentsForTank(
                        ownerUid = ownerUid,
                        tankId = tankId
                    )
                )
            } catch (error: Throwable) {
                error.throwIfCancellation()
                TankAssignmentCleanupResult.Failure(error)
            }
        }
    }

    suspend fun repairOwnerAssignments(): TankAssignmentRepairResult {
        return operationMutex.withLock {
            try {
                val validTankIds = tankStore.tanksSnapshotForOwner(ownerUid)
                    .map { tank ->
                        tank.id
                    }
                    .toSet()

                val validDeviceUids = devicesRepository.currentDevices()
                    .map { snapshot ->
                        snapshot.deviceUid.value
                    }
                    .toSet()

                val removedAssignments = assignmentStore.repairOwnerAssignments(
                    ownerUid = ownerUid,
                    validTankIds = validTankIds,
                    validDeviceUids = validDeviceUids
                )

                TankAssignmentRepairResult.Completed(
                    removedAssignments = removedAssignments
                )
            } catch (error: Throwable) {
                error.throwIfCancellation()
                TankAssignmentRepairResult.Failure(error)
            }
        }
    }

    suspend fun clearOwnerAssignments(): TankAssignmentCleanupResult {
        return operationMutex.withLock {
            try {
                TankAssignmentCleanupResult.Completed(
                    removedCount = assignmentStore.clearOwnerAssignments(ownerUid)
                )
            } catch (error: Throwable) {
                error.throwIfCancellation()
                TankAssignmentCleanupResult.Failure(error)
            }
        }
    }

    private fun Throwable.throwIfCancellation() {
        if (this is CancellationException) {
            throw this
        }
    }
}

sealed interface TankDeviceAssignmentResult {
    data class Assigned(
        val assignment: TankDeviceAssignment
    ) : TankDeviceAssignmentResult

    data class AlreadyAssigned(
        val assignment: TankDeviceAssignment
    ) : TankDeviceAssignmentResult

    data class Conflict(
        val existingAssignment: TankDeviceAssignment
    ) : TankDeviceAssignmentResult

    data object TankNotFound : TankDeviceAssignmentResult

    data object DeviceNotFound : TankDeviceAssignmentResult

    data object InvalidRequest : TankDeviceAssignmentResult

    data class Failure(
        val error: Throwable
    ) : TankDeviceAssignmentResult
}

sealed interface TankDeviceRemovalResult {
    data object Removed : TankDeviceRemovalResult

    data object NotAssigned : TankDeviceRemovalResult

    data object InvalidRequest : TankDeviceRemovalResult

    data class Failure(
        val error: Throwable
    ) : TankDeviceRemovalResult
}

sealed interface TankAssignmentCleanupResult {
    data class Completed(
        val removedCount: Int
    ) : TankAssignmentCleanupResult

    data object InvalidRequest : TankAssignmentCleanupResult

    data class Failure(
        val error: Throwable
    ) : TankAssignmentCleanupResult
}

sealed interface TankAssignmentRepairResult {
    data class Completed(
        val removedAssignments: List<TankDeviceAssignment>
    ) : TankAssignmentRepairResult

    data class Failure(
        val error: Throwable
    ) : TankAssignmentRepairResult
}
