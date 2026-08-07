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
                rollbackActive(owner, pending)
            }
        }
    }

    private suspend fun rollbackActive(
        ownerUid: String,
        pending: PendingUserDataRestore
    ): Result {
        val failures = mutableListOf<Throwable>()
        val removedAssignments = rollbackAssignments(pending, failures)
        val removedTasks = rollbackTasks(pending, failures)
        val newTankIds = rollbackTanks(ownerUid, pending, failures)
        finishRollback(ownerUid, failures)
        return Result(
            rolledBackTankCount = newTankIds.size,
            rolledBackTaskCount = removedTasks,
            rolledBackAssignmentCount = removedAssignments,
            clearedCommittedTransaction = false
        )
    }

    private suspend fun rollbackAssignments(
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

    private suspend fun rollbackTasks(
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

    private suspend fun rollbackTanks(
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
