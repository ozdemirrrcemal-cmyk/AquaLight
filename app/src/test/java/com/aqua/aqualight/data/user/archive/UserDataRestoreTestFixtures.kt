package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignment
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentResult
import com.aqua.aqualight.data.aquarium.devices.TankDeviceRemovalResult
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.aquarium.model.TankDraft
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.devices.model.DeviceUid

internal class RestoreHarness {
    val tanks = mutableListOf<SavedAquariumTank>()
    val tasks = mutableListOf<CareTask>()
    val assignments = linkedMapOf<DeviceUid, TankDeviceAssignment>()
    val transactions = InMemoryRestoreTransactions()
    private val provenance = InMemoryRestoreProvenance()
    var assignmentBehavior: ((Long, DeviceUid) -> TankDeviceAssignmentResult)? = null
    var reminderFailure: Throwable? = null
    private var nextTankId = RestoreFixture.FIRST_LOCAL_TANK_ID
    private var nextTankCreatedAt = RestoreFixture.FIRST_LOCAL_TANK_CREATED_AT_MILLIS

    private val sources = UserDataRestoreDataSources(
        tanks = UserDataRestoreDataSources.TankDataSource(
            snapshotForOwner = { ownerUid ->
                tanks.filter { tank -> tank.ownerUid == ownerUid }
            },
            addFromDraft = { ownerUid, draft -> addTank(ownerUid, draft) },
            updateSmartCareEnabled = { tankId, enabled ->
                updateTank(tankId) { tank -> tank.copy(smartCareEnabled = enabled) }
            },
            updateCareRemindersEnabled = { tankId, enabled ->
                updateTank(tankId) { tank -> tank.copy(careRemindersEnabled = enabled) }
            },
            addLivestockToTank = { _, _ -> Unit },
            deleteTanks = { tankIds -> tanks.removeAll { tank -> tank.id in tankIds } }
        ),
        careTasks = UserDataRestoreDataSources.CareTaskDataSource(
            snapshot = { tasks.toList() },
            addTask = { task -> tasks += task },
            deleteTask = { taskId -> tasks.removeAll { task -> task.id == taskId } }
        ),
        assignments = UserDataRestoreDataSources.AssignmentDataSource(
            assignmentForDevice = { deviceUid -> assignments[deviceUid] },
            assignDeviceToTank = { tankId, deviceUid -> assign(tankId, deviceUid) },
            removeDeviceFromTank = { tankId, deviceUid -> removeAssignment(tankId, deviceUid) }
        )
    )
    private val media = UserDataRestoreMediaOperations(
        snapshotTankPhoto = { null },
        prepareRestoredTankPhoto = { _, _, _ -> error("No photo expected in this test") },
        commit = {},
        rollback = {}
    )
    val recovery = UserDataRestoreRecovery(sources, transactions, provenance)
    private val runtime = UserDataRestoreRuntime(
        dataSources = sources,
        mediaOperations = media,
        transactions = transactions,
        provenance = provenance,
        recovery = recovery
    )

    fun restorer(): UserDataBackupRestorer {
        return UserDataBackupRestorer(
            ownerUid = RestoreFixture.OWNER_UID,
            runtime = runtime,
            reconcileCareReminders = {
                reminderFailure?.let { error -> throw error }
            },
            nowMillis = { RestoreFixture.RESTORE_TASK_ID_FALLBACK }
        )
    }

    fun editOnlyTankName(name: String) {
        updateTank(tanks.single().id) { tank -> tank.copy(name = name) }
    }

    fun seedExistingTank() {
        tanks += RestoreFixture.createSavedTank(
            id = RestoreFixture.PREEXISTING_TANK_ID,
            name = "Pre-existing Tank",
            createdAtMillis = RestoreFixture.PREEXISTING_TANK_CREATED_AT_MILLIS
        )
    }

    fun simulateInterruptedRestore() {
        val existingIds = tanks.mapTo(linkedSetOf()) { tank -> tank.id }
        transactions.begin(RestoreFixture.OWNER_UID, existingIds)
        val restoredTank = addTank(
            RestoreFixture.OWNER_UID,
            RestoreFixture.interruptedDraft()
        )
        val task = RestoreFixture.localTask(
            id = RestoreFixture.INTERRUPTED_TASK_ID,
            tankId = restoredTank.id,
            title = "Interrupted task"
        )
        transactions.planTasks(RestoreFixture.OWNER_UID, listOf(task.id))
        tasks += task
        val assignment = RestorePlannedAssignment(
            tankId = restoredTank.id,
            deviceUid = DeviceUid(RestoreFixture.INTERRUPTED_DEVICE_UID)
        )
        transactions.planAssignments(RestoreFixture.OWNER_UID, listOf(assignment))
        assignments[assignment.deviceUid] = TankDeviceAssignment(
            ownerUid = RestoreFixture.OWNER_UID,
            tankId = restoredTank.id,
            deviceUid = assignment.deviceUid,
            assignedAtMillis = RestoreFixture.ASSIGNED_AT_MILLIS
        )
    }

    private fun addTank(ownerUid: String, draft: TankDraft): SavedAquariumTank {
        val tank = RestoreFixture.createSavedTank(
            id = nextTankId++,
            name = draft.name,
            createdAtMillis = nextTankCreatedAt++,
            ownerUid = ownerUid,
            draft = draft
        )
        tanks += tank
        return tank
    }

    private fun updateTank(
        tankId: Long,
        transform: (SavedAquariumTank) -> SavedAquariumTank
    ) {
        val index = tanks.indexOfFirst { tank -> tank.id == tankId }
        check(index >= 0)
        tanks[index] = transform(tanks[index])
    }

    private fun assign(tankId: Long, deviceUid: DeviceUid): TankDeviceAssignmentResult {
        val configured = assignmentBehavior?.invoke(tankId, deviceUid)
        val result = configured ?: TankDeviceAssignmentResult.Assigned(
            TankDeviceAssignment(
                ownerUid = RestoreFixture.OWNER_UID,
                tankId = tankId,
                deviceUid = deviceUid,
                assignedAtMillis = RestoreFixture.ASSIGNED_AT_MILLIS
            )
        )
        if (result is TankDeviceAssignmentResult.Assigned) {
            assignments[deviceUid] = result.assignment
        }
        return result
    }

    private fun removeAssignment(
        tankId: Long,
        deviceUid: DeviceUid
    ): TankDeviceRemovalResult {
        val current = assignments[deviceUid]
        return if (current?.tankId == tankId) {
            assignments.remove(deviceUid)
            TankDeviceRemovalResult.Removed
        } else {
            TankDeviceRemovalResult.NotAssigned
        }
    }
}

internal class InMemoryRestoreTransactions : UserDataRestoreTransactions {
    private var transaction: PendingUserDataRestore? = null

    override fun pending(ownerUid: String): PendingUserDataRestore? =
        transaction?.takeIf { pending -> pending.ownerUid == ownerUid }

    override fun begin(ownerUid: String, existingTankIds: Set<Long>) {
        check(transaction == null)
        transaction = PendingUserDataRestore(
            ownerUid = ownerUid,
            state = UserDataRestoreTransactionState.ACTIVE,
            existingTankIds = existingTankIds,
            plannedTaskIds = emptyList(),
            plannedAssignments = emptyList()
        )
    }

    override fun planTasks(ownerUid: String, taskIds: Collection<Long>) {
        transaction = requireActive(ownerUid).copy(plannedTaskIds = taskIds.toList())
    }

    override fun planAssignments(
        ownerUid: String,
        assignments: Collection<RestorePlannedAssignment>
    ) {
        transaction = requireActive(ownerUid).copy(plannedAssignments = assignments.toList())
    }

    override fun markCommitted(ownerUid: String) {
        transaction = requireActive(ownerUid).copy(
            state = UserDataRestoreTransactionState.COMMITTED
        )
    }

    override fun clearOwner(ownerUid: String) {
        if (transaction?.ownerUid == ownerUid) transaction = null
    }

    private fun requireActive(ownerUid: String): PendingUserDataRestore {
        val current = requireNotNull(transaction)
        check(current.ownerUid == ownerUid)
        check(current.state == UserDataRestoreTransactionState.ACTIVE)
        return current
    }
}

internal class InMemoryRestoreProvenance : UserDataRestoreProvenance {
    private var current = UserDataRestoreProvenanceSnapshot.Empty

    override fun snapshot(ownerUid: String): UserDataRestoreProvenanceSnapshot {
        requireFixtureOwner(ownerUid)
        return current
    }

    override fun record(ownerUid: String, batch: UserDataRestoreProvenanceBatch) {
        requireFixtureOwner(ownerUid)
        val tankRecords = current.tanks.toMutableMap()
        batch.tankRecords().forEach { record -> tankRecords[record.origin] = record }
        val taskRecords = current.careTasks.toMutableMap()
        batch.careTaskRecords().forEach { record -> taskRecords[record.origin] = record }
        current = UserDataRestoreProvenanceSnapshot(
            tanks = tankRecords.toMap(),
            careTasks = taskRecords.toMap()
        )
    }

    override fun reconcile(
        ownerUid: String,
        currentAquariums: List<SavedAquariumTank>,
        currentCareTasks: List<CareTask>
    ) {
        requireFixtureOwner(ownerUid)
        val tanksById = currentAquariums.associateBy(SavedAquariumTank::id)
        val tasksById = currentCareTasks.associateBy(CareTask::id)
        current = UserDataRestoreProvenanceSnapshot(
            tanks = current.tanks.filterValues { record ->
                tanksById[record.localTankId]?.createdAtMillis == record.localCreatedAtMillis
            },
            careTasks = current.careTasks.filterValues { record ->
                val task = tasksById[record.localTaskId]
                task?.tankId == record.localTankId &&
                    task.createdAtMillis == record.localCreatedAtMillis
            }
        )
    }

    override fun clearOwner(ownerUid: String) {
        requireFixtureOwner(ownerUid)
        current = UserDataRestoreProvenanceSnapshot.Empty
    }

    private fun requireFixtureOwner(ownerUid: String) {
        check(ownerUid == RestoreFixture.OWNER_UID)
    }
}

internal object RestoreFixture {
    const val OWNER_UID = "owner-restore-test"
    const val SOURCE_TANK_ID = 7L
    const val SOURCE_TASK_ID = 41L
    const val FIRST_LOCAL_TANK_ID = 100L
    const val FIRST_LOCAL_TANK_CREATED_AT_MILLIS = 2_000L
    const val PREEXISTING_TANK_ID = 55L
    const val PREEXISTING_TANK_CREATED_AT_MILLIS = 1_700L
    const val UNRELATED_TANK_ID = 999L
    const val CONFLICT_TANK_OFFSET = 1L
    const val INTERRUPTED_TASK_ID = 4_444L
    const val INTERRUPTED_DEVICE_UID = "device-interrupted"
    const val LOCAL_TASK_CREATED_AT_MILLIS = 1_100L
    const val SOURCE_TANK_CREATED_AT_MILLIS = 900L
    const val SOURCE_TASK_CREATED_AT_MILLIS = 800L
    const val SOURCE_TASK_UPDATED_AT_MILLIS = 900L
    const val BACKUP_CREATED_AT_MILLIS = 1_000L
    const val TASK_DUE_AT_MILLIS = 2_000L
    const val ASSIGNED_AT_MILLIS = 1_500L
    const val RESTORE_TASK_ID_FALLBACK = 10_000L

    fun backup(
        careTasks: List<ArchiveCareTask> = emptyList(),
        assignments: List<ArchiveDeviceAssignment> = emptyList()
    ): DecodedUserDataBackup {
        return DecodedUserDataBackup(
            manifest = UserDataBackupManifest(
                format = USER_DATA_BACKUP_FORMAT,
                schemaVersion = USER_DATA_BACKUP_SCHEMA_VERSION,
                createdAtMillis = BACKUP_CREATED_AT_MILLIS,
                sourceAppVersion = "test",
                aquariums = listOf(archivedTank()),
                careTasks = careTasks,
                deviceAssignments = assignments
            ),
            mediaByEntryName = emptyMap()
        )
    }

    fun archivedCareTask(): ArchiveCareTask {
        return ArchiveCareTask(
            id = SOURCE_TASK_ID,
            tankId = SOURCE_TANK_ID,
            title = "Test water",
            description = "",
            type = CareTaskType.WATER_TEST.name,
            source = CareTaskSource.MANUAL.name,
            status = CareTaskStatus.PENDING.name,
            dueAtMillis = TASK_DUE_AT_MILLIS,
            completedAtMillis = null,
            repeatEnabled = false,
            repeatIntervalDays = 1,
            reminderEnabled = true,
            missedReminderEnabled = false,
            missedReminderDays = 1,
            waterChangePercent = null,
            note = "",
            generatedRuleKey = "",
            createdAtMillis = SOURCE_TASK_CREATED_AT_MILLIS,
            updatedAtMillis = SOURCE_TASK_UPDATED_AT_MILLIS
        )
    }

    fun archiveAssignment(deviceUid: String): ArchiveDeviceAssignment {
        return ArchiveDeviceAssignment(
            tankId = SOURCE_TANK_ID,
            deviceUid = deviceUid,
            assignedAtMillis = ASSIGNED_AT_MILLIS
        )
    }

    fun localTask(id: Long, tankId: Long, title: String): CareTask {
        return CareTask(
            id = id,
            ownerUid = OWNER_UID,
            tankId = tankId,
            title = title,
            description = "",
            type = CareTaskType.WATER_TEST,
            source = CareTaskSource.MANUAL,
            status = CareTaskStatus.PENDING,
            dueAtMillis = TASK_DUE_AT_MILLIS,
            completedAtMillis = null,
            repeatEnabled = false,
            repeatIntervalDays = 1,
            reminderEnabled = false,
            missedReminderEnabled = false,
            missedReminderDays = 1,
            waterChangePercent = null,
            note = "",
            generatedRuleKey = "",
            createdAtMillis = LOCAL_TASK_CREATED_AT_MILLIS,
            updatedAtMillis = LOCAL_TASK_CREATED_AT_MILLIS
        )
    }

    fun createSavedTank(
        id: Long,
        name: String,
        createdAtMillis: Long,
        ownerUid: String = OWNER_UID,
        draft: TankDraft? = null
    ): SavedAquariumTank {
        return SavedAquariumTank(
            id = id,
            ownerUid = ownerUid,
            name = name,
            description = draft?.description.orEmpty(),
            photoUri = draft?.photoUri,
            setupDateEpochDay = draft?.setupDateEpochDay,
            widthCm = draft?.widthCm ?: 60,
            lengthCm = draft?.lengthCm ?: 30,
            heightCm = draft?.heightCm ?: 36,
            sizeUnit = draft?.sizeUnit ?: "cm",
            volumeUnit = draft?.volumeUnit ?: "L",
            tankType = draft?.tankType ?: "freshwater",
            tankStyle = draft?.tankStyle ?: "nature",
            createdAtMillis = createdAtMillis,
            smartCareEnabled = true,
            careRemindersEnabled = true,
            plants = emptyList(),
            materials = emptyList(),
            livestock = emptyList()
        )
    }

    fun interruptedDraft(): TankDraft = TankDraft(
        name = "Interrupted Restore Tank",
        description = "",
        photoUri = null,
        plants = emptyList(),
        materials = emptyList(),
        setupDateEpochDay = null,
        widthCm = 60,
        lengthCm = 30,
        heightCm = 36,
        sizeUnit = "cm",
        volumeUnit = "L",
        tankType = "freshwater",
        tankStyle = "nature"
    )

    private fun archivedTank(): ArchiveAquarium {
        return ArchiveAquarium(
            id = SOURCE_TANK_ID,
            name = "Display Tank",
            description = "",
            photo = null,
            setupDateEpochDay = null,
            widthCm = 60,
            lengthCm = 30,
            heightCm = 36,
            sizeUnit = "cm",
            volumeUnit = "L",
            tankType = "freshwater",
            tankStyle = "nature",
            createdAtMillis = SOURCE_TANK_CREATED_AT_MILLIS,
            smartCareEnabled = true,
            careRemindersEnabled = true,
            plants = emptyList(),
            materials = emptyList(),
            livestock = emptyList()
        )
    }
}
