package com.aqua.aqualight.data.aquarium.devices

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val TANK_DEVICE_ASSIGNMENTS_DATASTORE_FILE =
    "tank_device_assignments_v1.pb"

private val Context.tankDeviceAssignmentsDataStore: DataStore<TankDeviceAssignmentsStore> by dataStore(
    fileName = TANK_DEVICE_ASSIGNMENTS_DATASTORE_FILE,
    serializer = TankDeviceAssignmentsSerializer
)

data class TankDeviceAssignment(
    val ownerUid: String,
    val tankId: Long,
    val deviceUid: DeviceUid,
    val assignedAtMillis: Long,
    val updatedAtMillis: Long
)

sealed interface TankDeviceAssignmentMutationResult {
    data class Assigned(
        val assignment: TankDeviceAssignment,
        val previousTankId: Long?
    ) : TankDeviceAssignmentMutationResult

    data class AlreadyAssigned(
        val assignment: TankDeviceAssignment
    ) : TankDeviceAssignmentMutationResult

    data class Removed(
        val assignment: TankDeviceAssignment
    ) : TankDeviceAssignmentMutationResult

    data object NotFound : TankDeviceAssignmentMutationResult
    data object InvalidInput : TankDeviceAssignmentMutationResult
}

data class TankDeviceAssignmentRepairReport(
    val removedInvalidCount: Int = 0,
    val removedMissingTankCount: Int = 0,
    val removedMissingDeviceCount: Int = 0,
    val removedDuplicateCount: Int = 0
) {
    val removedCount: Int
        get() =
            removedInvalidCount +
                removedMissingTankCount +
                removedMissingDeviceCount +
                removedDuplicateCount
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
            return dataStore.data.map {
                emptyList()
            }
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

    suspend fun assignDeviceToTank(
        ownerUid: String,
        tankId: Long,
        deviceUid: DeviceUid
    ): TankDeviceAssignmentMutationResult {
        val normalizedOwnerUid =
            UserDataScope.normalizeOwnerUid(ownerUid)
        val normalizedDeviceUid =
            deviceUid.value.trim()

        if (
            normalizedOwnerUid.isBlank() ||
            tankId <= 0L ||
            normalizedDeviceUid.isBlank()
        ) {
            return TankDeviceAssignmentMutationResult.InvalidInput
        }

        var result: TankDeviceAssignmentMutationResult =
            TankDeviceAssignmentMutationResult.InvalidInput

        dataStore.updateData { currentStore ->
            val existing = currentStore.assignmentsList
                .firstOrNull { stored ->
                    stored.ownerUid == normalizedOwnerUid &&
                        stored.deviceUid == normalizedDeviceUid
                }

            // One device may belong to only one aquarium. The conflict check is
            // inside the atomic DataStore update so two concurrent UI requests
            // can never silently move the same device between aquariums.
            if (existing != null) {
                result = existing.toDomainOrNull()
                    ?.let(
                        TankDeviceAssignmentMutationResult::AlreadyAssigned
                    )
                    ?: TankDeviceAssignmentMutationResult.InvalidInput

                return@updateData currentStore
            }

            val nowMillis =
                System.currentTimeMillis()
            val nextAssignment =
                StoredTankDeviceAssignment.newBuilder()
                    .setOwnerUid(normalizedOwnerUid)
                    .setTankId(tankId)
                    .setDeviceUid(normalizedDeviceUid)
                    .setAssignedAtMillis(nowMillis)
                    .setUpdatedAtMillis(nowMillis)
                    .build()

            val nextAssignments = currentStore.assignmentsList
                .plus(nextAssignment)

            result = TankDeviceAssignmentMutationResult.Assigned(
                assignment = requireNotNull(
                    nextAssignment.toDomainOrNull()
                ),
                previousTankId = null
            )

            currentStore.toBuilder()
                .clearAssignments()
                .addAllAssignments(nextAssignments)
                .build()
        }

        return result
    }

    suspend fun removeDeviceFromTank(
        ownerUid: String,
        tankId: Long,
        deviceUid: DeviceUid
    ): TankDeviceAssignmentMutationResult {
        val normalizedOwnerUid =
            UserDataScope.normalizeOwnerUid(ownerUid)
        val normalizedDeviceUid =
            deviceUid.value.trim()

        if (
            normalizedOwnerUid.isBlank() ||
            tankId <= 0L ||
            normalizedDeviceUid.isBlank()
        ) {
            return TankDeviceAssignmentMutationResult.InvalidInput
        }

        var result: TankDeviceAssignmentMutationResult =
            TankDeviceAssignmentMutationResult.NotFound

        dataStore.updateData { currentStore ->
            val removed = currentStore.assignmentsList
                .firstOrNull { stored ->
                    stored.ownerUid == normalizedOwnerUid &&
                        stored.tankId == tankId &&
                        stored.deviceUid == normalizedDeviceUid
                }

            if (removed == null) {
                return@updateData currentStore
            }

            result = removed.toDomainOrNull()
                ?.let(
                    TankDeviceAssignmentMutationResult::Removed
                )
                ?: TankDeviceAssignmentMutationResult.NotFound

            currentStore.toBuilder()
                .clearAssignments()
                .addAllAssignments(
                    currentStore.assignmentsList.filterNot { stored ->
                        stored.ownerUid == normalizedOwnerUid &&
                            stored.tankId == tankId &&
                            stored.deviceUid == normalizedDeviceUid
                    }
                )
                .build()
        }

        return result
    }

    suspend fun removeDeviceFromAnyTank(
        ownerUid: String,
        deviceUid: DeviceUid
    ): TankDeviceAssignmentMutationResult {
        val normalizedOwnerUid =
            UserDataScope.normalizeOwnerUid(ownerUid)
        val normalizedDeviceUid =
            deviceUid.value.trim()

        if (
            normalizedOwnerUid.isBlank() ||
            normalizedDeviceUid.isBlank()
        ) {
            return TankDeviceAssignmentMutationResult.InvalidInput
        }

        var result: TankDeviceAssignmentMutationResult =
            TankDeviceAssignmentMutationResult.NotFound

        dataStore.updateData { currentStore ->
            val removed = currentStore.assignmentsList
                .firstOrNull { stored ->
                    stored.ownerUid == normalizedOwnerUid &&
                        stored.deviceUid == normalizedDeviceUid
                }

            if (removed == null) {
                return@updateData currentStore
            }

            result = removed.toDomainOrNull()
                ?.let(
                    TankDeviceAssignmentMutationResult::Removed
                )
                ?: TankDeviceAssignmentMutationResult.NotFound

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

    suspend fun removeAssignmentsForTanks(
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
            val nextAssignments = currentStore.assignmentsList.filterNot { stored ->
                val shouldRemove =
                    stored.ownerUid == normalizedOwnerUid &&
                        stored.tankId in normalizedTankIds

                if (shouldRemove) {
                    removedCount += 1
                }

                shouldRemove
            }

            if (removedCount == 0) {
                currentStore
            } else {
                currentStore.toBuilder()
                    .clearAssignments()
                    .addAllAssignments(nextAssignments)
                    .build()
            }
        }

        return removedCount
    }

    suspend fun clearForOwner(
        ownerUid: String
    ): Int {
        val normalizedOwnerUid =
            UserDataScope.normalizeOwnerUid(ownerUid)

        if (normalizedOwnerUid.isBlank()) {
            return 0
        }

        var removedCount = 0

        dataStore.updateData { currentStore ->
            val nextAssignments = currentStore.assignmentsList.filterNot { stored ->
                val shouldRemove =
                    stored.ownerUid == normalizedOwnerUid

                if (shouldRemove) {
                    removedCount += 1
                }

                shouldRemove
            }

            if (removedCount == 0) {
                currentStore
            } else {
                currentStore.toBuilder()
                    .clearAssignments()
                    .addAllAssignments(nextAssignments)
                    .build()
            }
        }

        return removedCount
    }

    suspend fun repairAssignments(
        ownerUid: String,
        validTankIds: Set<Long>,
        validDeviceUids: Set<DeviceUid>
    ): TankDeviceAssignmentRepairReport {
        val normalizedOwnerUid =
            UserDataScope.normalizeOwnerUid(ownerUid)

        if (normalizedOwnerUid.isBlank()) {
            return TankDeviceAssignmentRepairReport()
        }

        val normalizedTankIds =
            validTankIds.filter { tankId -> tankId > 0L }.toSet()
        val normalizedDeviceUids =
            validDeviceUids
                .map { deviceUid -> deviceUid.value.trim() }
                .filter { value -> value.isNotBlank() }
                .toSet()

        var invalidCount = 0
        var missingTankCount = 0
        var missingDeviceCount = 0
        var duplicateCount = 0

        dataStore.updateData { currentStore ->
            val otherOwners = currentStore.assignmentsList.filterNot { stored ->
                stored.ownerUid == normalizedOwnerUid
            }

            val keptByDeviceUid =
                linkedMapOf<String, StoredTankDeviceAssignment>()

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
                    val deviceUidValue =
                        stored.deviceUid.trim()

                    when {
                        stored.tankId <= 0L || deviceUidValue.isBlank() -> {
                            invalidCount += 1
                        }

                        stored.tankId !in normalizedTankIds -> {
                            missingTankCount += 1
                        }

                        deviceUidValue !in normalizedDeviceUids -> {
                            missingDeviceCount += 1
                        }

                        deviceUidValue in keptByDeviceUid -> {
                            duplicateCount += 1
                        }

                        else -> {
                            keptByDeviceUid[deviceUidValue] =
                                stored.toBuilder()
                                    .setOwnerUid(normalizedOwnerUid)
                                    .setDeviceUid(deviceUidValue)
                                    .build()
                        }
                    }
                }

            val repairedOwnerAssignments = keptByDeviceUid.values
                .sortedWith(STORED_ASSIGNMENT_ORDER)

            currentStore.toBuilder()
                .clearAssignments()
                .addAllAssignments(otherOwners)
                .addAllAssignments(repairedOwnerAssignments)
                .build()
        }

        return TankDeviceAssignmentRepairReport(
            removedInvalidCount = invalidCount,
            removedMissingTankCount = missingTankCount,
            removedMissingDeviceCount = missingDeviceCount,
            removedDuplicateCount = duplicateCount
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
        private var INSTANCE: TankDeviceAssignmentStore? = null

        fun get(
            context: Context
        ): TankDeviceAssignmentStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TankDeviceAssignmentStore(
                    context = context.applicationContext
                ).also { store ->
                    INSTANCE = store
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
        tankId <= 0L ||
        normalizedDeviceUid.isBlank()
    ) {
        return null
    }

    val normalizedAssignedAtMillis =
        assignedAtMillis.takeIf { value -> value > 0L }
            ?: updatedAtMillis.takeIf { value -> value > 0L }
            ?: 1L
    val normalizedUpdatedAtMillis =
        updatedAtMillis.takeIf { value -> value > 0L }
            ?: normalizedAssignedAtMillis

    return TankDeviceAssignment(
        ownerUid = normalizedOwnerUid,
        tankId = tankId,
        deviceUid = DeviceUid(normalizedDeviceUid),
        assignedAtMillis = normalizedAssignedAtMillis,
        updatedAtMillis = normalizedUpdatedAtMillis
    )
}
