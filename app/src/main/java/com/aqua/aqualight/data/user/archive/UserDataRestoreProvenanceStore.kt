package com.aqua.aqualight.data.user.archive

import android.content.Context
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.care.model.CareTask
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

internal data class RestoreTankOrigin(
    val sourceTankId: Long,
    val sourceCreatedAtMillis: Long
)

internal data class RestoreTaskOrigin(
    val sourceTaskId: Long,
    val sourceTankId: Long,
    val sourceCreatedAtMillis: Long
)

internal data class RestoreTankProvenance(
    val origin: RestoreTankOrigin,
    val localTankId: Long,
    val localCreatedAtMillis: Long
)

internal data class RestoreTaskProvenance(
    val origin: RestoreTaskOrigin,
    val localTaskId: Long,
    val localTankId: Long,
    val localCreatedAtMillis: Long
)

internal data class UserDataRestoreProvenanceSnapshot(
    val tanks: Map<RestoreTankOrigin, RestoreTankProvenance>,
    val careTasks: Map<RestoreTaskOrigin, RestoreTaskProvenance>
) {
    fun aquarium(archived: ArchiveAquarium): RestoreTankProvenance? {
        return tanks[RestoreTankOrigin(archived.id, archived.createdAtMillis)]
    }

    fun careTask(archived: ArchiveCareTask): RestoreTaskProvenance? {
        return careTasks[
            RestoreTaskOrigin(
                sourceTaskId = archived.id,
                sourceTankId = archived.tankId,
                sourceCreatedAtMillis = archived.createdAtMillis
            )
        ]
    }

    companion object {
        val Empty = UserDataRestoreProvenanceSnapshot(
            tanks = emptyMap(),
            careTasks = emptyMap()
        )
    }
}

internal class UserDataRestoreProvenanceBatch {
    private val tanks = linkedMapOf<RestoreTankOrigin, RestoreTankProvenance>()
    private val careTasks = linkedMapOf<RestoreTaskOrigin, RestoreTaskProvenance>()

    fun rememberAquarium(archived: ArchiveAquarium, local: SavedAquariumTank) {
        val origin = RestoreTankOrigin(archived.id, archived.createdAtMillis)
        tanks[origin] = RestoreTankProvenance(
            origin = origin,
            localTankId = local.id,
            localCreatedAtMillis = local.createdAtMillis
        )
    }

    fun rememberCareTask(archived: ArchiveCareTask, local: CareTask) {
        val origin = RestoreTaskOrigin(
            sourceTaskId = archived.id,
            sourceTankId = archived.tankId,
            sourceCreatedAtMillis = archived.createdAtMillis
        )
        careTasks[origin] = RestoreTaskProvenance(
            origin = origin,
            localTaskId = local.id,
            localTankId = local.tankId,
            localCreatedAtMillis = local.createdAtMillis
        )
    }

    internal fun tankRecords(): Collection<RestoreTankProvenance> = tanks.values

    internal fun careTaskRecords(): Collection<RestoreTaskProvenance> = careTasks.values

    internal val isEmpty: Boolean
        get() = tanks.isEmpty() && careTasks.isEmpty()
}

internal interface UserDataRestoreProvenance {
    fun snapshot(ownerUid: String): UserDataRestoreProvenanceSnapshot

    fun record(ownerUid: String, batch: UserDataRestoreProvenanceBatch)

    fun reconcile(
        ownerUid: String,
        currentAquariums: List<SavedAquariumTank>,
        currentCareTasks: List<CareTask>
    )

    fun clearOwner(ownerUid: String)
}

/** Durable owner-scoped source-to-local identity map for repeatable backup restore. */
internal class UserDataRestoreProvenanceStore(
    context: Context
) : UserDataRestoreProvenance {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun snapshot(ownerUid: String): UserDataRestoreProvenanceSnapshot {
        val owner = canonicalRestoreOwnerUid(ownerUid)
        return synchronized(lock) { readSnapshot(owner) }
    }

    override fun record(ownerUid: String, batch: UserDataRestoreProvenanceBatch) {
        if (batch.isEmpty) return
        val owner = canonicalRestoreOwnerUid(ownerUid)
        synchronized(lock) {
            val current = readSnapshot(owner)
            val tanks = current.tanks.toMutableMap()
            batch.tankRecords().forEach { record -> tanks[record.origin] = record }
            val tasks = current.careTasks.toMutableMap()
            batch.careTaskRecords().forEach { record -> tasks[record.origin] = record }
            persist(
                owner = owner,
                snapshot = UserDataRestoreProvenanceSnapshot(
                    tanks = tanks.toMap(),
                    careTasks = tasks.toMap()
                )
            )
        }
    }

    override fun reconcile(
        ownerUid: String,
        currentAquariums: List<SavedAquariumTank>,
        currentCareTasks: List<CareTask>
    ) {
        val owner = canonicalRestoreOwnerUid(ownerUid)
        synchronized(lock) {
            val current = readSnapshot(owner)
            val tanksById = currentAquariums.associateBy(SavedAquariumTank::id)
            val tasksById = currentCareTasks.associateBy(CareTask::id)
            val reconciled = UserDataRestoreProvenanceSnapshot(
                tanks = current.tanks.filterValues { record ->
                    tanksById[record.localTankId]?.createdAtMillis == record.localCreatedAtMillis
                },
                careTasks = current.careTasks.filterValues { record ->
                    val task = tasksById[record.localTaskId]
                    task?.tankId == record.localTankId &&
                        task.createdAtMillis == record.localCreatedAtMillis
                }
            )
            if (reconciled != current) persist(owner, reconciled)
        }
    }

    override fun clearOwner(ownerUid: String) {
        val owner = canonicalRestoreOwnerUid(ownerUid)
        synchronized(lock) {
            val committed = preferences.edit()
                .remove(preferenceKey(owner))
                .commit()
            check(committed) { "Restore provenance could not be cleared." }
        }
    }

    private fun readSnapshot(owner: String): UserDataRestoreProvenanceSnapshot {
        val encoded = preferences.getString(preferenceKey(owner), null)
            ?: return UserDataRestoreProvenanceSnapshot.Empty
        return runCatching { decodeSnapshot(encoded, owner) }
            .getOrElse { error ->
                throw IllegalStateException(
                    "Restore provenance is corrupt for the active owner.",
                    error
                )
            }
    }

    private fun persist(owner: String, snapshot: UserDataRestoreProvenanceSnapshot) {
        val editor = preferences.edit()
        if (snapshot.tanks.isEmpty() && snapshot.careTasks.isEmpty()) {
            editor.remove(preferenceKey(owner))
        } else {
            editor.putString(preferenceKey(owner), encodeSnapshot(owner, snapshot))
        }
        check(editor.commit()) { "Restore provenance could not be committed." }
    }

    private fun encodeSnapshot(
        owner: String,
        snapshot: UserDataRestoreProvenanceSnapshot
    ): String {
        val tanks = JSONArray()
        snapshot.tanks.values
            .sortedWith(compareBy({ it.origin.sourceTankId }, { it.origin.sourceCreatedAtMillis }))
            .forEach { record ->
                tanks.put(
                    JSONObject()
                        .put("sourceTankId", record.origin.sourceTankId)
                        .put("sourceCreatedAtMillis", record.origin.sourceCreatedAtMillis)
                        .put("localTankId", record.localTankId)
                        .put("localCreatedAtMillis", record.localCreatedAtMillis)
                )
            }
        val tasks = JSONArray()
        snapshot.careTasks.values
            .sortedWith(compareBy({ it.origin.sourceTaskId }, { it.origin.sourceCreatedAtMillis }))
            .forEach { record ->
                tasks.put(
                    JSONObject()
                        .put("sourceTaskId", record.origin.sourceTaskId)
                        .put("sourceTankId", record.origin.sourceTankId)
                        .put("sourceCreatedAtMillis", record.origin.sourceCreatedAtMillis)
                        .put("localTaskId", record.localTaskId)
                        .put("localTankId", record.localTankId)
                        .put("localCreatedAtMillis", record.localCreatedAtMillis)
                )
            }
        return JSONObject()
            .put("version", FORMAT_VERSION)
            .put("ownerUid", owner)
            .put("tanks", tanks)
            .put("careTasks", tasks)
            .toString()
    }

    private fun decodeSnapshot(
        encoded: String,
        expectedOwner: String
    ): UserDataRestoreProvenanceSnapshot {
        val root = JSONObject(encoded)
        require(root.getInt("version") == FORMAT_VERSION)
        require(canonicalRestoreOwnerUid(root.getString("ownerUid")) == expectedOwner)

        val tanks = linkedMapOf<RestoreTankOrigin, RestoreTankProvenance>()
        val tankArray = root.getJSONArray("tanks")
        for (index in 0 until tankArray.length()) {
            val item = tankArray.getJSONObject(index)
            val origin = RestoreTankOrigin(
                sourceTankId = positiveLong(item, "sourceTankId"),
                sourceCreatedAtMillis = positiveLong(item, "sourceCreatedAtMillis")
            )
            val record = RestoreTankProvenance(
                origin = origin,
                localTankId = positiveLong(item, "localTankId"),
                localCreatedAtMillis = positiveLong(item, "localCreatedAtMillis")
            )
            require(tanks.put(origin, record) == null) {
                "Duplicate aquarium restore provenance."
            }
        }

        val tasks = linkedMapOf<RestoreTaskOrigin, RestoreTaskProvenance>()
        val taskArray = root.getJSONArray("careTasks")
        for (index in 0 until taskArray.length()) {
            val item = taskArray.getJSONObject(index)
            val origin = RestoreTaskOrigin(
                sourceTaskId = positiveLong(item, "sourceTaskId"),
                sourceTankId = positiveLong(item, "sourceTankId"),
                sourceCreatedAtMillis = positiveLong(item, "sourceCreatedAtMillis")
            )
            val record = RestoreTaskProvenance(
                origin = origin,
                localTaskId = positiveLong(item, "localTaskId"),
                localTankId = positiveLong(item, "localTankId"),
                localCreatedAtMillis = positiveLong(item, "localCreatedAtMillis")
            )
            require(tasks.put(origin, record) == null) {
                "Duplicate care-task restore provenance."
            }
        }

        return UserDataRestoreProvenanceSnapshot(
            tanks = tanks.toMap(),
            careTasks = tasks.toMap()
        )
    }

    private fun positiveLong(item: JSONObject, name: String): Long {
        return item.getLong(name).also { value -> require(value > 0L) }
    }

    private fun preferenceKey(owner: String): String {
        val token = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(owner.toByteArray(Charsets.UTF_8))
        return KEY_PREFIX + token
    }

    private companion object {
        const val PREFERENCES_NAME = "user_data_restore_provenance_v1"
        const val KEY_PREFIX = "owner."
        const val FORMAT_VERSION = 1
        val lock = Any()
    }
}

internal fun canonicalRestoreOwnerUid(ownerUid: String): String {
    val canonical = ownerUid.trim()
    require(canonical.isNotBlank() && canonical == ownerUid && canonical.length <= 128) {
        "Restore owner uid must be canonical and non-blank."
    }
    return canonical
}
