package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.application.user.UserDataRestoreResult
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentResult
import com.aqua.aqualight.data.aquarium.devices.TankDeviceRemovalResult
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.platform.media.UserDataArchiveMediaGateway
import java.util.concurrent.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal class UserDataBackupRestorer(
    private val ownerUid: String,
    private val aquariumStore: AquariumTankDataStoreManager,
    private val careTaskStore: CareTaskDataStoreManager,
    private val assignmentRepository: TankDeviceAssignmentRepository,
    private val mediaGateway: UserDataArchiveMediaGateway,
    private val reconcileCareReminders: suspend (String) -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {

    suspend fun restore(backup: DecodedUserDataBackup): UserDataRestoreResult {
        requireOwner()
        val rollback = RestoreRollbackState()
        return try {
            val tankIdMap = restoreAquariums(backup, rollback)
            restoreCareTasks(backup, tankIdMap, rollback)
            val assignmentResult = restoreAssignments(backup, tankIdMap, rollback)
            requireOwner()
            UserDataRestoreResult(
                restoredAquariumCount = tankIdMap.size,
                restoredCareTaskCount = rollback.createdTaskIds.size,
                restoredDeviceAssignmentCount = assignmentResult.restored,
                skippedDeviceAssignmentCount = assignmentResult.skipped,
                reminderReconciliationWarning = reconcileRemindersWithWarning()
            )
        } catch (cancellation: CancellationException) {
            rollbackCreatedData(rollback, cancellation)
            throw cancellation
        } catch (error: Throwable) {
            throw rollbackCreatedData(rollback, error)
        }
    }

    private suspend fun restoreAquariums(
        backup: DecodedUserDataBackup,
        rollback: RestoreRollbackState
    ): Map<Long, Long> {
        val tankIdMap = linkedMapOf<Long, Long>()
        backup.manifest.aquariums.forEach { archived ->
            requireOwner()
            val photoUri = archived.photo?.let { reference ->
                val bytes = requireNotNull(backup.mediaByEntryName[reference.entryName])
                mediaGateway.prepareRestoredTankPhoto(
                    ownerUid = ownerUid,
                    ownerToken = "restore_${archived.id}",
                    bytes = bytes
                )
            }
            val newTankId = try {
                aquariumStore.addTankFromDraft(archived.toTankDraft(photoUri))
            } catch (error: Throwable) {
                mediaGateway.rollback(photoUri)
                throw error
            }
            rollback.createdTankIds += newTankId
            tankIdMap[archived.id] = newTankId
            restoreAquariumDetails(archived, newTankId)
            mediaGateway.commit(photoUri)
        }
        return tankIdMap.toMap()
    }

    private suspend fun restoreAquariumDetails(
        archived: ArchiveAquarium,
        newTankId: Long
    ) {
        aquariumStore.updateSmartCareEnabled(newTankId, archived.smartCareEnabled)
        aquariumStore.updateCareRemindersEnabled(newTankId, archived.careRemindersEnabled)
        archived.livestock.forEach { item ->
            aquariumStore.addLivestockToTank(newTankId, item.toSavedLivestock())
        }
    }

    private suspend fun restoreCareTasks(
        backup: DecodedUserDataBackup,
        tankIdMap: Map<Long, Long>,
        rollback: RestoreRollbackState
    ) {
        val existingTaskIds = careTaskStore.tasksFlow.first()
            .mapTo(mutableSetOf()) { task -> task.id }
        val allocator = RestoreTaskIdAllocator(existingTaskIds, nowMillis)

        backup.manifest.careTasks.forEach { archived ->
            requireOwner()
            val restoredTankId = requireNotNull(tankIdMap[archived.tankId]) {
                "Validated backup care task lost its aquarium mapping."
            }
            val restoredTaskId = allocator.allocate(archived.id)
            careTaskStore.addTask(
                archived.toCareTask(
                    ownerUid = ownerUid,
                    restoredTankId = restoredTankId,
                    restoredTaskId = restoredTaskId
                )
            )
            rollback.createdTaskIds += restoredTaskId
        }
    }

    private suspend fun restoreAssignments(
        backup: DecodedUserDataBackup,
        tankIdMap: Map<Long, Long>,
        rollback: RestoreRollbackState
    ): AssignmentRestoreCount {
        var restored = 0
        var skipped = 0
        backup.manifest.deviceAssignments.forEach { archived ->
            requireOwner()
            val restoredTankId = requireNotNull(tankIdMap[archived.tankId]) {
                "Validated backup assignment lost its aquarium mapping."
            }
            val deviceUid = DeviceUid(archived.deviceUid)
            when (
                val result = assignmentRepository.assignDeviceToTank(
                    tankId = restoredTankId,
                    deviceUid = deviceUid
                )
            ) {
                is TankDeviceAssignmentResult.Assigned -> {
                    rollback.createdAssignments += RestoredAssignment(restoredTankId, deviceUid)
                    restored += 1
                }

                is TankDeviceAssignmentResult.AlreadyAssigned -> restored += 1
                is TankDeviceAssignmentResult.Conflict,
                TankDeviceAssignmentResult.DeviceNotFound -> skipped += 1
                TankDeviceAssignmentResult.TankNotFound,
                TankDeviceAssignmentResult.InvalidRequest -> error(
                    "Validated backup produced an invalid device assignment."
                )
                is TankDeviceAssignmentResult.Failure -> throw result.error
            }
        }
        return AssignmentRestoreCount(restored = restored, skipped = skipped)
    }

    private suspend fun reconcileRemindersWithWarning(): Boolean {
        return try {
            reconcileCareReminders(ownerUid)
            false
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            true
        }
    }

    private suspend fun rollbackCreatedData(
        rollback: RestoreRollbackState,
        originalError: Throwable
    ): Throwable = withContext(NonCancellable) {
        rollback.createdAssignments.asReversed().forEach { assignment ->
            when (
                val result = assignmentRepository.removeDeviceFromTank(
                    tankId = assignment.tankId,
                    deviceUid = assignment.deviceUid
                )
            ) {
                is TankDeviceRemovalResult.Failure -> originalError.addSuppressed(result.error)
                else -> Unit
            }
        }
        rollback.createdTaskIds.asReversed().forEach { taskId ->
            runCatching { careTaskStore.deleteTask(taskId) }
                .exceptionOrNull()
                ?.let(originalError::addSuppressed)
        }
        if (rollback.createdTankIds.isNotEmpty()) {
            runCatching { aquariumStore.deleteTanks(rollback.createdTankIds) }
                .exceptionOrNull()
                ?.let(originalError::addSuppressed)
        }
        originalError
    }

    private fun requireOwner() {
        check(UserDataScope.requireCurrentUid() == ownerUid) {
            "Authenticated owner changed during backup restore."
        }
    }
}

internal class RestoreTaskIdAllocator(
    existingIds: Set<Long>,
    private val nowMillis: () -> Long
) {
    private val reservedIds = existingIds.toMutableSet()
    private var nextCandidate = maxOf(nowMillis(), 1L)

    fun allocate(preferredId: Long): Long {
        if (preferredId > 0L && reservedIds.add(preferredId)) return preferredId
        while (!reservedIds.add(nextCandidate)) {
            check(nextCandidate < Long.MAX_VALUE) {
                "No care-task id is available for backup restore."
            }
            nextCandidate += 1L
        }
        return nextCandidate.also { allocated ->
            if (allocated < Long.MAX_VALUE) nextCandidate = allocated + 1L
        }
    }
}

private data class RestoreRollbackState(
    val createdTankIds: MutableList<Long> = mutableListOf(),
    val createdTaskIds: MutableList<Long> = mutableListOf(),
    val createdAssignments: MutableList<RestoredAssignment> = mutableListOf()
)

private data class RestoredAssignment(
    val tankId: Long,
    val deviceUid: DeviceUid
)

private data class AssignmentRestoreCount(
    val restored: Int,
    val skipped: Int
)
