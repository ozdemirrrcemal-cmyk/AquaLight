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

data class DeviceTankAssignmentSummary(
    val tankId: Long,
    val tankName: String,
    val assignedAtMillis: Long
)

sealed interface AssignDeviceToTankResult {
    data class Assigned(
        val assignment: TankDeviceAssignment
    ) : AssignDeviceToTankResult

    data class AlreadyAssignedToTank(
        val tankId: Long,
        val tankName: String
    ) : AssignDeviceToTankResult

    data object TankNotFound : AssignDeviceToTankResult
    data object DeviceNotFound : AssignDeviceToTankResult
    data object Unauthenticated : AssignDeviceToTankResult
    data object InvalidInput : AssignDeviceToTankResult
}

sealed interface RemoveDeviceFromTankResult {
    data class Removed(
        val assignment: TankDeviceAssignment
    ) : RemoveDeviceFromTankResult

    data object NotAssigned : RemoveDeviceFromTankResult
    data object Unauthenticated : RemoveDeviceFromTankResult
    data object InvalidInput : RemoveDeviceFromTankResult
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
            val tankExists = tanks.any { tank ->
                tank.id == tankId
            }

            if (!tankExists) {
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

    fun assignmentSummariesByDevice(): Flow<Map<DeviceUid, DeviceTankAssignmentSummary>> {
        return combine(
            tankDataStoreManager.tanksFlow,
            assignmentsForCurrentOwner()
        ) { tanks, assignments ->
            val tankById = tanks.associateBy { tank ->
                tank.id
            }

            assignments.mapNotNull { assignment ->
                val tank = tankById[assignment.tankId]
                    ?: return@mapNotNull null

                assignment.deviceUid to DeviceTankAssignmentSummary(
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
        val visibleDevice = devicesRepository.currentDevice(normalizedUid)

        if (visibleDevice == null) {
            val durableDeviceExists = devicesRepository.knownDeviceUids()
                .any { knownDeviceUid ->
                    knownDeviceUid.value == normalizedDeviceUid
                }

            if (!durableDeviceExists) {
                return AssignDeviceToTankResult.DeviceNotFound
            }
        }

        val existingAssignment = assignmentStore
            .assignmentsSnapshotForOwner(ownerUid)
            .firstOrNull { assignment ->
                assignment.deviceUid.value == normalizedDeviceUid
            }

        if (existingAssignment != null) {
            return existingAssignment.toAlreadyAssignedResult(tanks)
        }

        return when (
            val result = assignmentStore.assignDeviceToTank(
                ownerUid = ownerUid,
                tankId = tankId,
                deviceUid = normalizedUid
            )
        ) {
            is TankDeviceAssignmentMutationResult.Assigned -> {
                AssignDeviceToTankResult.Assigned(
                    assignment = result.assignment
                )
            }

            is TankDeviceAssignmentMutationResult.AlreadyAssigned -> {
                result.assignment.toAlreadyAssignedResult(tanks)
            }

            TankDeviceAssignmentMutationResult.InvalidInput -> {
                AssignDeviceToTankResult.InvalidInput
            }

            is TankDeviceAssignmentMutationResult.Removed,
            TankDeviceAssignmentMutationResult.NotFound -> {
                AssignDeviceToTankResult.InvalidInput
            }
        }
    }

    suspend fun removeDeviceFromTank(
        tankId: Long,
        deviceUid: DeviceUid
    ): RemoveDeviceFromTankResult {
        val ownerUid = currentOwnerUidOrNull()
            ?: return RemoveDeviceFromTankResult.Unauthenticated

        if (tankId <= 0L || deviceUid.value.isBlank()) {
            return RemoveDeviceFromTankResult.InvalidInput
        }

        return when (
            val result = assignmentStore.removeDeviceFromTank(
                ownerUid = ownerUid,
                tankId = tankId,
                deviceUid = deviceUid
            )
        ) {
            is TankDeviceAssignmentMutationResult.Removed -> {
                RemoveDeviceFromTankResult.Removed(
                    assignment = result.assignment
                )
            }

            TankDeviceAssignmentMutationResult.NotFound -> {
                RemoveDeviceFromTankResult.NotAssigned
            }

            TankDeviceAssignmentMutationResult.InvalidInput -> {
                RemoveDeviceFromTankResult.InvalidInput
            }

            is TankDeviceAssignmentMutationResult.Assigned,
            is TankDeviceAssignmentMutationResult.AlreadyAssigned -> {
                RemoveDeviceFromTankResult.InvalidInput
            }
        }
    }

    suspend fun removeDeviceFromAnyTank(
        deviceUid: DeviceUid
    ): RemoveDeviceFromTankResult {
        val ownerUid = currentOwnerUidOrNull()
            ?: return RemoveDeviceFromTankResult.Unauthenticated

        if (deviceUid.value.isBlank()) {
            return RemoveDeviceFromTankResult.InvalidInput
        }

        return when (
            val result = assignmentStore.removeDeviceFromAnyTank(
                ownerUid = ownerUid,
                deviceUid = deviceUid
            )
        ) {
            is TankDeviceAssignmentMutationResult.Removed -> {
                RemoveDeviceFromTankResult.Removed(
                    assignment = result.assignment
                )
            }

            TankDeviceAssignmentMutationResult.NotFound -> {
                RemoveDeviceFromTankResult.NotAssigned
            }

            TankDeviceAssignmentMutationResult.InvalidInput -> {
                RemoveDeviceFromTankResult.InvalidInput
            }

            is TankDeviceAssignmentMutationResult.Assigned,
            is TankDeviceAssignmentMutationResult.AlreadyAssigned -> {
                RemoveDeviceFromTankResult.InvalidInput
            }
        }
    }

    suspend fun removeAssignmentsForTanks(
        tankIds: Set<Long>
    ): Int {
        val ownerUid = currentOwnerUidOrNull()
            ?: return 0

        return assignmentStore.removeAssignmentsForTanks(
            ownerUid = ownerUid,
            tankIds = tankIds
        )
    }

    suspend fun clearAssignmentsForOwner(
        ownerUid: String
    ): Int {
        return assignmentStore.clearForOwner(
            ownerUid = ownerUid
        )
    }

    suspend fun repairStaleAssignments(): TankDeviceAssignmentRepairReport {
        val ownerUid = currentOwnerUidOrNull()
            ?: return TankDeviceAssignmentRepairReport()
        val validTankIds = tankDataStoreManager
            .tanksSnapshotForOwner(ownerUid)
            .map { tank -> tank.id }
            .toSet()
        val validDeviceUids = devicesRepository.knownDeviceUids()

        return assignmentStore.repairAssignments(
            ownerUid = ownerUid,
            validTankIds = validTankIds,
            validDeviceUids = validDeviceUids
        )
    }

    private fun TankDeviceAssignment.toAlreadyAssignedResult(
        tanks: List<SavedAquariumTank>
    ): AssignDeviceToTankResult.AlreadyAssignedToTank {
        val tankName = tanks
            .firstOrNull { tank ->
                tank.id == tankId
            }
            ?.name
            .orEmpty()

        return AssignDeviceToTankResult.AlreadyAssignedToTank(
            tankId = tankId,
            tankName = tankName
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
}
