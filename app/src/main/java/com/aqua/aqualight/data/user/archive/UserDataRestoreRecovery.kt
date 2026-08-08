package com.aqua.aqualight.data.user.archive

import android.content.Context
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepositoryProvider
import com.aqua.aqualight.data.aquarium.devices.TankDeviceRemovalResult
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Recovers or finalizes a durable user-data restore transaction after interruption. */
internal class UserDataRestoreRecovery(
    private val dataSources: UserDataRestoreDataSources,
    private val transactions: UserDataRestoreTransactions,
    private val provenance: UserDataRestoreProvenance
) {

    data class Result(
        val rolledBackTankCount: Int,
        val rolledBackTaskCount: Int,
        val rolledBackAssignmentCount: Int,
        val clearedCommittedTransaction: Boolean
    )

    suspend fun recover(ownerUid: String): Result {
        val owner = canonicalRestoreOwnerUid(ownerUid)
        check(UserDataScope.requireCurrentUid() == owner) {
            "Restore recovery owner does not match the active owner."
        }
        val pending = transactions.pending(owner)
        return when {
            pending == null -> Result(0, 0, 0, false)
            pending.state == UserDataRestoreTransactionState.COMMITTED -> {
                transactions.clearOwner(owner)
                Result(0, 0, 0, true)
            }
            else -> withContext(NonCancellable) {
                if (pending.exactMutationTracking) {
                    rollbackExact(owner, pending)
                } else {
                    rollbackLegacy(owner, pending)
                }
            }
        }
    }

    private suspend fun rollbackExact(
        ownerUid: String,
        pending: PendingUserDataRestore
    ): Result {
        val failures = mutableListOf<Throwable>()
        val removedAssignments = rollbackExactAssignments(pending, failures)
        val removedTasks = rollbackExactTasks(pending, failures)
        val removedTanks = rollbackExactTanks(ownerUid, pending, failures)
        finishRollback(ownerUid, failures)
        return Result(
            rolledBackTankCount = removedTanks,
            rolledBackTaskCount = removedTasks,
            rolledBackAssignmentCount = removedAssignments,
            clearedCommittedTransaction = false
        )
    }

    private suspend fun rollbackExactAssignments(
        pending: PendingUserDataRestore,
        failures: MutableList<Throwable>
    ): Int {
        var removed = 0
        pending.createdAssignments.asReversed().forEach { expected ->
            val current = runCatching {
                dataSources.assignments.assignmentForDevice(expected.deviceUid)
            }.getOrElse { error ->
                failures += error
                null
            }
            if (
                current == null ||
                current.tankId != expected.tankId ||
                current.assignedAtMillis != expected.assignedAtMillis
            ) {
                return@forEach
            }
            val result = runCatching {
                dataSources.assignments.removeDeviceFromTank(
                    expected.tankId,
                    expected.deviceUid
                )
            }.getOrElse { error ->
                failures += error
                null
            }
            when (result) {
                TankDeviceRemovalResult.Removed -> removed += 1
                is TankDeviceRemovalResult.Failure -> failures += result.error
                TankDeviceRemovalResult.InvalidRequest -> failures += IllegalStateException(
                    "Restore journal contains an invalid device assignment."
                )
                TankDeviceRemovalResult.NotAssigned,
                null -> Unit
            }
        }
        return removed
    }

    private suspend fun rollbackExactTasks(
        pending: PendingUserDataRestore,
        failures: MutableList<Throwable>
    ): Int {
        val tasksBefore = runCatching { dataSources.careTasks.snapshot() }
            .getOrElse { error ->
                failures += error
                return 0
            }
        val tasksById = tasksBefore.associateBy { task -> task.id }
        var removed = 0
        pending.createdTasks.asReversed().forEach { expected ->
            val current = tasksById[expected.taskId]
            if (
                current == null ||
                current.tankId != expected.tankId ||
                current.createdAtMillis != expected.createdAtMillis
            ) {
                return@forEach
            }
            runCatching { dataSources.careTasks.deleteTask(expected.taskId) }
                .onSuccess { removed += 1 }
                .onFailure { error -> failures += error }
        }
        return removed
    }

    private suspend fun rollbackExactTanks(
        ownerUid: String,
        pending: PendingUserDataRestore,
        failures: MutableList<Throwable>
    ): Int {
        val tanksBefore = runCatching { dataSources.tanks.snapshotForOwner(ownerUid) }
            .getOrElse { error ->
                failures += error
                return 0
            }
        val tanksById = tanksBefore.associateBy { tank -> tank.id }
        val idsToDelete = pending.createdTanks.mapNotNull { expected ->
            val current = tanksById[expected.tankId]
            expected.tankId.takeIf {
                current != null && current.createdAtMillis == expected.createdAtMillis
            }
        }
        if (idsToDelete.isEmpty()) return 0
        return runCatching {
            dataSources.tanks.deleteTanks(idsToDelete)
            idsToDelete.size
        }.getOrElse { error ->
            failures += error
            0
        }
    }

    private suspend fun rollbackLegacy(
        ownerUid: String,
        pending: PendingUserDataRestore
    ): Result {
        val failures = mutableListOf<Throwable>()
        val removedAssignments = rollbackLegacyAssignments(pending, failures)
        val removedTasks = rollbackLegacyTasks(pending, failures)
        val newTankIds = rollbackLegacyTanks(ownerUid, pending, failures)
        finishRollback(ownerUid, failures)
        return Result(
            rolledBackTankCount = newTankIds.size,
            rolledBackTaskCount = removedTasks,
            rolledBackAssignmentCount = removedAssignments,
            clearedCommittedTransaction = false
        )
    }

    private suspend fun rollbackLegacyAssignments(
        pending: PendingUserDataRestore,
        failures: MutableList<Throwable>
    ): Int {
        var removed = 0
        pending.plannedAssignments.asReversed().forEach { assignment ->
            val result = runCatching {
                dataSources.assignments.removeDeviceFromTank(
                    assignment.tankId,
                    assignment.deviceUid
                )
            }.getOrElse { error ->
                failures += error
                null
            }
            when (result) {
                TankDeviceRemovalResult.Removed -> removed += 1
                is TankDeviceRemovalResult.Failure -> failures += result.error
                TankDeviceRemovalResult.InvalidRequest -> failures += IllegalStateException(
                    "Restore journal contains an invalid device assignment."
                )
                TankDeviceRemovalResult.NotAssigned,
                null -> Unit
            }
        }
        return removed
    }

    private suspend fun rollbackLegacyTasks(
        pending: PendingUserDataRestore,
        failures: MutableList<Throwable>
    ): Int {
        val tasksBefore = runCatching { dataSources.careTasks.snapshot() }
            .getOrElse { error ->
                failures += error
                emptyList()
            }
        pending.plannedTaskIds.asReversed().forEach { taskId ->
            runCatching { dataSources.careTasks.deleteTask(taskId) }
                .onFailure { error -> failures += error }
        }
        return tasksBefore.count { task -> task.id in pending.plannedTaskIds }
    }

    private suspend fun rollbackLegacyTanks(
        ownerUid: String,
        pending: PendingUserDataRestore,
        failures: MutableList<Throwable>
    ): List<Long> {
        val tanksBefore = runCatching { dataSources.tanks.snapshotForOwner(ownerUid) }
            .getOrElse { error ->
                failures += error
                emptyList()
            }
        val newTankIds = tanksBefore
            .map { tank -> tank.id }
            .filterNot(pending.existingTankIds::contains)
        if (newTankIds.isNotEmpty()) {
            runCatching { dataSources.tanks.deleteTanks(newTankIds) }
                .onFailure { error -> failures += error }
        }
        return newTankIds
    }

    private suspend fun finishRollback(
        ownerUid: String,
        failures: List<Throwable>
    ) {
        if (failures.isNotEmpty()) {
            val combined = IllegalStateException(
                "Interrupted user-data restore could not be fully recovered."
            )
            failures.forEach(combined::addSuppressed)
            throw combined
        }
        val currentTanks = dataSources.tanks.snapshotForOwner(ownerUid)
        val currentTasks = dataSources.careTasks.snapshot()
        provenance.reconcile(ownerUid, currentTanks, currentTasks)
        transactions.clearOwner(ownerUid)
    }

    companion object {
        fun create(context: Context, ownerUid: String): UserDataRestoreRecovery {
            val appContext = context.applicationContext
            val owner = canonicalRestoreOwnerUid(ownerUid)
            val assignmentRepository = requireNotNull(
                TankDeviceAssignmentRepositoryProvider.currentRepository(owner)
            ) {
                "Restore recovery requires the active owner assignment repository."
            }
            val archiveSources = UserDataArchiveDataSources(
                aquariumStore = AquariumTankDataStoreManager(appContext),
                careTaskStore = CareTaskDataStoreManager.create(appContext),
                assignmentRepository = assignmentRepository
            )
            val restoreSources = UserDataRestoreDataSources.from(archiveSources)
            val transactions = UserDataRestoreJournal(appContext)
            val provenance = UserDataRestoreProvenanceStore(appContext)
            return UserDataRestoreRecovery(
                dataSources = restoreSources,
                transactions = transactions,
                provenance = provenance
            )
        }
    }
}
