package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.LivestockObservationSnapshot
import com.aqua.aqualight.base.BaseActivity

internal fun Fragment.showLivestockHealthSaveFailure() {
    (activity as? BaseActivity)?.showSnackBar(
        getString(R.string.livestock_health_save_failed), BaseActivity.SnackType.ERROR
    )
}

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
