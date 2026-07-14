package com.aqua.aqualight.data.aquarium.devices

internal class TankDeviceAssignmentsValidationException(
    message: String
) : IllegalStateException(message)

internal sealed interface TankDeviceStoreAssignDecision {
    data class Assigned(
        val assignment: TankDeviceAssignment
    ) : TankDeviceStoreAssignDecision

    data class AlreadyAssigned(
        val assignment: TankDeviceAssignment
    ) : TankDeviceStoreAssignDecision

    data class Conflict(
        val existingAssignment: TankDeviceAssignment
    ) : TankDeviceStoreAssignDecision
}

internal data class TankDeviceStoreAssignMutation(
    val store: TankDeviceAssignmentsStore,
    val decision: TankDeviceStoreAssignDecision
)

internal data class TankDeviceStoreRepairMutation(
    val store: TankDeviceAssignmentsStore,
    val removedAssignments: List<TankDeviceAssignment>
)

internal object TankDeviceAssignmentRules {

    fun validate(
        store: TankDeviceAssignmentsStore
    ) {
        val deviceOwnerKeys = mutableSetOf<String>()

        store.getAssignmentsList().forEachIndexed { index, assignment ->
            val ownerUid = assignment.ownerUid
            val deviceUid = assignment.deviceUid

            if (ownerUid.isBlank() || ownerUid != ownerUid.trim()) {
                invalid(index, "owner_uid must be non-blank and trimmed")
            }

            if (assignment.tankId <= 0L) {
                invalid(index, "tank_id must be positive")
            }

            if (deviceUid.isBlank() || deviceUid != deviceUid.trim()) {
                invalid(index, "device_uid must be non-blank and trimmed")
            }

            if (assignment.assignedAtMillis <= 0L) {
                invalid(index, "assigned_at_millis must be positive")
            }

            val ownerDeviceKey = ownerDeviceKey(
                ownerUid = ownerUid,
                deviceUid = deviceUid
            )

            if (!deviceOwnerKeys.add(ownerDeviceKey)) {
                invalid(
                    index = index,
                    reason = "an owner can assign a device to only one tank"
                )
            }
        }
    }

    fun assign(
        store: TankDeviceAssignmentsStore,
        ownerUid: String,
        tankId: Long,
        deviceUid: String,
        assignedAtMillis: Long
    ): TankDeviceStoreAssignMutation {
        val normalizedOwnerUid = ownerUid.requireNormalizedOwnerUid()
        val normalizedDeviceUid = deviceUid.requireNormalizedDeviceUid()

        require(tankId > 0L) {
            "tankId must be positive"
        }
        require(assignedAtMillis > 0L) {
            "assignedAtMillis must be positive"
        }

        validate(store)

        val existing = store.getAssignmentsList().firstOrNull { assignment ->
            assignment.ownerUid == normalizedOwnerUid &&
                assignment.deviceUid == normalizedDeviceUid
        }

        if (existing != null) {
            val existingDomain = existing.toDomain()
            val decision = if (existing.tankId == tankId) {
                TankDeviceStoreAssignDecision.AlreadyAssigned(existingDomain)
            } else {
                TankDeviceStoreAssignDecision.Conflict(existingDomain)
            }

            return TankDeviceStoreAssignMutation(
                store = store,
                decision = decision
            )
        }

        val assignment = TankDeviceAssignment(
            ownerUid = normalizedOwnerUid,
            tankId = tankId,
            deviceUid = com.aqua.aqualight.data.devices.model.DeviceUid(normalizedDeviceUid),
            assignedAtMillis = assignedAtMillis
        )

        val updatedStore = store.toBuilder()
            .addAssignments(assignment.toStored())
            .build()

        validate(updatedStore)

        return TankDeviceStoreAssignMutation(
            store = updatedStore,
            decision = TankDeviceStoreAssignDecision.Assigned(assignment)
        )
    }

    fun removeFromTank(
        store: TankDeviceAssignmentsStore,
        ownerUid: String,
        tankId: Long,
        deviceUid: String
    ): Pair<TankDeviceAssignmentsStore, Boolean> {
        val normalizedOwnerUid = ownerUid.requireNormalizedOwnerUid()
        val normalizedDeviceUid = deviceUid.requireNormalizedDeviceUid()

        require(tankId > 0L) {
            "tankId must be positive"
        }

        return removeMatching(store) { assignment ->
            assignment.ownerUid == normalizedOwnerUid &&
                assignment.tankId == tankId &&
                assignment.deviceUid == normalizedDeviceUid
        }
    }

    fun removeDevice(
        store: TankDeviceAssignmentsStore,
        ownerUid: String,
        deviceUid: String
    ): Pair<TankDeviceAssignmentsStore, Boolean> {
        val normalizedOwnerUid = ownerUid.requireNormalizedOwnerUid()
        val normalizedDeviceUid = deviceUid.requireNormalizedDeviceUid()

        return removeMatching(store) { assignment ->
            assignment.ownerUid == normalizedOwnerUid &&
                assignment.deviceUid == normalizedDeviceUid
        }
    }

    fun removeTank(
        store: TankDeviceAssignmentsStore,
        ownerUid: String,
        tankId: Long
    ): Pair<TankDeviceAssignmentsStore, Int> {
        val normalizedOwnerUid = ownerUid.requireNormalizedOwnerUid()

        require(tankId > 0L) {
            "tankId must be positive"
        }

        return removeMatchingCount(store) { assignment ->
            assignment.ownerUid == normalizedOwnerUid &&
                assignment.tankId == tankId
        }
    }

    fun clearOwner(
        store: TankDeviceAssignmentsStore,
        ownerUid: String
    ): Pair<TankDeviceAssignmentsStore, Int> {
        val normalizedOwnerUid = ownerUid.requireNormalizedOwnerUid()

        return removeMatchingCount(store) { assignment ->
            assignment.ownerUid == normalizedOwnerUid
        }
    }

    fun repairOwner(
        store: TankDeviceAssignmentsStore,
        ownerUid: String,
        validTankIds: Set<Long>,
        validDeviceUids: Set<String>
    ): TankDeviceStoreRepairMutation {
        val normalizedOwnerUid = ownerUid.requireNormalizedOwnerUid()
        val normalizedDeviceUids = validDeviceUids.map { deviceUid ->
            deviceUid.requireNormalizedDeviceUid()
        }.toSet()

        validate(store)

        val removedAssignments = mutableListOf<TankDeviceAssignment>()
        val keptAssignments = store.getAssignmentsList().filter { assignment ->
            val belongsToOwner = assignment.ownerUid == normalizedOwnerUid
            val isStale = belongsToOwner && (
                assignment.tankId !in validTankIds ||
                    assignment.deviceUid !in normalizedDeviceUids
                )

            if (isStale) {
                removedAssignments += assignment.toDomain()
            }

            !isStale
        }

        val updatedStore = if (removedAssignments.isEmpty()) {
            store
        } else {
            store.toBuilder()
                .clearAssignments()
                .addAllAssignments(keptAssignments)
                .build()
        }

        validate(updatedStore)

        return TankDeviceStoreRepairMutation(
            store = updatedStore,
            removedAssignments = removedAssignments.toList()
        )
    }

    private fun removeMatching(
        store: TankDeviceAssignmentsStore,
        predicate: (StoredTankDeviceAssignment) -> Boolean
    ): Pair<TankDeviceAssignmentsStore, Boolean> {
        val (updatedStore, removedCount) = removeMatchingCount(
            store = store,
            predicate = predicate
        )

        return updatedStore to (removedCount > 0)
    }

    private fun removeMatchingCount(
        store: TankDeviceAssignmentsStore,
        predicate: (StoredTankDeviceAssignment) -> Boolean
    ): Pair<TankDeviceAssignmentsStore, Int> {
        validate(store)

        var removedCount = 0
        val keptAssignments = store.getAssignmentsList().filter { assignment ->
            val remove = predicate(assignment)
            if (remove) {
                removedCount += 1
            }
            !remove
        }

        if (removedCount == 0) {
            return store to 0
        }

        val updatedStore = store.toBuilder()
            .clearAssignments()
            .addAllAssignments(keptAssignments)
            .build()

        validate(updatedStore)

        return updatedStore to removedCount
    }

    private fun ownerDeviceKey(
        ownerUid: String,
        deviceUid: String
    ): String {
        return "$ownerUid\u0000$deviceUid"
    }

    private fun String.requireNormalizedOwnerUid(): String {
        val normalized = trim()
        require(normalized.isNotBlank()) {
            "ownerUid must not be blank"
        }
        return normalized
    }

    private fun String.requireNormalizedDeviceUid(): String {
        val normalized = trim()
        require(normalized.isNotBlank()) {
            "deviceUid must not be blank"
        }
        return normalized
    }

    private fun invalid(
        index: Int,
        reason: String
    ): Nothing {
        throw TankDeviceAssignmentsValidationException(
            "Invalid tank assignment at index $index: $reason."
        )
    }
}
