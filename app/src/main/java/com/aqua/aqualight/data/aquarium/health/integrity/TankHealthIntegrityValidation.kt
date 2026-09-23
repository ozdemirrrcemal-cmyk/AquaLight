package com.aqua.aqualight.data.aquarium.health.integrity

import com.aqua.aqualight.data.aquarium.health.AquariumHealthStoredRecordRules
import com.aqua.aqualight.data.aquarium.health.TankHealthIntegritySnapshot
import com.aqua.aqualight.data.store.StoreInvariantViolation

internal data class TankHealthIntegrityKey(
    val ownerUid: String,
    val tankId: Long
)

internal object TankHealthIntegrityValidation {

    fun key(
        ownerUid: String,
        tankId: Long
    ): TankHealthIntegrityKey {
        requireTankId(tankId)
        return TankHealthIntegrityKey(
            ownerUid = canonicalOwnerUid(ownerUid),
            tankId = tankId
        )
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

    private const val MAX_OWNER_UID_CHARS = 128
}
