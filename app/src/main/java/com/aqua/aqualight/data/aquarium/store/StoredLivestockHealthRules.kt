package com.aqua.aqualight.data.aquarium.store

import com.aqua.aqualight.application.aquarium.AquariumLivestockTaxonomy
import com.aqua.aqualight.application.aquarium.BaselineChange
import com.aqua.aqualight.application.aquarium.LivestockHealthSymptom
import com.aqua.aqualight.application.aquarium.LivestockHealthTrend

/** Validates a complete persisted timeline before the owner-scoped store accepts it. */
internal object StoredLivestockHealthRules {
    private const val MAX_OBSERVATIONS = 500
    private const val MAX_SYMPTOMS = 5
    private const val MAX_CHECKS = 300
    private const val MAX_AFFECTED = 100_000

    fun validate(tank: StoredTank) {
        if (tank.healthObservationsCount > MAX_OBSERVATIONS) {
            TankStoreRules.violation("Too many health observations in tank ${tank.id}.")
        }
        val ids = mutableSetOf<Long>()
        tank.healthObservationsList.forEach { observation ->
            TankStoreRules.requirePositiveId("health.id", observation.id)
            if (!ids.add(observation.id)) TankStoreRules.violation("Duplicate health observation id.")
            validateObservation(observation)
        }
    }

    private fun validateObservation(observation: StoredLivestockHealthObservation) {
        with(TankStoreRules) {
            requirePositiveId("health.livestockId", observation.livestockId)
            requireCanonicalRequiredText("health.livestockName", observation.livestockName,
                MAX_ENTITY_NAME_CHARS)
            requireCanonicalRequiredText("health.catalogEntryId", observation.catalogEntryId,
                MAX_PRODUCT_ID_CHARS)
            if (observation.livestockCategory !in AquariumLivestockTaxonomy.categoryCodes) {
                violation("health.livestockCategory is invalid.")
            }
            if (observation.affectedCount !in 1..MAX_AFFECTED) violation("health.affectedCount is invalid.")
            requireTimestamp("health.observedAtMillis", observation.observedAtMillis)
            requireTimestamp("health.startedAtMillis", observation.startedAtMillis)
            if (observation.startedAtMillis > observation.observedAtMillis) {
                violation("Health symptom onset cannot follow its observation.")
            }
            requireCanonicalOptionalText("health.note", observation.note, MAX_NOTE_CHARS)
            requireCanonicalOptionalText("health.photoUri", observation.photoUri, MAX_URI_CHARS)
            if (observation.symptomCodesCount !in 1..MAX_SYMPTOMS ||
                observation.symptomCodesList.size != observation.symptomCodesList.toSet().size ||
                observation.symptomCodesList.any { code ->
                    LivestockHealthSymptom.entries.none { it.code == code }
                }
            ) violation("Health symptoms are invalid.")
            if (observation.trendCode !in LivestockHealthTrend.entries.map { it.code } ||
                observation.trendCode == LivestockHealthTrend.RESOLVED.code
            ) violation("Initial health trend is invalid.")
            if (observation.baselineChangeCode.isNotEmpty() &&
                BaselineChange.entries.none { it.code == observation.baselineChangeCode }
            ) violation("Health baseline change is invalid.")
            if (LivestockHealthSymptom.SURFACE_FREQUENCY_CHANGE.code in observation.symptomCodesList &&
                observation.baselineChangeCode.isEmpty()
            ) violation("Surface behavior requires baseline context.")
        }
        validateChecks(observation)
    }

    private fun validateChecks(observation: StoredLivestockHealthObservation) {
        if (observation.checksCount > MAX_CHECKS) TankStoreRules.violation("Too many follow-up checks.")
        var lastAt = observation.observedAtMillis
        val checkIds = mutableSetOf<Long>()
        observation.checksList.forEach { check ->
            with(TankStoreRules) {
                requirePositiveId("health.check.id", check.id)
                if (!checkIds.add(check.id)) violation("Duplicate health check id.")
                requireTimestamp("health.check.observedAtMillis", check.observedAtMillis)
                if (check.observedAtMillis < lastAt) violation("Health checks must be chronological.")
                lastAt = check.observedAtMillis
                if (check.affectedCount !in 1..MAX_AFFECTED) violation("Health check count is invalid.")
                if (LivestockHealthTrend.entries.none { it.code == check.trendCode }) {
                    violation("Health check trend is invalid.")
                }
                requireCanonicalOptionalText("health.check.note", check.note, MAX_NOTE_CHARS)
                requireCanonicalOptionalText("health.check.photoUri", check.photoUri, MAX_URI_CHARS)
            }
        }
        with(TankStoreRules) {
            if (observation.closedAtMillis == 0L) {
                if (observation.outcomeCode.isNotEmpty()) violation("Open health observation has an outcome.")
            } else {
                requireTimestamp("health.closedAtMillis", observation.closedAtMillis)
                if (observation.closedAtMillis < lastAt ||
                    observation.outcomeCode !in setOf("ended", LivestockHealthTrend.RESOLVED.code)
                ) violation("Health closure is invalid.")
            }
        }
    }
}
