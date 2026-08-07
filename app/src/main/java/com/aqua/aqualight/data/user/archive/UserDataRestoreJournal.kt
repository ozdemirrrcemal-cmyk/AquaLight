package com.aqua.aqualight.data.user.archive

import android.content.Context
import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.Base64
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

internal data class PendingUserDataRestore(
    val ownerUid: String,
    val state: UserDataRestoreTransactionState,
    val existingTankIds: Set<Long>,
    val plannedTaskIds: List<Long>,
    val plannedAssignments: List<RestorePlannedAssignment>
)

internal interface UserDataRestoreTransactions {
    fun pending(ownerUid: String): PendingUserDataRestore?

    fun begin(ownerUid: String, existingTankIds: Set<Long>)

    fun planTasks(ownerUid: String, taskIds: Collection<Long>)

    fun planAssignments(
        ownerUid: String,
        assignments: Collection<RestorePlannedAssignment>
    )

    fun markCommitted(ownerUid: String)

    fun clearOwner(ownerUid: String)
}

/** Small durable transaction journal used only to recover an interrupted restore. */
internal class UserDataRestoreJournal(
    context: Context
) : UserDataRestoreTransactions {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

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
                    existingTankIds = existingTankIds.toSet(),
                    plannedTaskIds = emptyList(),
                    plannedAssignments = emptyList()
                )
            )
        }
    }

    override fun planTasks(ownerUid: String, taskIds: Collection<Long>) {
        val owner = canonicalRestoreOwnerUid(ownerUid)
        val normalized = taskIds.distinct()
        require(normalized.all { taskId -> taskId > 0L })
        synchronized(lock) {
            val current = requireActive(owner)
            persist(current.copy(plannedTaskIds = normalized))
        }
    }

    override fun planAssignments(
        ownerUid: String,
        assignments: Collection<RestorePlannedAssignment>
    ) {
        val owner = canonicalRestoreOwnerUid(ownerUid)
        val normalized = assignments.distinct()
        require(normalized.all { assignment ->
            assignment.tankId > 0L && assignment.deviceUid.value.isNotBlank()
        })
        synchronized(lock) {
            val current = requireActive(owner)
            persist(current.copy(plannedAssignments = normalized))
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
            check(
                preferences.edit()
                    .remove(UserDataRestoreJournalCodec.preferenceKey(owner))
                    .commit()
            ) { "User-data restore journal could not be cleared." }
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
        val encoded = preferences.getString(
            UserDataRestoreJournalCodec.preferenceKey(owner),
            null
        ) ?: return null
        return runCatching { UserDataRestoreJournalCodec.decode(encoded, owner) }
            .getOrElse { error ->
                throw IllegalStateException(
                    "User-data restore journal is corrupt for the active owner.",
                    error
                )
            }
    }

    private fun persist(transaction: PendingUserDataRestore) {
        check(
            preferences.edit()
                .putString(
                    UserDataRestoreJournalCodec.preferenceKey(transaction.ownerUid),
                    UserDataRestoreJournalCodec.encode(transaction)
                )
                .commit()
        ) { "User-data restore journal could not be committed." }
    }

    private companion object {
        const val PREFERENCES_NAME = "user_data_restore_journal_v1"
        val lock = Any()
    }
}

private object UserDataRestoreJournalCodec {
    private const val KEY_PREFIX = "owner."
    private const val FORMAT_VERSION = 1

    fun preferenceKey(owner: String): String {
        val token = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(owner.toByteArray(Charsets.UTF_8))
        return KEY_PREFIX + token
    }

    fun encode(transaction: PendingUserDataRestore): String {
        val tankIds = JSONArray()
        transaction.existingTankIds.sorted().forEach { tankId ->
            tankIds.put(tankId)
        }
        val taskIds = JSONArray()
        transaction.plannedTaskIds.forEach { taskId ->
            taskIds.put(taskId)
        }
        val assignments = JSONArray()
        transaction.plannedAssignments.forEach { assignment ->
            assignments.put(
                JSONObject()
                    .put("tankId", assignment.tankId)
                    .put("deviceUid", assignment.deviceUid.value)
            )
        }
        return JSONObject()
            .put("version", FORMAT_VERSION)
            .put("ownerUid", transaction.ownerUid)
            .put("state", transaction.state.name)
            .put("existingTankIds", tankIds)
            .put("plannedTaskIds", taskIds)
            .put("plannedAssignments", assignments)
            .toString()
    }

    fun decode(encoded: String, expectedOwner: String): PendingUserDataRestore {
        val root = JSONObject(encoded)
        require(root.getInt("version") == FORMAT_VERSION)
        val owner = canonicalRestoreOwnerUid(root.getString("ownerUid"))
        require(owner == expectedOwner)
        val state = UserDataRestoreTransactionState.valueOf(root.getString("state"))

        val existingTankIds = linkedSetOf<Long>()
        val tankArray = root.getJSONArray("existingTankIds")
        for (index in 0 until tankArray.length()) {
            val tankId = tankArray.getLong(index)
            require(tankId > 0L && existingTankIds.add(tankId))
        }

        val plannedTaskIds = mutableListOf<Long>()
        val taskArray = root.getJSONArray("plannedTaskIds")
        for (index in 0 until taskArray.length()) {
            val taskId = taskArray.getLong(index)
            require(taskId > 0L && taskId !in plannedTaskIds)
            plannedTaskIds += taskId
        }

        val plannedAssignments = mutableListOf<RestorePlannedAssignment>()
        val assignmentArray = root.getJSONArray("plannedAssignments")
        for (index in 0 until assignmentArray.length()) {
            val item = assignmentArray.getJSONObject(index)
            val assignment = RestorePlannedAssignment(
                tankId = item.getLong("tankId").also { value -> require(value > 0L) },
                deviceUid = DeviceUid(
                    item.getString("deviceUid").trim().also { value ->
                        require(value.isNotBlank())
                    }
                )
            )
            require(assignment !in plannedAssignments)
            plannedAssignments += assignment
        }

        return PendingUserDataRestore(
            ownerUid = owner,
            state = state,
            existingTankIds = existingTankIds,
            plannedTaskIds = plannedTaskIds.toList(),
            plannedAssignments = plannedAssignments.toList()
        )
    }
}
