package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.health.LivestockObservationSnapshot

internal fun LivestockObservationSnapshot.toActiveUi(): ActiveLivestockFollowupUi =
    ActiveLivestockFollowupUi(
        observationId = id,
        livestockId = livestockId,
        symptomKey = symptomKeys.first(),
        affectedCount = checks.maxByOrNull { it.checkedAtMillis }?.affectedCount ?: affectedCount,
        totalCount = totalCount,
        startedAtMillis = createdAtMillis,
        lastCheckAtMillis = checks.maxOfOrNull { it.checkedAtMillis } ?: 0L
    )

internal fun LivestockObservationSnapshot.toClosedUi(): ClosedLivestockFollowupUi =
    ClosedLivestockFollowupUi(
        observationId = id,
        livestockId = livestockId,
        symptomKey = symptomKeys.first(),
        affectedCount = checks.maxByOrNull { it.checkedAtMillis }?.affectedCount ?: affectedCount,
        totalCount = totalCount,
        startedAtMillis = createdAtMillis,
        closedAtMillis = requireNotNull(closedAtMillis),
        closeReason = closeReason.orEmpty(),
        checkCount = checks.size
    )
