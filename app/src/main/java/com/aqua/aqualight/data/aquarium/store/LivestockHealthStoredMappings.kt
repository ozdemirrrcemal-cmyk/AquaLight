package com.aqua.aqualight.data.aquarium.store

import com.aqua.aqualight.application.aquarium.BaselineChange
import com.aqua.aqualight.application.aquarium.LivestockHealthCheck
import com.aqua.aqualight.application.aquarium.LivestockHealthObservation
import com.aqua.aqualight.application.aquarium.LivestockHealthSymptom
import com.aqua.aqualight.application.aquarium.LivestockHealthTrend

internal fun LivestockHealthObservation.toStored(): StoredLivestockHealthObservation =
    StoredLivestockHealthObservation.newBuilder()
        .setId(id)
        .setLivestockId(livestockId)
        .setLivestockName(livestockName)
        .setLivestockCategory(livestockCategory)
        .setCatalogEntryId(catalogEntryId)
        .setAffectedCount(affectedCount)
        .setObservedAtMillis(observedAtMillis)
        .setStartedAtMillis(startedAtMillis)
        .addAllSymptomCodes(symptoms.map(LivestockHealthSymptom::code))
        .setTrendCode(trend.code)
        .setNote(note)
        .setPhotoUri(photoUri.orEmpty())
        .setBaselineChangeCode(baselineChange?.code.orEmpty())
        .setClosedAtMillis(closedAtMillis ?: 0L)
        .setOutcomeCode(outcome?.code.orEmpty())
        .addAllChecks(checks.map(LivestockHealthCheck::toStored))
        .build()

internal fun LivestockHealthCheck.toStored(): StoredLivestockHealthCheck =
    StoredLivestockHealthCheck.newBuilder()
        .setId(id)
        .setObservedAtMillis(observedAtMillis)
        .setAffectedCount(affectedCount)
        .setTrendCode(trend.code)
        .setNote(note)
        .setPhotoUri(photoUri.orEmpty())
        .build()

internal fun StoredLivestockHealthObservation.toApplication(): LivestockHealthObservation =
    LivestockHealthObservation(
        id = id,
        livestockId = livestockId,
        livestockName = livestockName,
        livestockCategory = livestockCategory,
        catalogEntryId = catalogEntryId,
        affectedCount = affectedCount,
        observedAtMillis = observedAtMillis,
        startedAtMillis = startedAtMillis,
        symptoms = symptomCodesList.map(LivestockHealthSymptom::fromCode),
        trend = LivestockHealthTrend.fromCode(trendCode),
        note = note,
        photoUri = photoUri.ifBlank { null },
        baselineChange = baselineChangeCode.takeIf(String::isNotBlank)
            ?.let(BaselineChange::fromCode),
        closedAtMillis = closedAtMillis.takeIf { it > 0L },
        outcome = outcomeCode.takeIf { it != "ended" && it.isNotBlank() }
            ?.let(LivestockHealthTrend::fromCode),
        checks = checksList.map { check ->
            LivestockHealthCheck(
                id = check.id,
                observedAtMillis = check.observedAtMillis,
                affectedCount = check.affectedCount,
                trend = LivestockHealthTrend.fromCode(check.trendCode),
                note = check.note,
                photoUri = check.photoUri.ifBlank { null }
            )
        }
    )
