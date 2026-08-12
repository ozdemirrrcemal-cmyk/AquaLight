package com.aqua.aqualight.data.care.integrity

import android.content.Context
import android.content.SharedPreferences
import com.aqua.aqualight.data.care.CareTaskStoreRules
import com.aqua.aqualight.data.care.StoredCareTask
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType
import com.aqua.aqualight.data.store.StoreInvariantViolation
import java.util.Base64

internal interface TankCareIntegrityTransactions {
    fun begin(
        ownerUid: String,
        tankIds: Collection<Long>
    )

    fun captureSnapshots(
        ownerUid: String,
        snapshotsByTank: Map<Long, List<CareTask>>
    )

    suspend fun <T> withRollbackWritesAllowed(
        ownerUid: String,
        tankId: Long,
        block: suspend () -> T
    ): T

    fun complete(
        ownerUid: String,
        tankId: Long
    )

    fun abort(
        ownerUid: String,
        tankId: Long
    )
}

/**
 * Durable compensation journal for the two authoritative Tank and Care Task stores.
 *
 * A deletion first blocks writes for the target tank, then snapshots its tasks. Care
 * tasks are removed before the tank record. If the tank write fails, the snapshots are
 * restored. If the process dies, owner-session recovery decides whether to restore or
 * finish cleanup by checking the authoritative tank store.
 */
internal object TankCareIntegrityJournal : TankCareIntegrityTransactions {

    internal enum class State {
        BLOCKED,
        SNAPSHOTS_CAPTURED
    }

    internal data class PendingDeletion(
        val ownerUid: String,
        val tankId: Long,
        val state: State,
        val taskSnapshots: List<CareTask>
    )

    private data class Key(
        val ownerUid: String,
        val tankId: Long
    )

    private val lock = Any()
    private val entries = linkedMapOf<Key, PendingDeletion>()

    // Completed deletions remain blocked for the rest of this process. This rejects a
    // stale coroutine that passed the tank-exists check before deletion began.
    private val processTombstones = linkedSetOf<Key>()
    private val rollbackWritesAllowed = linkedSetOf<Key>()

    @Volatile
    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        synchronized(lock) {
            if (preferences != null) return

            val loadedPreferences = context.applicationContext.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
            val loadedEntries = linkedMapOf<Key, PendingDeletion>()

            loadedPreferences
                .getStringSet(KEY_PENDING_DELETIONS, emptySet())
                .orEmpty()
                .forEach { encoded ->
                    val entry = decodeEntry(encoded)
                    val key = entry.key()
                    if (loadedEntries.put(key, entry) != null) {
                        violation(
                            "Duplicate tank-care integrity journal entry for " +
                                "${entry.ownerUid}/${entry.tankId}."
                        )
                    }
                }

            preferences = loadedPreferences
            entries.clear()
            entries.putAll(loadedEntries)
        }
    }

    override fun begin(
        ownerUid: String,
        tankIds: Collection<Long>
    ) {
        val owner = canonicalOwnerUid(ownerUid)
        val normalizedIds = tankIds.distinct()
        require(normalizedIds.isNotEmpty()) {
            "At least one tank id is required for an integrity transaction."
        }
        normalizedIds.forEach(CareTaskStoreRules::requireValidTankId)

        synchronized(lock) {
            requireInitialized()
            val next = LinkedHashMap(entries)

            normalizedIds.forEach { tankId ->
                val key = Key(owner, tankId)
                if (key in processTombstones) {
                    violation(
                        "A deleted tank id cannot start another care integrity transaction."
                    )
                }
                if (key in next) {
                    violation(
                        "A tank-care integrity transaction is already pending."
                    )
                }
                next[key] = PendingDeletion(
                    ownerUid = owner,
                    tankId = tankId,
                    state = State.BLOCKED,
                    taskSnapshots = emptyList()
                )
            }

            persistAndReplace(next)
        }
    }

    override fun captureSnapshots(
        ownerUid: String,
        snapshotsByTank: Map<Long, List<CareTask>>
    ) {
        val owner = canonicalOwnerUid(ownerUid)
        require(snapshotsByTank.isNotEmpty()) {
            "Snapshot capture requires at least one tank."
        }

        synchronized(lock) {
            requireInitialized()
            val next = LinkedHashMap(entries)

            snapshotsByTank.forEach { (tankId, snapshots) ->
                CareTaskStoreRules.requireValidTankId(tankId)
                val key = Key(owner, tankId)
                val current = next[key] ?: violation(
                    "Tank-care snapshot capture has no matching blocked transaction."
                )
                if (current.state != State.BLOCKED) {
                    violation("Tank-care snapshots may only be captured once.")
                }

                validateSnapshots(
                    ownerUid = owner,
                    tankId = tankId,
                    snapshots = snapshots
                )
                next[key] = current.copy(
                    state = State.SNAPSHOTS_CAPTURED,
                    taskSnapshots = snapshots.toList()
                )
            }

            persistAndReplace(next)
        }
    }

    override suspend fun <T> withRollbackWritesAllowed(
        ownerUid: String,
        tankId: Long,
        block: suspend () -> T
    ): T {
        val key = validatedKey(ownerUid, tankId)

        synchronized(lock) {
            requireInitialized()
            if (key !in entries) {
                violation(
                    "Rollback writes require a pending tank-care integrity transaction."
                )
            }
            rollbackWritesAllowed += key
        }

        return try {
            block()
        } finally {
            synchronized(lock) {
                rollbackWritesAllowed -= key
            }
        }
    }

    override fun complete(
        ownerUid: String,
        tankId: Long
    ) {
        val key = validatedKey(ownerUid, tankId)

        synchronized(lock) {
            requireInitialized()
            if (key !in entries) {
                violation("Cannot complete a missing tank-care integrity transaction.")
            }

            // Install the in-process tombstone before removing the durable entry. A stale
            // writer therefore remains blocked even if the preference commit itself fails.
            processTombstones += key
            val next = LinkedHashMap(entries)
            next.remove(key)
            persistAndReplace(next)
        }
    }

    override fun abort(
        ownerUid: String,
        tankId: Long
    ) {
        val key = validatedKey(ownerUid, tankId)

        synchronized(lock) {
            requireInitialized()
            val next = LinkedHashMap(entries)
            next.remove(key)
            persistAndReplace(next)
            rollbackWritesAllowed -= key
        }
    }

    fun pendingForOwner(ownerUid: String): List<PendingDeletion> {
        val owner = canonicalOwnerUid(ownerUid)
        return synchronized(lock) {
            requireInitialized()
            entries.values
                .filter { entry -> entry.ownerUid == owner }
                .sortedBy { entry -> entry.tankId }
        }
    }

    fun clearOwner(ownerUid: String) {
        val owner = canonicalOwnerUid(ownerUid)
        synchronized(lock) {
            requireInitialized()

            val keysToRemove = entries.keys
                .filter { key -> key.ownerUid == owner }
            val next = LinkedHashMap(entries)
            keysToRemove.forEach { key ->
                next.remove(key)
            }

            persistAndReplace(next)
            processTombstones.removeAll { key -> key.ownerUid == owner }
            rollbackWritesAllowed.removeAll { key -> key.ownerUid == owner }
        }
    }

    fun requireNoBlockedReferences(tasks: Iterable<StoredCareTask>) {
        synchronized(lock) {
            tasks.forEach { task ->
                val key = Key(task.ownerUid, task.tankId)
                val blocked = key in entries || key in processTombstones
                if (blocked && key !in rollbackWritesAllowed) {
                    violation(
                        "Care-task write targets a tank with an active deletion transaction."
                    )
                }
            }
        }
    }

    fun isWriteBlocked(ownerUid: String, tankId: Long): Boolean {
        val key = validatedKey(ownerUid, tankId)
        return synchronized(lock) {
            (key in entries || key in processTombstones) &&
                key !in rollbackWritesAllowed
        }
    }

    private fun persistAndReplace(next: Map<Key, PendingDeletion>) {
        val targetPreferences = requireInitialized()
        val encoded = next.values.mapTo(linkedSetOf(), ::encodeEntry)
        val committed = targetPreferences.edit()
            .putStringSet(KEY_PENDING_DELETIONS, encoded)
            .commit()
        if (!committed) {
            throw IllegalStateException(
                "Tank-care integrity journal could not be committed."
            )
        }
        entries.clear()
        entries.putAll(next)
    }

    private fun requireInitialized(): SharedPreferences {
        return preferences ?: throw IllegalStateException(
            "TankCareIntegrityJournal must be initialized before owner data is used."
        )
    }

    private fun encodeEntry(entry: PendingDeletion): String {
        val stateToken = when (entry.state) {
            State.BLOCKED -> "B"
            State.SNAPSHOTS_CAPTURED -> "S"
        }
        val ownerToken = encodeBytes(
            entry.ownerUid.toByteArray(Charsets.UTF_8)
        )
        val taskToken = entry.taskSnapshots.joinToString(",") { task ->
            encodeBytes(task.toStoredTask().toByteArray())
        }
        return listOf(
            FORMAT_VERSION,
            stateToken,
            ownerToken,
            entry.tankId.toString(),
            taskToken
        ).joinToString("|")
    }

    private fun decodeEntry(encoded: String): PendingDeletion {
        val parts = encoded.split('|', limit = 5)
        if (parts.size != 5 || parts[0] != FORMAT_VERSION) {
            violation("Tank-care integrity journal contains an unsupported entry.")
        }

        val state = when (parts[1]) {
            "B" -> State.BLOCKED
            "S" -> State.SNAPSHOTS_CAPTURED
            else -> violation(
                "Tank-care integrity journal contains an invalid state."
            )
        }
        val ownerUid = runCatching {
            String(decodeBytes(parts[2]), Charsets.UTF_8)
        }.getOrElse { error ->
            violation(
                "Tank-care integrity journal owner is unreadable: ${error.message}"
            )
        }.let(::canonicalOwnerUid)
        val tankId = parts[3].toLongOrNull()
            ?: violation(
                "Tank-care integrity journal contains an invalid tank id."
            )
        CareTaskStoreRules.requireValidTankId(tankId)

        val snapshots = if (parts[4].isBlank()) {
            emptyList()
        } else {
            parts[4].split(',').map { taskToken ->
                val stored = runCatching {
                    StoredCareTask.parseFrom(decodeBytes(taskToken))
                }.getOrElse { error ->
                    violation(
                        "Tank-care integrity task snapshot is corrupt: ${error.message}"
                    )
                }
                stored.toDomainTask()
            }
        }

        if (state == State.BLOCKED && snapshots.isNotEmpty()) {
            violation("A blocked tank-care transaction must not contain snapshots.")
        }
        validateSnapshots(
            ownerUid = ownerUid,
            tankId = tankId,
            snapshots = snapshots
        )

        return PendingDeletion(
            ownerUid = ownerUid,
            tankId = tankId,
            state = state,
            taskSnapshots = snapshots
        )
    }

    private fun validateSnapshots(
        ownerUid: String,
        tankId: Long,
        snapshots: List<CareTask>
    ) {
        val taskIds = mutableSetOf<Long>()
        snapshots.forEach { task ->
            CareTaskStoreRules.validateTask(task, expectedOwnerUid = ownerUid)
            if (task.tankId != tankId) {
                violation(
                    "Tank-care integrity snapshot references another tank."
                )
            }
            if (!taskIds.add(task.id)) {
                violation(
                    "Tank-care integrity snapshot contains duplicate task ids."
                )
            }
        }
    }

    private fun CareTask.toStoredTask(): StoredCareTask {
        CareTaskStoreRules.validateTask(this)
        return StoredCareTask.newBuilder()
            .setId(id)
            .setOwnerUid(ownerUid)
            .setTankId(tankId)
            .setTitle(title)
            .setDescription(description)
            .setType(type.name)
            .setSource(source.name)
            .setStatus(status.name)
            .setDueAtMillis(dueAtMillis)
            .setCompletedAtMillis(completedAtMillis ?: 0L)
            .setRepeatEnabled(repeatEnabled)
            .setRepeatIntervalDays(repeatIntervalDays)
            .setReminderEnabled(reminderEnabled)
            .setMissedReminderEnabled(missedReminderEnabled)
            .setMissedReminderDays(missedReminderDays)
            .setWaterChangePercent(waterChangePercent ?: 0)
            .setNote(note)
            .setGeneratedRuleKey(generatedRuleKey)
            .setCreatedAtMillis(createdAtMillis)
            .setUpdatedAtMillis(updatedAtMillis)
            .build()
            .also(CareTaskStoreRules::validateStoredTask)
    }

    private fun StoredCareTask.toDomainTask(): CareTask {
        CareTaskStoreRules.validateStoredTask(this)
        return CareTask(
            id = id,
            ownerUid = ownerUid,
            tankId = tankId,
            title = title,
            description = description,
            type = CareTaskType.valueOf(type),
            source = CareTaskSource.valueOf(source),
            status = CareTaskStatus.valueOf(status),
            dueAtMillis = dueAtMillis,
            completedAtMillis = completedAtMillis.takeIf { value -> value > 0L },
            repeatEnabled = repeatEnabled,
            repeatIntervalDays = repeatIntervalDays,
            reminderEnabled = reminderEnabled,
            missedReminderEnabled = missedReminderEnabled,
            missedReminderDays = missedReminderDays,
            waterChangePercent = waterChangePercent.takeIf { value -> value > 0 },
            note = note,
            generatedRuleKey = generatedRuleKey,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        ).also(CareTaskStoreRules::validateTask)
    }

    private fun PendingDeletion.key(): Key = Key(ownerUid, tankId)

    private fun validatedKey(ownerUid: String, tankId: Long): Key {
        CareTaskStoreRules.requireValidTankId(tankId)
        return Key(canonicalOwnerUid(ownerUid), tankId)
    }

    private fun canonicalOwnerUid(value: String): String {
        val canonical = value.trim()
        if (canonical.isBlank() || canonical != value || canonical.length > 128) {
            violation(
                "Tank-care integrity owner uid must be canonical and non-blank."
            )
        }
        return canonical
    }

    private fun encodeBytes(bytes: ByteArray): String =
        base64Encoder.encodeToString(bytes)

    private fun decodeBytes(value: String): ByteArray =
        base64Decoder.decode(value)

    private fun violation(message: String): Nothing {
        throw StoreInvariantViolation(message)
    }

    private const val PREFERENCES_NAME = "tank_care_integrity_journal"
    private const val KEY_PENDING_DELETIONS = "pending_deletions"
    private const val FORMAT_VERSION = "v1"

    private val base64Encoder = Base64.getUrlEncoder().withoutPadding()
    private val base64Decoder = Base64.getUrlDecoder()
}
