package com.aqua.aqualight.data.user.archive

import android.content.Context
import com.aqua.aqualight.application.user.UserDataRestoreResult
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentResult
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.platform.media.UserDataArchiveMediaGateway
import java.util.concurrent.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class UserDataBackupRestorer(
    private val ownerUid: String,
    private val runtime: UserDataRestoreRuntime,
    private val reconcileCareReminders: suspend (String) -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {

    internal constructor(
        context: Context,
        ownerUid: String,
        dataSources: UserDataArchiveDataSources,
        mediaGateway: UserDataArchiveMediaGateway,
        reconcileCareReminders: suspend (String) -> Unit,
        nowMillis: () -> Long = System::currentTimeMillis
    ) : this(
        ownerUid = ownerUid,
        runtime = UserDataRestoreRuntime.create(context, dataSources, mediaGateway),
        reconcileCareReminders = reconcileCareReminders,
        nowMillis = nowMillis
    )

    private val dataSources: UserDataRestoreDataSources
        get() = runtime.dataSources
    private val mediaOperations: UserDataRestoreMediaOperations
        get() = runtime.mediaOperations
    private val transactions: UserDataRestoreTransactions
        get() = runtime.transactions
    private val provenance: UserDataRestoreProvenance
        get() = runtime.provenance

    suspend fun restore(backup: DecodedUserDataBackup): UserDataRestoreResult {
        requireRestoreOwner(ownerUid)
        runtime.recovery.recover(ownerUid)

        val existingAquariums = dataSources.tanks.snapshotForOwner(ownerUid)
        val existingCareTasks = dataSources.careTasks.snapshot()
        provenance.reconcile(ownerUid, existingAquariums, existingCareTasks)
        val deduplicator = UserDataRestoreDeduplicator(
            existingAquariums = existingAquariums,
            existingCareTasks = existingCareTasks,
            ownerUid = ownerUid,
            snapshotTankPhoto = mediaOperations.snapshotTankPhoto,
            provenance = provenance.snapshot(ownerUid)
        )
        transactions.begin(
            ownerUid = ownerUid,
            existingTankIds = existingAquariums.mapTo(linkedSetOf()) { tank -> tank.id }
        )

        val provenanceBatch = UserDataRestoreProvenanceBatch()
        val attempt = runCatching {
            executeRestore(
                backup = backup,
                existingCareTasks = existingCareTasks,
                deduplicator = deduplicator,
                provenanceBatch = provenanceBatch
            )
        }
        val failure = attempt.exceptionOrNull()
        if (failure != null) throw rollbackAfterFailure(failure)
        return attempt.getOrThrow()
    }

    private suspend fun executeRestore(
        backup: DecodedUserDataBackup,
        existingCareTasks: List<CareTask>,
        deduplicator: UserDataRestoreDeduplicator,
        provenanceBatch: UserDataRestoreProvenanceBatch
    ): UserDataRestoreResult {
        val aquariums = restoreAquariums(backup, deduplicator, provenanceBatch)
        val restoredCareTasks = restoreCareTasks(
            backup = backup,
            tankIdMap = aquariums.tankIdMap,
            existingCareTasks = existingCareTasks,
            deduplicator = deduplicator,
            provenanceBatch = provenanceBatch
        )
        val assignments = restoreAssignments(backup, aquariums.tankIdMap)

        provenance.record(ownerUid, provenanceBatch)
        requireRestoreOwner(ownerUid)
        val reminderWarning = reconcileRemindersWithWarning()
        transactions.markCommitted(ownerUid)
        runCatching { transactions.clearOwner(ownerUid) }

        return UserDataRestoreResult(
            restoredAquariumCount = aquariums.restoredCount,
            restoredCareTaskCount = restoredCareTasks,
            restoredDeviceAssignmentCount = assignments.restored,
            skippedDeviceAssignmentCount = assignments.skipped,
            reminderReconciliationWarning = reminderWarning
        )
    }

    private suspend fun restoreAquariums(
        backup: DecodedUserDataBackup,
        deduplicator: UserDataRestoreDeduplicator,
        provenanceBatch: UserDataRestoreProvenanceBatch
    ): AquariumRestoreResult {
        val tankIdMap = linkedMapOf<Long, Long>()
        var restoredCount = 0
        backup.manifest.aquariums.forEach { archived ->
            requireRestoreOwner(ownerUid)
            val existing = deduplicator.takeMatchingAquarium(archived)
            val local = existing ?: createAquarium(archived, backup).also {
                restoredCount += 1
            }
            provenanceBatch.rememberAquarium(archived, local)
            tankIdMap[archived.id] = local.id
        }
        return AquariumRestoreResult(
            tankIdMap = tankIdMap.toMap(),
            restoredCount = restoredCount
        )
    }

    private suspend fun createAquarium(
        archived: ArchiveAquarium,
        backup: DecodedUserDataBackup
    ): SavedAquariumTank {
        val photoUri = archived.photo?.let { reference ->
            val bytes = requireNotNull(backup.mediaByEntryName[reference.entryName])
            mediaOperations.prepareRestoredTankPhoto(
                ownerUid,
                "restore_${archived.id}",
                bytes
            )
        }
        var tankWasCreated = false
        return try {
            val local = dataSources.tanks.addFromDraft(
                ownerUid,
                archived.toTankDraft(photoUri)
            )
            tankWasCreated = true
            restoreAquariumDetails(archived, local.id)
            mediaOperations.commit(photoUri)
            local
        } finally {
            if (!tankWasCreated) mediaOperations.rollback(photoUri)
        }
    }

    private suspend fun restoreAquariumDetails(
        archived: ArchiveAquarium,
        newTankId: Long
    ) {
        dataSources.tanks.updateSmartCareEnabled(newTankId, archived.smartCareEnabled)
        dataSources.tanks.updateCareRemindersEnabled(newTankId, archived.careRemindersEnabled)
        archived.livestock.forEach { item ->
            dataSources.tanks.addLivestockToTank(newTankId, item.toSavedLivestock())
        }
    }

    private suspend fun restoreCareTasks(
        backup: DecodedUserDataBackup,
        tankIdMap: Map<Long, Long>,
        existingCareTasks: List<CareTask>,
        deduplicator: UserDataRestoreDeduplicator,
        provenanceBatch: UserDataRestoreProvenanceBatch
    ): Int {
        val allocator = RestoreTaskIdAllocator(
            existingIds = existingCareTasks.mapTo(mutableSetOf()) { task -> task.id },
            nowMillis = nowMillis
        )
        val plans = mutableListOf<CareTaskRestorePlan>()

        backup.manifest.careTasks.forEach { archived ->
            requireRestoreOwner(ownerUid)
            val restoredTankId = requireNotNull(tankIdMap[archived.tankId]) {
                "Validated backup care task lost its aquarium mapping."
            }
            val existing = deduplicator.takeMatchingCareTask(archived, restoredTankId)
            if (existing != null) {
                provenanceBatch.rememberCareTask(archived, existing)
            } else {
                val task = archived.toCareTask(
                    ownerUid = ownerUid,
                    restoredTankId = restoredTankId,
                    restoredTaskId = allocator.allocate(archived.id)
                )
                plans += CareTaskRestorePlan(archived, task)
            }
        }

        transactions.planTasks(ownerUid, plans.map { plan -> plan.task.id })
        plans.forEach { plan ->
            requireRestoreOwner(ownerUid)
            dataSources.careTasks.addTask(plan.task)
            provenanceBatch.rememberCareTask(plan.archived, plan.task)
        }
        return plans.size
    }

    private suspend fun restoreAssignments(
        backup: DecodedUserDataBackup,
        tankIdMap: Map<Long, Long>
    ): AssignmentRestoreCount {
        val planning = planAssignments(backup, tankIdMap)
        transactions.planAssignments(
            ownerUid,
            planning.plans.map { plan ->
                RestorePlannedAssignment(plan.tankId, plan.deviceUid)
            }
        )

        var restored = 0
        var skipped = planning.skipped
        planning.plans.forEach { plan ->
            requireRestoreOwner(ownerUid)
            when (
                val result = dataSources.assignments.assignDeviceToTank(
                    plan.tankId,
                    plan.deviceUid
                )
            ) {
                is TankDeviceAssignmentResult.Assigned -> restored += 1
                is TankDeviceAssignmentResult.AlreadyAssigned,
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

    private suspend fun planAssignments(
        backup: DecodedUserDataBackup,
        tankIdMap: Map<Long, Long>
    ): AssignmentPlanningResult {
        val plans = mutableListOf<AssignmentRestorePlan>()
        var skipped = 0
        backup.manifest.deviceAssignments.forEach { archived ->
            requireRestoreOwner(ownerUid)
            val restoredTankId = requireNotNull(tankIdMap[archived.tankId]) {
                "Validated backup assignment lost its aquarium mapping."
            }
            val deviceUid = DeviceUid(archived.deviceUid)
            val existing = dataSources.assignments.assignmentForDevice(deviceUid)
            if (existing == null) {
                plans += AssignmentRestorePlan(restoredTankId, deviceUid)
            } else {
                skipped += 1
            }
        }
        return AssignmentPlanningResult(plans = plans, skipped = skipped)
    }

    private suspend fun reconcileRemindersWithWarning(): Boolean {
        val attempt = runCatching { reconcileCareReminders(ownerUid) }
        val failure = attempt.exceptionOrNull()
        if (failure is CancellationException) throw failure
        return failure != null
    }

    private suspend fun rollbackAfterFailure(originalError: Throwable): Throwable {
        val rollbackFailure = withContext(NonCancellable) {
            runCatching { runtime.recovery.recover(ownerUid) }.exceptionOrNull()
        }
        rollbackFailure?.let(originalError::addSuppressed)
        return originalError
    }
}

private fun requireRestoreOwner(ownerUid: String) {
    check(UserDataScope.requireCurrentUid() == ownerUid) {
        "Authenticated owner changed during backup restore."
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

private data class AquariumRestoreResult(
    val tankIdMap: Map<Long, Long>,
    val restoredCount: Int
)

private data class CareTaskRestorePlan(
    val archived: ArchiveCareTask,
    val task: CareTask
)

private data class AssignmentRestorePlan(
    val tankId: Long,
    val deviceUid: DeviceUid
)

private data class AssignmentPlanningResult(
    val plans: List<AssignmentRestorePlan>,
    val skipped: Int
)

private data class AssignmentRestoreCount(
    val restored: Int,
    val skipped: Int
)
