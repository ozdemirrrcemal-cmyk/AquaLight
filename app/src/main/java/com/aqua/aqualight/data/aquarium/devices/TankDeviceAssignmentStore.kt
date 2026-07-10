package com.aqua.aqualight.data.aquarium.devices

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val TANK_DEVICE_ASSIGNMENTS_FILE =
    "tank_device_assignments_v1.pb"

private val Context.tankDeviceAssignmentsDataStore: DataStore<TankDeviceAssignmentsStore> by dataStore(
    fileName = TANK_DEVICE_ASSIGNMENTS_FILE,
    serializer = TankDeviceAssignmentsSerializer
)

data class TankDeviceAssignment(
    val ownerUid: String,
    val deviceUid: DeviceUid,
    val tankId: Long,
    val assignedAtMillis: Long,
    val updatedAtMillis: Long
)

sealed interface TankDeviceAssignmentWriteResult {
    data class Assigned(
        val assignment: TankDeviceAssignment
    ) : TankDeviceAssignmentWriteResult

    data class AlreadyAssigned(
        val assignment: TankDeviceAssignment
    ) : TankDeviceAssignmentWriteResult

    data class Conflict(
        val existingAssignment: TankDeviceAssignment
    ) : TankDeviceAssignmentWriteResult

    data class Removed(
        val assignment: TankDeviceAssignment
    ) : TankDeviceAssignmentWriteResult

    data object NotFound : TankDeviceAssignmentWriteResult
    data object InvalidInput : TankDeviceAssignmentWriteResult
}

data class TankDeviceAssignmentRepairResult(
    val removedInvalid: Int = 0,
    val removedMissingTank: Int = 0,
    val removedMissingDevice: Int = 0,
    val removedDuplicate: Int = 0
) {
    val removedTotal: Int
        get() =
            removedInvalid +
                removedMissingTank +
                removedMissingDevice +
                removedDuplicate
}

class TankDeviceAssignmentStore private constructor(
    context: Context
) {
    private val dataStore =
        context.applicationContext.tankDeviceAssignmentsDataStore

    fun assignmentsForOwner(
        ownerUid: String
    ): Flow<List<TankDeviceAssignment>> {
        val normalizedOwnerUid =
            UserDataScope.normalizeOwnerUid(ownerUid)

        if (normalizedOwnerUid.isBlank()) {
            return dataStore.data.map { emptyList() }
        }

        return dataStore.data.map { store ->
            store.assignmentsList
                .asSequence()
                .filter { stored ->
                    stored.ownerUid == normalizedOwnerUid
                }
                .mapNotNull { stored ->
                    stored.toDomainOrNull()
                }
                .sortedWith(ASSIGNMENT_ORDER)
                .toList()
        }
    }

    suspend fun assignmentsSnapshotForOwner(
        ownerUid: String
    ): List<TankDeviceAssignment> {
        return assignmentsForOwner(ownerUid).first()
    }

    suspend fun assign(
        ownerUid: String,
        tankId: Long,
        deviceUid: DeviceUid
    ): TankDeviceAssignmentWriteResult {
        val normalizedOwnerUid =
            UserDataScope.normalizeOwnerUid(ownerUid)
        val normalizedDeviceUid =
            deviceUid.value.trim()

        if (
            normalizedOwnerUid.isBlank() ||
            normalizedDeviceUid.isBlank() ||
            tankId <= 0L
        ) {
            return TankDeviceAssignmentWriteResult.InvalidInput
        }

        var result: TankDeviceAssignmentWriteResult =
            TankDeviceAssignmentWriteResult.InvalidInput

        dataStore.updateData { currentStore ->
            val existing = currentStore.assignmentsList
                .firstOrNull { stored ->
                    stored.ownerUid == normalizedOwnerUid &&
                        stored.deviceUid == normalizedDeviceUid
                }
                ?.toDomainOrNull()

            if (existing != null) {
                result = if (existing.tankId == tankId) {
                    TankDeviceAssignmentWriteResult.AlreadyAssigned(existing)
                } else {
                    TankDeviceAssignmentWriteResult.Conflict(existing)
                }

                return@updateData currentStore
            }

            val nowMillis = System.currentTimeMillis()
            val storedAssignment = StoredTankDeviceAssignment.newBuilder()
                .setOwnerUid(normalizedOwnerUid)
                .setDeviceUid(normalizedDeviceUid)
                .setTankId(tankId)
                .setAssignedAtMillis(nowMillis)
                .setUpdatedAtMillis(nowMillis)
                .build()

            result = TankDeviceAssignmentWriteResult.Assigned(
                assignment = requireNotNull(
                    storedAssignment.toDomainOrNull()
                )
            )

            currentStore.toBuilder()
                .addAssignments(storedAssignment)
                .build()
        }

        return result
    }

    suspend fun remove(
        ownerUid: String,
        tankId: Long,
        deviceUid: DeviceUid
    ): TankDeviceAssignmentWriteResult {
        val normalizedOwnerUid =
            UserDataScope.normalizeOwnerUid(ownerUid)
        val normalizedDeviceUid =
            deviceUid.value.trim()

        if (
            normalizedOwnerUid.isBlank() ||
            normalizedDeviceUid.isBlank() ||
            tankId <= 0L
        ) {
            return TankDeviceAssignmentWriteResult.InvalidInput
        }

        var result: TankDeviceAssignmentWriteResult =
            TankDeviceAssignmentWriteResult.NotFound

        dataStore.updateData { currentStore ->
            val removed = currentStore.assignmentsList
                .firstOrNull { stored ->
                    stored.ownerUid == normalizedOwnerUid &&
                        stored.deviceUid == normalizedDeviceUid &&
                        stored.tankId == tankId
                }
                ?.toDomainOrNull()

            if (removed == null) {
                return@updateData currentStore
            }

            result = TankDeviceAssignmentWriteResult.Removed(removed)

            currentStore.toBuilder()
                .clearAssignments()
                .addAllAssignments(
                    currentStore.assignmentsList.filterNot { stored ->
                        stored.ownerUid == normalizedOwnerUid &&
                            stored.deviceUid == normalizedDeviceUid &&
                            stored.tankId == tankId
                    }
                )
                .build()
        }

        return result
    }

    suspend fun removeForDevice(
        ownerUid: String,
        deviceUid: DeviceUid
    ): TankDeviceAssignmentWriteResult {
        val normalizedOwnerUid =
            UserDataScope.normalizeOwnerUid(ownerUid)
        val normalizedDeviceUid =
            deviceUid.value.trim()

        if (
            normalizedOwnerUid.isBlank() ||
            normalizedDeviceUid.isBlank()
        ) {
            return TankDeviceAssignmentWriteResult.InvalidInput
        }

        var result: TankDeviceAssignmentWriteResult =
            TankDeviceAssignmentWriteResult.NotFound

        dataStore.updateData { currentStore ->
            val removed = currentStore.assignmentsList
                .firstOrNull { stored ->
                    stored.ownerUid == normalizedOwnerUid &&
                        stored.deviceUid == normalizedDeviceUid
                }
                ?.toDomainOrNull()

            if (removed == null) {
                return@updateData currentStore
            }

            result = TankDeviceAssignmentWriteResult.Removed(removed)

            currentStore.toBuilder()
                .clearAssignments()
                .addAllAssignments(
                    currentStore.assignmentsList.filterNot { stored ->
                        stored.ownerUid == normalizedOwnerUid &&
                            stored.deviceUid == normalizedDeviceUid
                    }
                )
                .build()
        }

        return result
    }

    suspend fun removeForTanks(
        ownerUid: String,
        tankIds: Set<Long>
    ): Int {
        val normalizedOwnerUid =
            UserDataScope.normalizeOwnerUid(ownerUid)
        val normalizedTankIds =
            tankIds.filter { tankId -> tankId > 0L }.toSet()

        if (
            normalizedOwnerUid.isBlank() ||
            normalizedTankIds.isEmpty()
        ) {
            return 0
        }

        var removedCount = 0

        dataStore.updateData { currentStore ->
            val remaining = currentStore.assignmentsList.filterNot { stored ->
                val remove =
                    stored.ownerUid == normalizedOwnerUid &&
                        stored.tankId in normalizedTankIds

                if (remove) {
                    removedCount += 1
                }

                remove
            }

            if (removedCount == 0) {
                currentStore
            } else {
                currentStore.toBuilder()
                    .clearAssignments()
                    .addAllAssignments(remaining)
                    .build()
            }
        }

        return removedCount
    }

    suspend fun clearOwner(
        ownerUid: String
    ): Int {
        val normalizedOwnerUid =
            UserDataScope.normalizeOwnerUid(ownerUid)

        if (normalizedOwnerUid.isBlank()) {
            return 0
        }

        var removedCount = 0

        dataStore.updateData { currentStore ->
            val remaining = currentStore.assignmentsList.filterNot { stored ->
                val remove = stored.ownerUid == normalizedOwnerUid

                if (remove) {
                    removedCount += 1
                }

                remove
            }

            if (removedCount == 0) {
                currentStore
            } else {
                currentStore.toBuilder()
                    .clearAssignments()
                    .addAllAssignments(remaining)
                    .build()
            }
        }

        return removedCount
    }

    suspend fun repair(
        ownerUid: String,
        validTankIds: Set<Long>,
        validDeviceUids: Set<DeviceUid>
    ): TankDeviceAssignmentRepairResult {
        val normalizedOwnerUid =
            UserDataScope.normalizeOwnerUid(ownerUid)

        if (normalizedOwnerUid.isBlank()) {
            return TankDeviceAssignmentRepairResult()
        }

        val validTanks = validTankIds.filter { tankId -> tankId > 0L }.toSet()
        val validDevices = validDeviceUids
            .map { deviceUid -> deviceUid.value.trim() }
            .filter { value -> value.isNotBlank() }
            .toSet()

        var removedInvalid = 0
        var removedMissingTank = 0
        var removedMissingDevice = 0
        var removedDuplicate = 0

        dataStore.updateData { currentStore ->
            val otherOwners = currentStore.assignmentsList.filterNot { stored ->
                stored.ownerUid == normalizedOwnerUid
            }
            val keptByDeviceUid = linkedMapOf<String, StoredTankDeviceAssignment>()

            currentStore.assignmentsList
                .asSequence()
                .filter { stored ->
                    stored.ownerUid == normalizedOwnerUid
                }
                .sortedWith(
                    compareByDescending<StoredTankDeviceAssignment> { stored ->
                        stored.updatedAtMillis
                    }.thenByDescending { stored ->
                        stored.assignedAtMillis
                    }
                )
                .forEach { stored ->
                    val deviceUid = stored.deviceUid.trim()

                    when {
                        deviceUid.isBlank() || stored.tankId <= 0L -> {
                            removedInvalid += 1
                        }

                        stored.tankId !in validTanks -> {
                            removedMissingTank += 1
                        }

                        deviceUid !in validDevices -> {
                            removedMissingDevice += 1
                        }

                        deviceUid in keptByDeviceUid -> {
                            removedDuplicate += 1
                        }

                        else -> {
                            keptByDeviceUid[deviceUid] = stored.toBuilder()
                                .setOwnerUid(normalizedOwnerUid)
                                .setDeviceUid(deviceUid)
                                .build()
                        }
                    }
                }

            currentStore.toBuilder()
                .clearAssignments()
                .addAllAssignments(otherOwners)
                .addAllAssignments(
                    keptByDeviceUid.values.sortedWith(STORED_ASSIGNMENT_ORDER)
                )
                .build()
        }

        return TankDeviceAssignmentRepairResult(
            removedInvalid = removedInvalid,
            removedMissingTank = removedMissingTank,
            removedMissingDevice = removedMissingDevice,
            removedDuplicate = removedDuplicate
        )
    }

    companion object {
        private val ASSIGNMENT_ORDER =
            compareBy<TankDeviceAssignment> { assignment ->
                assignment.assignedAtMillis
            }.thenBy { assignment ->
                assignment.deviceUid.value
            }

        private val STORED_ASSIGNMENT_ORDER =
            compareBy<StoredTankDeviceAssignment> { stored ->
                stored.assignedAtMillis
            }.thenBy { stored ->
                stored.deviceUid
            }

        @Volatile
        private var instance: TankDeviceAssignmentStore? = null

        fun get(
            context: Context
        ): TankDeviceAssignmentStore {
            return instance ?: synchronized(this) {
                instance ?: TankDeviceAssignmentStore(
                    context.applicationContext
                ).also { store ->
                    instance = store
                }
            }
        }
    }
}

private fun StoredTankDeviceAssignment.toDomainOrNull(): TankDeviceAssignment? {
    val normalizedOwnerUid =
        UserDataScope.normalizeOwnerUid(ownerUid)
    val normalizedDeviceUid =
        deviceUid.trim()

    if (
        normalizedOwnerUid.isBlank() ||
        normalizedDeviceUid.isBlank() ||
        tankId <= 0L
    ) {
        return null
    }

    val normalizedAssignedAt = assignedAtMillis
        .takeIf { value -> value > 0L }
        ?: updatedAtMillis.takeIf { value -> value > 0L }
        ?: 1L
    val normalizedUpdatedAt = updatedAtMillis
        .takeIf { value -> value > 0L }
        ?: normalizedAssignedAt

    return TankDeviceAssignment(
        ownerUid = normalizedOwnerUid,
        deviceUid = DeviceUid(normalizedDeviceUid),
        tankId = tankId,
        assignedAtMillis = normalizedAssignedAt,
        updatedAtMillis = normalizedUpdatedAt
    )
}
