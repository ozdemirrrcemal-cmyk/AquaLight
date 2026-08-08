package com.aqua.aqualight.data.user.archive

import android.content.Context
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignment
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentResult
import com.aqua.aqualight.data.aquarium.devices.TankDeviceRemovalResult
import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.platform.media.UserDataArchiveMediaGateway
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Narrow restore-only adapters around AquaLight's authoritative owner-scoped stores. */
internal data class UserDataRestoreDataSources(
    val tanks: TankDataSource,
    val careTasks: CareTaskDataSource,
    val assignments: AssignmentDataSource
) {
    internal data class TankDataSource(
        val snapshotForOwner: suspend (String) -> List<SavedAquariumTank>,
        val addFromDraft: suspend (String, TankDraft) -> SavedAquariumTank,
        val updateSmartCareEnabled: suspend (Long, Boolean) -> Unit,
        val updateCareRemindersEnabled: suspend (Long, Boolean) -> Unit,
        val addLivestockToTank: suspend (Long, SavedAquariumLivestock) -> Unit,
        val deleteTanks: suspend (List<Long>) -> Unit
    )

    internal data class CareTaskDataSource(
        val snapshot: suspend () -> List<CareTask>,
        val addTask: suspend (CareTask) -> Unit,
        val deleteTask: suspend (Long) -> Unit
    )

    internal data class AssignmentDataSource(
        val assignmentForDevice: suspend (DeviceUid) -> TankDeviceAssignment?,
        val assignDeviceToTank: suspend (Long, DeviceUid) -> TankDeviceAssignmentResult,
        val removeDeviceFromTank: suspend (Long, DeviceUid) -> TankDeviceRemovalResult
    )

    companion object {
        fun from(dataSources: UserDataArchiveDataSources): UserDataRestoreDataSources {
            val aquariumStore = dataSources.aquariumStore
            val careTaskStore = dataSources.careTaskStore
            val assignmentRepository = dataSources.assignmentRepository
            return UserDataRestoreDataSources(
                tanks = TankDataSource(
                    snapshotForOwner = aquariumStore::tanksSnapshotForOwner,
                    addFromDraft = { ownerUid, draft ->
                        val tankId = aquariumStore.addTankFromDraft(draft)
                        requireNotNull(
                            aquariumStore.tanksSnapshotForOwner(ownerUid)
                                .firstOrNull { tank -> tank.id == tankId }
                        ) {
                            "Restored aquarium was not visible after its durable store commit."
                        }
                    },
                    updateSmartCareEnabled = aquariumStore::updateSmartCareEnabled,
                    updateCareRemindersEnabled = aquariumStore::updateCareRemindersEnabled,
                    addLivestockToTank = aquariumStore::addLivestockToTank,
                    deleteTanks = aquariumStore::deleteTanks
                ),
                careTasks = CareTaskDataSource(
                    snapshot = { careTaskStore.tasksFlow.first() },
                    addTask = careTaskStore::addTask,
                    deleteTask = careTaskStore::deleteTask
                ),
                assignments = AssignmentDataSource(
                    assignmentForDevice = assignmentRepository::assignmentForDevice,
                    assignDeviceToTank = assignmentRepository::assignDeviceToTank,
                    removeDeviceFromTank = assignmentRepository::removeDeviceFromTank
                )
            )
        }
    }
}

private fun UserDataRestoreDataSources.trackCreatedMutations(
    transactions: UserDataRestoreTransactions
): UserDataRestoreDataSources {
    val rawTanks = tanks
    val rawCareTasks = careTasks
    val rawAssignments = assignments
    return copy(
        tanks = rawTanks.copy(
            addFromDraft = { ownerUid, draft ->
                val tank = rawTanks.addFromDraft(ownerUid, draft)
                val failure = runCatching {
                    transactions.recordCreatedTank(
                        ownerUid,
                        RestoreCreatedTank(
                            tankId = tank.id,
                            createdAtMillis = tank.createdAtMillis
                        )
                    )
                }.exceptionOrNull()
                if (failure != null) {
                    withContext(NonCancellable) {
                        runCatching { rawTanks.deleteTanks(listOf(tank.id)) }
                            .exceptionOrNull()
                            ?.let(failure::addSuppressed)
                    }
                    throw failure
                }
                tank
            }
        ),
        careTasks = rawCareTasks.copy(
            addTask = { task ->
                rawCareTasks.addTask(task)
                val failure = runCatching {
                    transactions.recordCreatedTask(
                        task.ownerUid,
                        RestoreCreatedTask(
                            taskId = task.id,
                            tankId = task.tankId,
                            createdAtMillis = task.createdAtMillis
                        )
                    )
                }.exceptionOrNull()
                if (failure != null) {
                    withContext(NonCancellable) {
                        runCatching { rawCareTasks.deleteTask(task.id) }
                            .exceptionOrNull()
                            ?.let(failure::addSuppressed)
                    }
                    throw failure
                }
            }
        ),
        assignments = rawAssignments.copy(
            assignDeviceToTank = { tankId, deviceUid ->
                when (val result = rawAssignments.assignDeviceToTank(tankId, deviceUid)) {
                    is TankDeviceAssignmentResult.Assigned -> {
                        val assignment = result.assignment
                        val failure = runCatching {
                            transactions.recordCreatedAssignment(
                                assignment.ownerUid,
                                RestoreCreatedAssignment(
                                    tankId = assignment.tankId,
                                    deviceUid = assignment.deviceUid,
                                    assignedAtMillis = assignment.assignedAtMillis
                                )
                            )
                        }.exceptionOrNull()
                        if (failure == null) {
                            result
                        } else {
                            withContext(NonCancellable) {
                                val current = runCatching {
                                    rawAssignments.assignmentForDevice(assignment.deviceUid)
                                }.getOrNull()
                                if (
                                    current?.tankId == assignment.tankId &&
                                    current.assignedAtMillis == assignment.assignedAtMillis
                                ) {
                                    when (
                                        val cleanup = rawAssignments.removeDeviceFromTank(
                                            assignment.tankId,
                                            assignment.deviceUid
                                        )
                                    ) {
                                        is TankDeviceRemovalResult.Failure ->
                                            failure.addSuppressed(cleanup.error)
                                        TankDeviceRemovalResult.InvalidRequest ->
                                            failure.addSuppressed(
                                                IllegalStateException(
                                                    "Restore assignment compensation was invalid."
                                                )
                                            )
                                        TankDeviceRemovalResult.Removed,
                                        TankDeviceRemovalResult.NotAssigned -> Unit
                                    }
                                }
                            }
                            TankDeviceAssignmentResult.Failure(failure)
                        }
                    }
                    else -> result
                }
            }
        )
    )
}

/** Restore-only media boundary; presentation never receives paths, streams or Android URIs. */
internal data class UserDataRestoreMediaOperations(
    val snapshotTankPhoto: (String?) -> ByteArray?,
    val prepareRestoredTankPhoto: (String, String, ByteArray) -> String,
    val commit: (String?) -> Unit,
    val rollback: (String?) -> Unit
) {
    companion object {
        fun from(mediaGateway: UserDataArchiveMediaGateway): UserDataRestoreMediaOperations {
            return UserDataRestoreMediaOperations(
                snapshotTankPhoto = mediaGateway::snapshotTankPhoto,
                prepareRestoredTankPhoto = mediaGateway::prepareRestoredTankPhoto,
                commit = mediaGateway::commit,
                rollback = mediaGateway::rollback
            )
        }
    }
}

/** One centrally composed runtime for restore mutation, crash recovery and provenance. */
internal data class UserDataRestoreRuntime(
    val dataSources: UserDataRestoreDataSources,
    val mediaOperations: UserDataRestoreMediaOperations,
    val transactions: UserDataRestoreTransactions,
    val provenance: UserDataRestoreProvenance,
    val recovery: UserDataRestoreRecovery
) {
    companion object {
        fun create(
            context: Context,
            archiveDataSources: UserDataArchiveDataSources,
            mediaGateway: UserDataArchiveMediaGateway
        ): UserDataRestoreRuntime {
            val appContext = context.applicationContext
            val restoreDataSources = UserDataRestoreDataSources.from(archiveDataSources)
            val transactions = UserDataRestoreJournal(appContext)
            val provenance = UserDataRestoreProvenanceStore(appContext)
            return UserDataRestoreRuntime(
                dataSources = restoreDataSources.trackCreatedMutations(transactions),
                mediaOperations = UserDataRestoreMediaOperations.from(mediaGateway),
                transactions = transactions,
                provenance = provenance,
                recovery = UserDataRestoreRecovery(
                    dataSources = restoreDataSources,
                    transactions = transactions,
                    provenance = provenance
                )
            )
        }
    }
}
