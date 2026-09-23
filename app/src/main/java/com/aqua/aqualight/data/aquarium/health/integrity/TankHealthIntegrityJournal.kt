package com.aqua.aqualight.data.aquarium.health.integrity

import android.content.Context
import android.content.SharedPreferences
import com.aqua.aqualight.data.aquarium.health.AquariumHealthStore
import com.aqua.aqualight.data.aquarium.health.AquariumHealthStoreRules
import com.aqua.aqualight.data.aquarium.health.TankHealthIntegritySnapshot
import com.aqua.aqualight.data.store.StoreInvariantViolation
import java.util.Base64

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

internal object TankHealthIntegrityJournal : TankHealthIntegrityTransactions {

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
                            "Duplicate tank-health integrity journal entry for " +
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
            "At least one tank id is required for a Health integrity transaction."
        }
        normalizedIds.forEach(::requireTankId)

        synchronized(lock) {
            requireInitialized()
            val next = LinkedHashMap(entries)
            normalizedIds.forEach { tankId ->
                val key = Key(owner, tankId)
                if (key in processTombstones) {
                    violation(
                        "A deleted tank id cannot start another Health integrity transaction."
                    )
                }
                if (key in next) {
                    violation(
                        "A tank-health integrity transaction is already pending."
                    )
                }
                next[key] = PendingDeletion(
                    ownerUid = owner,
                    tankId = tankId,
                    state = State.BLOCKED,
                    snapshot = emptySnapshot()
                )
            }
            persistAndReplace(next)
        }
    }

    override fun captureSnapshots(
        ownerUid: String,
        snapshotsByTank: Map<Long, TankHealthIntegritySnapshot>
    ) {
        val owner = canonicalOwnerUid(ownerUid)
        require(snapshotsByTank.isNotEmpty()) {
            "Health snapshot capture requires at least one tank."
        }

        synchronized(lock) {
            requireInitialized()
            val next = LinkedHashMap(entries)

            snapshotsByTank.forEach { (tankId, snapshot) ->
                requireTankId(tankId)
                validateSnapshot(owner, tankId, snapshot)
                val key = Key(owner, tankId)
                val current = next[key] ?: violation(
                    "Health snapshot capture has no matching blocked transaction."
                )
                if (current.state != State.BLOCKED) {
                    violation("Health snapshots may only be captured once.")
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
            requireInitialized()
            if (key !in entries) {
                violation(
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
            requireInitialized()
            if (key !in entries) {
                violation("Cannot complete a missing tank-health integrity transaction.")
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
            val next = LinkedHashMap(entries)
            next.keys
                .filter { key -> key.ownerUid == owner }
                .forEach(next::remove)
            persistAndReplace(next)
            processTombstones.removeAll { key -> key.ownerUid == owner }
            rollbackWritesAllowed.removeAll { key -> key.ownerUid == owner }
        }
    }

    fun requireWritable(
        ownerUid: String,
        tankId: Long
    ) {
        val key = validatedKey(ownerUid, tankId)
        synchronized(lock) {
            val blocked = key in entries || key in processTombstones
            if (blocked && key !in rollbackWritesAllowed) {
                violation(
                    "Aquarium-health write targets a tank with an active deletion transaction."
                )
            }
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
                "Tank-health integrity journal could not be committed."
            )
        }
        entries.clear()
        entries.putAll(next)
    }

    private fun encodeEntry(entry: PendingDeletion): String {
        val stateToken = when (entry.state) {
            State.BLOCKED -> "B"
            State.SNAPSHOTS_CAPTURED -> "S"
        }
        val ownerToken = encodeBytes(entry.ownerUid.toByteArray(Charsets.UTF_8))
        val snapshotToken = if (entry.state == State.BLOCKED) {
            ""
        } else {
            encodeBytes(snapshotToStore(entry.snapshot).toByteArray())
        }
        return listOf(
            FORMAT_VERSION,
            stateToken,
            ownerToken,
            entry.tankId.toString(),
            snapshotToken
        ).joinToString("|")
    }

    private fun decodeEntry(encoded: String): PendingDeletion {
        val parts = encoded.split('|', limit = 5)
        if (parts.size != 5 || parts[0] != FORMAT_VERSION) {
            violation("Tank-health integrity journal contains an unsupported entry.")
        }

        val state = when (parts[1]) {
            "B" -> State.BLOCKED
            "S" -> State.SNAPSHOTS_CAPTURED
            else -> violation(
                "Tank-health integrity journal contains an invalid state."
            )
        }

        val ownerUid = runCatching {
            String(decodeBytes(parts[2]), Charsets.UTF_8)
        }.getOrElse { error ->
            violation(
                "Tank-health integrity owner is unreadable: ${error.message}"
            )
        }.let(::canonicalOwnerUid)

        val tankId = parts[3].toLongOrNull()
            ?: violation(
                "Tank-health integrity journal contains an invalid tank id."
            )
        requireTankId(tankId)

        val snapshot = if (state == State.BLOCKED) {
            if (parts[4].isNotEmpty()) {
                violation(
                    "A blocked tank-health transaction must not contain a snapshot."
                )
            }
            emptySnapshot()
        } else {
            if (parts[4].isBlank()) {
                violation(
                    "A captured tank-health transaction must contain a snapshot."
                )
            }
            val store = runCatching {
                AquariumHealthStore.parseFrom(decodeBytes(parts[4]))
            }.getOrElse { error ->
                violation(
                    "Tank-health integrity snapshot is corrupt: ${error.message}"
                )
            }
            storeToSnapshot(AquariumHealthStoreRules.validateStore(store))
        }

        validateSnapshot(ownerUid, tankId, snapshot)
        return PendingDeletion(
            ownerUid = ownerUid,
            tankId = tankId,
            state = state,
            snapshot = snapshot
        )
    }

    private fun snapshotToStore(
        snapshot: TankHealthIntegritySnapshot
    ): AquariumHealthStore = AquariumHealthStoreRules.validateStore(
        AquariumHealthStoreRules.defaultStore()
            .toBuilder()
            .addAllWaterTests(snapshot.waterTests)
            .addAllLivestockObservations(snapshot.livestockObservations)
            .addAllPlantObservations(snapshot.plantObservations)
            .build()
    )

    private fun storeToSnapshot(store: AquariumHealthStore) =
        TankHealthIntegritySnapshot(
            waterTests = store.waterTestsList,
            livestockObservations = store.livestockObservationsList,
            plantObservations = store.plantObservationsList
        )

    private fun validateSnapshot(
        ownerUid: String,
        tankId: Long,
        snapshot: TankHealthIntegritySnapshot
    ) {
        snapshot.waterTests.forEach { test ->
            AquariumHealthStoreRules.validateWaterTest(test, ownerUid)
            if (test.tankId != tankId) {
                violation("Health snapshot water test references another tank.")
            }
        }
        snapshot.livestockObservations.forEach { observation ->
            AquariumHealthStoredRecordRules.validateLivestockObservation(
                observation,
                ownerUid
            )
            if (observation.tankId != tankId) {
                violation(
                    "Health snapshot livestock observation references another tank."
                )
            }
        }
        snapshot.plantObservations.forEach { observation ->
            AquariumHealthStoredRecordRules.validatePlantObservation(
                observation,
                ownerUid
            )
            if (observation.tankId != tankId) {
                violation(
                    "Health snapshot plant observation references another tank."
                )
            }
        }
    }

    private fun emptySnapshot() = TankHealthIntegritySnapshot(
        waterTests = emptyList(),
        livestockObservations = emptyList(),
        plantObservations = emptyList()
    )

    private fun PendingDeletion.key(): Key = Key(ownerUid, tankId)

    private fun validatedKey(ownerUid: String, tankId: Long): Key {
        requireTankId(tankId)
        return Key(canonicalOwnerUid(ownerUid), tankId)
    }

    private fun canonicalOwnerUid(value: String): String {
        val canonical = value.trim()
        if (canonical.isBlank() || canonical != value || canonical.length > 128) {
            violation(
                "Tank-health integrity owner uid must be canonical and non-blank."
            )
        }
        return canonical
    }

    private fun requireTankId(tankId: Long) {
        if (tankId <= 0L) {
            violation("Tank-health integrity tank id must be positive.")
        }
    }

    private fun requireInitialized(): SharedPreferences {
        return preferences ?: throw IllegalStateException(
            "TankHealthIntegrityJournal must be initialized before owner data is used."
        )
    }

    private fun encodeBytes(bytes: ByteArray): String =
        base64Encoder.encodeToString(bytes)

    private fun decodeBytes(value: String): ByteArray =
        base64Decoder.decode(value)

    private fun violation(message: String): Nothing {
        throw StoreInvariantViolation(message)
    }

    private const val PREFERENCES_NAME = "tank_health_integrity_journal"
    private const val KEY_PENDING_DELETIONS = "pending_deletions"
    private const val FORMAT_VERSION = "v1"

    private val base64Encoder = Base64.getUrlEncoder().withoutPadding()
    private val base64Decoder = Base64.getUrlDecoder()
}
