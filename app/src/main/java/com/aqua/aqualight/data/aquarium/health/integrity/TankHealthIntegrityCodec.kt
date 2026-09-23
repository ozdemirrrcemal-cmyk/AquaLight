package com.aqua.aqualight.data.aquarium.health.integrity

import com.aqua.aqualight.data.aquarium.health.AquariumHealthStore
import com.aqua.aqualight.data.aquarium.health.AquariumHealthStoreRules
import com.aqua.aqualight.data.aquarium.health.AquariumHealthStoredRecordRules
import com.aqua.aqualight.data.aquarium.health.TankHealthIntegritySnapshot
import com.aqua.aqualight.data.store.StoreInvariantViolation
import java.util.Base64

internal object TankHealthIntegrityCodec {

    fun encode(
        entry: TankHealthIntegrityJournal.PendingDeletion
    ): String {
        val stateToken = when (entry.state) {
            TankHealthIntegrityJournal.State.BLOCKED -> BLOCKED_TOKEN
            TankHealthIntegrityJournal.State.SNAPSHOTS_CAPTURED ->
                SNAPSHOTS_CAPTURED_TOKEN
        }
        val ownerToken = encoder.encodeToString(
            entry.ownerUid.toByteArray(Charsets.UTF_8)
        )
        val snapshotToken =
            if (entry.state == TankHealthIntegrityJournal.State.BLOCKED) {
                ""
            } else {
                encoder.encodeToString(
                    snapshotToStore(entry.snapshot).toByteArray()
                )
            }

        return listOf(
            FORMAT_VERSION,
            stateToken,
            ownerToken,
            entry.tankId.toString(),
            snapshotToken
        ).joinToString(ENTRY_SEPARATOR)
    }

    fun decode(
        encoded: String
    ): TankHealthIntegrityJournal.PendingDeletion {
        val parts = encoded.split(
            ENTRY_SEPARATOR_CHAR,
            limit = ENTRY_PART_COUNT
        )
        if (
            parts.size != ENTRY_PART_COUNT ||
            parts[VERSION_INDEX] != FORMAT_VERSION
        ) {
            violation(
                "Tank-health integrity journal contains an unsupported entry."
            )
        }

        val state = decodeState(parts[STATE_INDEX])
        val ownerUid = decodeOwner(parts[OWNER_INDEX])
        val tankId = parts[TANK_ID_INDEX].toLongOrNull()
            ?: violation(
                "Tank-health integrity journal contains an invalid tank id."
            )
        requireTankId(tankId)

        val snapshot = decodeSnapshot(
            state = state,
            token = parts[SNAPSHOT_INDEX]
        )
        validateSnapshot(
            ownerUid = ownerUid,
            tankId = tankId,
            snapshot = snapshot
        )

        return TankHealthIntegrityJournal.PendingDeletion(
            ownerUid = ownerUid,
            tankId = tankId,
            state = state,
            snapshot = snapshot
        )
    }

    fun emptySnapshot(): TankHealthIntegritySnapshot =
        TankHealthIntegritySnapshot(
            waterTests = emptyList(),
            livestockObservations = emptyList(),
            plantObservations = emptyList()
        )

    fun validateSnapshot(
        ownerUid: String,
        tankId: Long,
        snapshot: TankHealthIntegritySnapshot
    ) {
        snapshot.waterTests.forEach { test ->
            AquariumHealthStoredRecordRules.validateWaterTest(
                test,
                ownerUid
            )
            requireSameTank(
                expectedTankId = tankId,
                actualTankId = test.tankId,
                recordName = "water test"
            )
        }
        snapshot.livestockObservations.forEach { observation ->
            AquariumHealthStoredRecordRules.validateLivestockObservation(
                observation,
                ownerUid
            )
            requireSameTank(
                expectedTankId = tankId,
                actualTankId = observation.tankId,
                recordName = "livestock observation"
            )
        }
        snapshot.plantObservations.forEach { observation ->
            AquariumHealthStoredRecordRules.validatePlantObservation(
                observation,
                ownerUid
            )
            requireSameTank(
                expectedTankId = tankId,
                actualTankId = observation.tankId,
                recordName = "plant observation"
            )
        }
    }

    fun canonicalOwnerUid(value: String): String {
        val canonical = value.trim()
        if (
            canonical.isBlank() ||
            canonical != value ||
            canonical.length > MAX_OWNER_UID_CHARS
        ) {
            violation(
                "Tank-health integrity owner uid must be canonical and non-blank."
            )
        }
        return canonical
    }

    fun requireTankId(tankId: Long) {
        if (tankId <= 0L) {
            violation(
                "Tank-health integrity tank id must be positive."
            )
        }
    }

    private fun decodeState(
        token: String
    ): TankHealthIntegrityJournal.State = when (token) {
        BLOCKED_TOKEN -> TankHealthIntegrityJournal.State.BLOCKED
        SNAPSHOTS_CAPTURED_TOKEN ->
            TankHealthIntegrityJournal.State.SNAPSHOTS_CAPTURED
        else -> violation(
            "Tank-health integrity journal contains an invalid state."
        )
    }

    private fun decodeOwner(token: String): String {
        val decoded = runCatching {
            String(
                decoder.decode(token),
                Charsets.UTF_8
            )
        }.getOrElse { error ->
            violation(
                "Tank-health integrity owner is unreadable: " +
                    error.message.orEmpty()
            )
        }
        return canonicalOwnerUid(decoded)
    }

    private fun decodeSnapshot(
        state: TankHealthIntegrityJournal.State,
        token: String
    ): TankHealthIntegritySnapshot {
        if (state == TankHealthIntegrityJournal.State.BLOCKED) {
            if (token.isNotEmpty()) {
                violation(
                    "A blocked tank-health transaction must not contain a snapshot."
                )
            }
            return emptySnapshot()
        }

        if (token.isBlank()) {
            violation(
                "A captured tank-health transaction must contain a snapshot."
            )
        }

        val store = runCatching {
            AquariumHealthStore.parseFrom(decoder.decode(token))
        }.getOrElse { error ->
            violation(
                "Tank-health integrity snapshot is corrupt: " +
                    error.message.orEmpty()
            )
        }
        return storeToSnapshot(
            AquariumHealthStoreRules.validateStore(store)
        )
    }

    private fun snapshotToStore(
        snapshot: TankHealthIntegritySnapshot
    ): AquariumHealthStore = AquariumHealthStoreRules.validateStore(
        AquariumHealthStoreRules.defaultStore()
            .toBuilder()
            .addAllWaterTests(snapshot.waterTests)
            .addAllLivestockObservations(
                snapshot.livestockObservations
            )
            .addAllPlantObservations(snapshot.plantObservations)
            .build()
    )

    private fun storeToSnapshot(
        store: AquariumHealthStore
    ): TankHealthIntegritySnapshot =
        TankHealthIntegritySnapshot(
            waterTests = store.waterTestsList,
            livestockObservations = store.livestockObservationsList,
            plantObservations = store.plantObservationsList
        )

    private fun requireSameTank(
        expectedTankId: Long,
        actualTankId: Long,
        recordName: String
    ) {
        if (actualTankId != expectedTankId) {
            violation(
                "Health snapshot $recordName references another tank."
            )
        }
    }

    private fun violation(message: String): Nothing {
        throw StoreInvariantViolation(message)
    }

    private const val FORMAT_VERSION = "v1"
    private const val BLOCKED_TOKEN = "B"
    private const val SNAPSHOTS_CAPTURED_TOKEN = "S"
    private const val ENTRY_SEPARATOR = "|"
    private const val ENTRY_SEPARATOR_CHAR = '|'
    private const val ENTRY_PART_COUNT = 5
    private const val VERSION_INDEX = 0
    private const val STATE_INDEX = 1
    private const val OWNER_INDEX = 2
    private const val TANK_ID_INDEX = 3
    private const val SNAPSHOT_INDEX = 4
    private const val MAX_OWNER_UID_CHARS = 128

    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
}
