package com.aqua.aqualight.data.user.archive

import android.content.Context
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.json.JSONArray
import org.json.JSONObject

internal enum class UserDataRestoreTransactionState {
    ACTIVE,
    COMMITTED
}

internal data class RestorePlannedAssignment(
    val tankId: Long,
    val deviceUid: DeviceUid
)

internal data class RestoreCreatedTank(
    val tankId: Long,
    val createdAtMillis: Long
)

internal data class RestoreCreatedTask(
    val taskId: Long,
    val tankId: Long,
    val createdAtMillis: Long
)

internal data class RestoreCreatedAssignment(
    val tankId: Long,
    val deviceUid: DeviceUid,
    val assignedAtMillis: Long
)

internal data class PendingUserDataRestore(
    val ownerUid: String,
    val state: UserDataRestoreTransactionState,
    val existingTankIds: Set<Long>,
    val plannedTaskIds: List<Long>,
    val plannedAssignments: List<RestorePlannedAssignment>,
    val createdTanks: List<RestoreCreatedTank> = emptyList(),
    val createdTasks: List<RestoreCreatedTask> = emptyList(),
    val createdAssignments: List<RestoreCreatedAssignment> = emptyList(),
    val exactMutationTracking: Boolean = false
)

internal interface UserDataRestoreTransactions {
    fun pending(ownerUid: String): PendingUserDataRestore?

    fun begin(ownerUid: String, existingTankIds: Set<Long>)

    fun planTasks(ownerUid: String, taskIds: Collection<Long>)

    fun planAssignments(
        ownerUid: String,
        assignments: Collection<RestorePlannedAssignment>
    )

    fun recordCreatedTank(ownerUid: String, tank: RestoreCreatedTank) = Unit

    fun recordCreatedTask(ownerUid: String, task: RestoreCreatedTask) = Unit

    fun recordCreatedAssignment(ownerUid: String, assignment: RestoreCreatedAssignment) = Unit

    fun markCommitted(ownerUid: String)

    fun clearOwner(ownerUid: String)
}

/** Small durable transaction journal used only to recover an interrupted restore. */
internal class UserDataRestoreJournal(
    context: Context
) : UserDataRestoreTransactions {

    private val files = UserDataRestoreMetadataFiles(context, JOURNAL_NAMESPACE)

    override fun pending(ownerUid: String): PendingUserDataRestore? {
        val owner = canonicalRestoreOwnerUid(ownerUid)
        return synchronized(lock) { read(owner) }
    }

    override fun begin(ownerUid: String, existingTankIds: Set<Long>) {
        val owner = canonicalRestoreOwnerUid(ownerUid)
        require(existingTankIds.all { tankId -> tankId > 0L })
        synchronized(lock) {
            check(read(owner) == null) {
                "A user-data restore transaction is already pending."
            }
            persist(
                PendingUserDataRestore(
                    ownerUid = owner,
                    state = UserDataRestoreTransactionState.ACTIVE,
                    existingTankIds = emptySet(),
                    plannedTaskIds = emptyList(),
                    plannedAssignments = emptyList(),
                    exactMutationTracking = true
                )
            )
        }
    }

    override fun planTasks(ownerUid: String, taskIds: Collection<Long>) {
        canonicalRestoreOwnerUid(ownerUid)
        require(taskIds.all { taskId -> taskId > 0L })
    }

    override fun planAssignments(
        ownerUid: String,
        assignments: Collection<RestorePlannedAssignment>
    ) {
        canonicalRestoreOwnerUid(ownerUid)
        require(assignments.all { assignment ->
            assignment.tankId > 0L && assignment.deviceUid.value.isNotBlank()
        })
    }

    override fun recordCreatedTank(ownerUid: String, tank: RestoreCreatedTank) {
        val owner = canonicalRestoreOwnerUid(ownerUid)
        require(tank.tankId > 0L && tank.createdAtMillis > 0L)
        synchronized(lock) {
            val current = requireActive(owner)
            val existing = current.createdTanks.firstOrNull { item -> item.tankId == tank.tankId }
            check(existing == null || existing == tank) {
                "Restore journal tank identity changed."
            }
            if (existing == null) {
                persist(current.copy(createdTanks = current.createdTanks + tank))
            }
        }
    }

    override fun recordCreatedTask(ownerUid: String, task: RestoreCreatedTask) {
        val owner = canonicalRestoreOwnerUid(ownerUid)
        require(task.taskId > 0L && task.tankId > 0L && task.createdAtMillis > 0L)
        synchronized(lock) {
            val current = requireActive(owner)
            val existing = current.createdTasks.firstOrNull { item -> item.taskId == task.taskId }
            check(existing == null || existing == task) {
                "Restore journal care-task identity changed."
            }
            if (existing == null) {
                persist(current.copy(createdTasks = current.createdTasks + task))
            }
        }
    }

    override fun recordCreatedAssignment(
        ownerUid: String,
        assignment: RestoreCreatedAssignment
    ) {
        val owner = canonicalRestoreOwnerUid(ownerUid)
        require(
            assignment.tankId > 0L &&
                assignment.deviceUid.value.isNotBlank() &&
                assignment.assignedAtMillis > 0L
        )
        synchronized(lock) {
            val current = requireActive(owner)
            val existing = current.createdAssignments.firstOrNull { item ->
                item.deviceUid == assignment.deviceUid
            }
            check(existing == null || existing == assignment) {
                "Restore journal device-assignment identity changed."
            }
            if (existing == null) {
                persist(current.copy(createdAssignments = current.createdAssignments + assignment))
            }
        }
    }

    override fun markCommitted(ownerUid: String) {
        val owner = canonicalRestoreOwnerUid(ownerUid)
        synchronized(lock) {
            val current = requireActive(owner)
            persist(current.copy(state = UserDataRestoreTransactionState.COMMITTED))
        }
    }

    override fun clearOwner(ownerUid: String) {
        val owner = canonicalRestoreOwnerUid(ownerUid)
        synchronized(lock) {
            files.delete(owner)
        }
    }

    private fun requireActive(owner: String): PendingUserDataRestore {
        val current = requireNotNull(read(owner)) {
            "No user-data restore transaction is pending."
        }
        check(current.state == UserDataRestoreTransactionState.ACTIVE) {
            "A committed user-data restore transaction cannot be mutated."
        }
        return current
    }

    private fun read(owner: String): PendingUserDataRestore? {
        val encoded = files.read(owner) ?: return null
        return runCatching { UserDataRestoreJournalCodec.decode(encoded, owner) }
            .getOrElse { error ->
                throw IllegalStateException(
                    "User-data restore journal is corrupt for the active owner.",
                    error
                )
            }
    }

    private fun persist(transaction: PendingUserDataRestore) {
        files.write(
            transaction.ownerUid,
            UserDataRestoreJournalCodec.encode(transaction)
        )
    }

    private companion object {
        const val JOURNAL_NAMESPACE = "journal"
        val lock = Any()
    }
}

private object UserDataRestoreJournalCodec {
    private const val FORMAT_VERSION = 2
    private const val LEGACY_FORMAT_VERSION = 1

    fun encode(transaction: PendingUserDataRestore): String {
        val tanks = JSONArray()
        transaction.createdTanks.forEach { tank ->
            tanks.put(
                JSONObject()
                    .put("tankId", tank.tankId)
                    .put("createdAtMillis", tank.createdAtMillis)
            )
        }
        val tasks = JSONArray()
        transaction.createdTasks.forEach { task ->
            tasks.put(
                JSONObject()
                    .put("taskId", task.taskId)
                    .put("tankId", task.tankId)
                    .put("createdAtMillis", task.createdAtMillis)
            )
        }
        val assignments = JSONArray()
        transaction.createdAssignments.forEach { assignment ->
            assignments.put(
                JSONObject()
                    .put("tankId", assignment.tankId)
                    .put("deviceUid", assignment.deviceUid.value)
                    .put("assignedAtMillis", assignment.assignedAtMillis)
            )
        }
        return JSONObject()
            .put("version", FORMAT_VERSION)
            .put("ownerUid", transaction.ownerUid)
            .put("state", transaction.state.name)
            .put("createdTanks", tanks)
            .put("createdTasks", tasks)
            .put("createdAssignments", assignments)
            .toString()
    }

    fun decode(encoded: String, expectedOwner: String): PendingUserDataRestore {
        val root = JSONObject(encoded)
        val version = root.getInt("version")
        val owner = canonicalRestoreOwnerUid(root.getString("ownerUid"))
        require(owner == expectedOwner)
        val state = UserDataRestoreTransactionState.valueOf(root.getString("state"))
        return when (version) {
            FORMAT_VERSION -> decodeCurrent(root, owner, state)
            LEGACY_FORMAT_VERSION -> PendingUserDataRestore(
                ownerUid = owner,
                state = state,
                existingTankIds = emptySet(),
                plannedTaskIds = emptyList(),
                plannedAssignments = emptyList(),
                exactMutationTracking = true
            )
            else -> error("Unsupported user-data restore journal version.")
        }
    }

    private fun decodeCurrent(
        root: JSONObject,
        owner: String,
        state: UserDataRestoreTransactionState
    ): PendingUserDataRestore {
        val createdTanks = mutableListOf<RestoreCreatedTank>()
        val tankArray = root.getJSONArray("createdTanks")
        for (index in 0 until tankArray.length()) {
            val item = tankArray.getJSONObject(index)
            val tank = RestoreCreatedTank(
                tankId = positiveLong(item, "tankId"),
                createdAtMillis = positiveLong(item, "createdAtMillis")
            )
            require(createdTanks.none { existing -> existing.tankId == tank.tankId })
            createdTanks += tank
        }

        val createdTasks = mutableListOf<RestoreCreatedTask>()
        val taskArray = root.getJSONArray("createdTasks")
        for (index in 0 until taskArray.length()) {
            val item = taskArray.getJSONObject(index)
            val task = RestoreCreatedTask(
                taskId = positiveLong(item, "taskId"),
                tankId = positiveLong(item, "tankId"),
                createdAtMillis = positiveLong(item, "createdAtMillis")
            )
            require(createdTasks.none { existing -> existing.taskId == task.taskId })
            createdTasks += task
        }

        val createdAssignments = mutableListOf<RestoreCreatedAssignment>()
        val assignmentArray = root.getJSONArray("createdAssignments")
        for (index in 0 until assignmentArray.length()) {
            val item = assignmentArray.getJSONObject(index)
            val assignment = RestoreCreatedAssignment(
                tankId = positiveLong(item, "tankId"),
                deviceUid = DeviceUid(
                    item.getString("deviceUid").trim().also { value -> require(value.isNotBlank()) }
                ),
                assignedAtMillis = positiveLong(item, "assignedAtMillis")
            )
            require(
                createdAssignments.none { existing ->
                    existing.deviceUid == assignment.deviceUid
                }
            )
            createdAssignments += assignment
        }

        return PendingUserDataRestore(
            ownerUid = owner,
            state = state,
            existingTankIds = emptySet(),
            plannedTaskIds = emptyList(),
            plannedAssignments = emptyList(),
            createdTanks = createdTanks.toList(),
            createdTasks = createdTasks.toList(),
            createdAssignments = createdAssignments.toList(),
            exactMutationTracking = true
        )
    }

    private fun positiveLong(item: JSONObject, name: String): Long {
        return item.getLong(name).also { value -> require(value > 0L) }
    }
}
