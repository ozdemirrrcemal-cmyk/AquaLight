package com.aqua.aqualight.data.aquarium.devices

import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

data class AssignedTankDevice(
    val snapshot: DeviceSnapshot,
    val assignment: TankDeviceAssignment,
    val tankName: String
)

data class AssignedTankSummary(
    val tankId: Long,
    val tankName: String,
    val assignedAtMillis: Long
)

sealed interface AssignDeviceToTankResult {
    data class Assigned(
        val assignment: TankDeviceAssignment
    ) : AssignDeviceToTankResult

    data class AlreadyAssigned(
        val tankId: Long,
        val tankName: String
    ) : AssignDeviceToTankResult

    data class Conflict(
        val tankId: Long,
        val tankName: String
    ) : AssignDeviceToTankResult

    data object TankNotFound : AssignDeviceToTankResult
    data object DeviceNotFound : AssignDeviceToTankResult
    data object Unauthenticated : AssignDeviceToTankResult
    data object InvalidInput : AssignDeviceToTankResult
}

sealed interface RemoveDeviceAssignmentResult {
    data class Removed(
        val assignment: TankDeviceAssignment
    ) : RemoveDeviceAssignmentResult

    data object NotAssigned : RemoveDeviceAssignmentResult
    data object Unauthenticated : RemoveDeviceAssignmentResult
    data object InvalidInput : RemoveDeviceAssignmentResult
}

class TankDeviceAssignmentRepository(
    private val devicesRepository: DevicesRepository,
    private val assignmentStore: TankDeviceAssignmentStore,
    private val tankDataStoreManager: AquariumTankDataStoreManager,
    private val ownerUidProvider: () -> String = UserDataScope::currentUid
) {
    fun assignedDevicesForTank(
        tankId: Long
    ): Flow<List<AssignedTankDevice>> {
        if (tankId <= 0L) {
            return flowOf(emptyList())
        }

        return combine(
            devicesRepository.devices,
            tankDataStoreManager.tanksFlow,
            assignmentsForCurrentOwner()
        ) { devices, tanks, assignments ->
            val tank = tanks.firstOrNull { item ->
                item.id == tankId
            } ?: return@combine emptyList()
            val deviceByUid = devices.associateBy { snapshot ->
                snapshot.deviceUid
            }

            assignments
                .asSequence()
                .filter { assignment ->
                    assignment.tankId == tankId
                }
                .sortedBy { assignment ->
                    assignment.assignedAtMillis
                }
                .mapNotNull { assignment ->
                    deviceByUid[assignment.deviceUid]?.let { snapshot ->
                        AssignedTankDevice(
                            snapshot = snapshot,
                            assignment = assignment,
                            tankName = tank.name
                        )
                    }
                }
                .toList()
        }
    }

    fun availableDevicesForTank(
        tankId: Long
    ): Flow<List<DeviceSnapshot>> {
        if (tankId <= 0L) {
            return flowOf(emptyList())
        }

        return combine(
            devicesRepository.devices,
            tankDataStoreManager.tanksFlow,
            assignmentsForCurrentOwner()
        ) { devices, tanks, assignments ->
            if (tanks.none { tank -> tank.id == tankId }) {
                return@combine emptyList()
            }

            val assignedDeviceUids = assignments
                .map { assignment -> assignment.deviceUid }
                .toSet()

            devices
                .filter { snapshot ->
                    snapshot.deviceUid !in assignedDeviceUids
                }
                .sortedWith(
                    compareBy<DeviceSnapshot> { snapshot ->
                        snapshot.title.lowercase()
                    }.thenBy { snapshot ->
                        snapshot.deviceUid.value
                    }
                )
        }
    }

    fun assignedTankByDevice(): Flow<Map<DeviceUid, AssignedTankSummary>> {
        return combine(
            tankDataStoreManager.tanksFlow,
            assignmentsForCurrentOwner()
        ) { tanks, assignments ->
            val tankById = tanks.associateBy { tank -> tank.id }

            assignments.mapNotNull { assignment ->
                val tank = tankById[assignment.tankId]
                    ?: return@mapNotNull null

                assignment.deviceUid to AssignedTankSummary(
                    tankId = tank.id,
                    tankName = tank.name,
                    assignedAtMillis = assignment.assignedAtMillis
                )
            }.toMap()
        }
    }

    suspend fun assignDeviceToTank(
        tankId: Long,
        deviceUid: DeviceUid
    ): AssignDeviceToTankResult {
        val ownerUid = currentOwnerUidOrNull()
            ?: return AssignDeviceToTankResult.Unauthenticated
        val normalizedDeviceUid = deviceUid.value.trim()

        if (tankId <= 0L || normalizedDeviceUid.isBlank()) {
            return AssignDeviceToTankResult.InvalidInput
        }

        val tanks = tankDataStoreManager.tanksSnapshotForOwner(ownerUid)
        tanks.firstOrNull { tank ->
            tank.id == tankId
        } ?: return AssignDeviceToTankResult.TankNotFound

        val normalizedUid = DeviceUid(normalizedDeviceUid)
        val deviceExists = devicesRepository.currentDevice(normalizedUid) != null ||
            devicesRepository.knownDeviceUids().contains(normalizedUid)

        if (!deviceExists) {
            return AssignDeviceToTankResult.DeviceNotFound
        }

        return when (
            val result = assignmentStore.assign(
                ownerUid = ownerUid,
                tankId = tankId,
                deviceUid = normalizedUid
            )
        ) {
            is TankDeviceAssignmentWriteResult.Assigned -> {
                AssignDeviceToTankResult.Assigned(result.assignment)
            }

            is TankDeviceAssignmentWriteResult.AlreadyAssigned -> {
                result.assignment.toAlreadyAssignedResult(tanks)
            }

            is TankDeviceAssignmentWriteResult.Conflict -> {
                result.existingAssignment.toConflictResult(tanks)
            }

            TankDeviceAssignmentWriteResult.InvalidInput -> {
                AssignDeviceToTankResult.InvalidInput
            }

            is TankDeviceAssignmentWriteResult.Removed,
            TankDeviceAssignmentWriteResult.NotFound -> {
                AssignDeviceToTankResult.InvalidInput
            }
        }
    }

    suspend fun removeDeviceFromTank(
        tankId: Long,
        deviceUid: DeviceUid
    ): RemoveDeviceAssignmentResult {
        val ownerUid = currentOwnerUidOrNull()
            ?: return RemoveDeviceAssignmentResult.Unauthenticated

        if (tankId <= 0L || deviceUid.value.isBlank()) {
            return RemoveDeviceAssignmentResult.InvalidInput
        }

        return assignmentStore.remove(
            ownerUid = ownerUid,
            tankId = tankId,
            deviceUid = deviceUid
        ).toRemoveResult()
    }

    suspend fun removeDeviceFromAnyTank(
        deviceUid: DeviceUid
    ): RemoveDeviceAssignmentResult {
        val ownerUid = currentOwnerUidOrNull()
            ?: return RemoveDeviceAssignmentResult.Unauthenticated

        if (deviceUid.value.isBlank()) {
            return RemoveDeviceAssignmentResult.InvalidInput
        }

        return assignmentStore.removeForDevice(
            ownerUid = ownerUid,
            deviceUid = deviceUid
        ).toRemoveResult()
    }

    suspend fun removeAssignmentsForTanks(
        tankIds: Set<Long>
    ): Int {
        val ownerUid = currentOwnerUidOrNull()
            ?: return 0

        return assignmentStore.removeForTanks(
            ownerUid = ownerUid,
            tankIds = tankIds
        )
    }

    suspend fun clearAssignmentsForOwner(
        ownerUid: String
    ): Int {
        return assignmentStore.clearOwner(ownerUid)
    }

    suspend fun repairStaleAssignments(): TankDeviceAssignmentRepairResult {
        val ownerUid = currentOwnerUidOrNull()
            ?: return TankDeviceAssignmentRepairResult()
        val validTankIds = tankDataStoreManager
            .tanksSnapshotForOwner(ownerUid)
            .map { tank -> tank.id }
            .toSet()
        val validDeviceUids = devicesRepository.knownDeviceUids()

        return assignmentStore.repair(
            ownerUid = ownerUid,
            validTankIds = validTankIds,
            validDeviceUids = validDeviceUids
        )
    }

    private fun assignmentsForCurrentOwner(): Flow<List<TankDeviceAssignment>> {
        val ownerUid = currentOwnerUidOrNull()
            ?: return flowOf(emptyList())

        return assignmentStore.assignmentsForOwner(ownerUid)
    }

    private fun currentOwnerUidOrNull(): String? {
        return UserDataScope.normalizeOwnerUid(
            ownerUidProvider()
        ).takeIf { ownerUid ->
            ownerUid.isNotBlank()
        }
    }

    private fun TankDeviceAssignment.toAlreadyAssignedResult(
        tanks: List<SavedAquariumTank>
    ): AssignDeviceToTankResult.AlreadyAssigned {
        return AssignDeviceToTankResult.AlreadyAssigned(
            tankId = tankId,
            tankName = tanks.nameFor(tankId)
        )
    }

    private fun TankDeviceAssignment.toConflictResult(
        tanks: List<SavedAquariumTank>
    ): AssignDeviceToTankResult.Conflict {
        return AssignDeviceToTankResult.Conflict(
            tankId = tankId,
            tankName = tanks.nameFor(tankId)
        )
    }

    private fun List<SavedAquariumTank>.nameFor(
        tankId: Long
    ): String {
        return firstOrNull { tank -> tank.id == tankId }
            ?.name
            .orEmpty()
    }

    private fun TankDeviceAssignmentWriteResult.toRemoveResult(): RemoveDeviceAssignmentResult {
        return when (this) {
            is TankDeviceAssignmentWriteResult.Removed -> {
                RemoveDeviceAssignmentResult.Removed(assignment)
            }

            TankDeviceAssignmentWriteResult.NotFound -> {
                RemoveDeviceAssignmentResult.NotAssigned
            }

            TankDeviceAssignmentWriteResult.InvalidInput -> {
                RemoveDeviceAssignmentResult.InvalidInput
            }

            is TankDeviceAssignmentWriteResult.Assigned,
            is TankDeviceAssignmentWriteResult.AlreadyAssigned,
            is TankDeviceAssignmentWriteResult.Conflict -> {
                RemoveDeviceAssignmentResult.InvalidInput
            }
        }
    }
}
