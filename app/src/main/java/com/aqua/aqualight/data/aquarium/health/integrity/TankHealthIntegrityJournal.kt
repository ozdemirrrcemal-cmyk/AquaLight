package com.aqua.aqualight.data.aquarium.health.integrity

import android.content.Context
import android.content.SharedPreferences
import com.aqua.aqualight.data.aquarium.health.TankHealthIntegritySnapshot
import com.aqua.aqualight.data.store.StoreInvariantViolation

internal interface TankHealthIntegrityTransactions {
    fun begin(
        ownerUid: String,
        tankIds: Collection<Long>
    )

    fun captureSnapshots(
        ownerUid: String,
        snapshotsByTank: Map<Long, TankHealthIntegritySnapshot>
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

internal object TankHealthIntegrityJournal :
    TankHealthIntegrityTransactions {

    internal enum class State {
        BLOCKED,
        SNAPSHOTS_CAPTURED
    }

    internal data class PendingDeletion(
        val ownerUid: String,
        val tankId: Long,
        val state: State,
        val snapshot: TankHealthIntegritySnapshot
    )

    private data class Key(
        val ownerUid: String,
        val tankId: Long
    )

    private val lock = Any()
    private val entries = linkedMapOf<Key, PendingDeletion>()
    private val processTombstones = linkedSetOf<Key>()
    private val rollbackWritesAllowed = linkedSetOf<Key>()

    @Volatile
    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        synchronized(lock) {
            if (preferences != null) {
                return
            }

            val loadedPreferences =
                context.applicationContext.getSharedPreferences(
                    PREFERENCES_NAME,
                    Context.MODE_PRIVATE
                )
            val loadedEntries = linkedMapOf<Key, PendingDeletion>()

            loadedPreferences
                .getStringSet(KEY_PENDING_DELETIONS, emptySet())
                .orEmpty()
                .forEach { encoded ->
                    val entry = TankHealthIntegrityCodec.decode(encoded)
                    val key = Key(entry.ownerUid, entry.tankId)
                    if (loadedEntries.put(key, entry) != null) {
                        throw StoreInvariantViolation(
                            "Duplicate tank-health integrity journal entry for " +
                                entry.ownerUid + "/" + entry.tankId + "."
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
        val owner =
            TankHealthIntegrityCodec.canonicalOwnerUid(ownerUid)
        val normalizedIds = tankIds.distinct()
        require(normalizedIds.isNotEmpty()) {
            "At least one tank id is required for a Health integrity transaction."
        }
        normalizedIds.forEach(
            TankHealthIntegrityCodec::requireTankId
        )

        synchronized(lock) {
            check(preferences != null) {
                NOT_INITIALIZED_MESSAGE
            }
            val next = LinkedHashMap(entries)
            normalizedIds.forEach { tankId ->
                val key = Key(owner, tankId)
                if (key in processTombstones) {
                    throw StoreInvariantViolation(
                        "A deleted tank id cannot start another Health integrity transaction."
                    )
                }
                if (key in next) {
                    throw StoreInvariantViolation(
                        "A tank-health integrity transaction is already pending."
                    )
                }
                next[key] = PendingDeletion(
                    ownerUid = owner,
                    tankId = tankId,
                    state = State.BLOCKED,
                    snapshot = TankHealthIntegrityCodec.emptySnapshot()
                )
            }
            persistAndReplace(next)
        }
    }

    override fun captureSnapshots(
        ownerUid: String,
        snapshotsByTank: Map<Long, TankHealthIntegritySnapshot>
    ) {
        val owner =
            TankHealthIntegrityCodec.canonicalOwnerUid(ownerUid)
        require(snapshotsByTank.isNotEmpty()) {
            "Health snapshot capture requires at least one tank."
        }

        synchronized(lock) {
            check(preferences != null) {
                NOT_INITIALIZED_MESSAGE
            }
            val next = LinkedHashMap(entries)

            snapshotsByTank.forEach { (tankId, snapshot) ->
                TankHealthIntegrityCodec.requireTankId(tankId)
                TankHealthIntegrityCodec.validateSnapshot(
                    ownerUid = owner,
                    tankId = tankId,
                    snapshot = snapshot
                )
                val key = Key(owner, tankId)
                val current = next[key]
                    ?: throw StoreInvariantViolation(
                        "Health snapshot capture has no matching blocked transaction."
                    )
                if (current.state != State.BLOCKED) {
                    throw StoreInvariantViolation(
                        "Health snapshots may only be captured once."
                    )
                }
                next[key] = current.copy(
                    state = State.SNAPSHOTS_CAPTURED,
                    snapshot = snapshot
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
            check(preferences != null) {
                NOT_INITIALIZED_MESSAGE
            }
            if (key !in entries) {
                throw StoreInvariantViolation(
                    "Health rollback writes require a pending integrity transaction."
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
            check(preferences != null) {
                NOT_INITIALIZED_MESSAGE
            }
            if (key !in entries) {
                throw StoreInvariantViolation(
                    "Cannot complete a missing tank-health integrity transaction."
                )
            }
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
            check(preferences != null) {
                NOT_INITIALIZED_MESSAGE
            }
            val next = LinkedHashMap(entries)
            next.remove(key)
            persistAndReplace(next)
            rollbackWritesAllowed -= key
        }
    }

    fun pendingForOwner(
        ownerUid: String
    ): List<PendingDeletion> {
        val owner =
            TankHealthIntegrityCodec.canonicalOwnerUid(ownerUid)
        return synchronized(lock) {
            check(preferences != null) {
                NOT_INITIALIZED_MESSAGE
            }
            entries.values
                .filter { entry -> entry.ownerUid == owner }
                .sortedBy { entry -> entry.tankId }
        }
    }

    fun clearOwner(ownerUid: String) {
        val owner =
            TankHealthIntegrityCodec.canonicalOwnerUid(ownerUid)
        synchronized(lock) {
            check(preferences != null) {
                NOT_INITIALIZED_MESSAGE
            }
            val next = LinkedHashMap(entries)
            next.keys
                .filter { key -> key.ownerUid == owner }
                .forEach(next::remove)
            persistAndReplace(next)
            processTombstones.removeAll { key ->
                key.ownerUid == owner
            }
            rollbackWritesAllowed.removeAll { key ->
                key.ownerUid == owner
            }
        }
    }

    fun requireWritable(
        ownerUid: String,
        tankId: Long
    ) {
        val key = validatedKey(ownerUid, tankId)
        synchronized(lock) {
            val blocked =
                key in entries || key in processTombstones
            if (blocked && key !in rollbackWritesAllowed) {
                throw StoreInvariantViolation(
                    "Aquarium-health write targets a tank with an active " +
                        "deletion transaction."
                )
            }
        }
    }

    private fun persistAndReplace(
        next: Map<Key, PendingDeletion>
    ) {
        val targetPreferences = checkNotNull(preferences) {
            NOT_INITIALIZED_MESSAGE
        }
        val encoded = next.values.mapTo(
            linkedSetOf(),
            TankHealthIntegrityCodec::encode
        )
        check(
            targetPreferences.edit()
                .putStringSet(KEY_PENDING_DELETIONS, encoded)
                .commit()
        ) {
            "Tank-health integrity journal could not be committed."
        }
        entries.clear()
        entries.putAll(next)
    }

    private fun validatedKey(
        ownerUid: String,
        tankId: Long
    ): Key {
        TankHealthIntegrityCodec.requireTankId(tankId)
        return Key(
            TankHealthIntegrityCodec.canonicalOwnerUid(ownerUid),
            tankId
        )
    }

    private const val PREFERENCES_NAME =
        "tank_health_integrity_journal"
    private const val KEY_PENDING_DELETIONS =
        "pending_deletions"
    private const val NOT_INITIALIZED_MESSAGE =
        "TankHealthIntegrityJournal must be initialized before owner data is used."
}
