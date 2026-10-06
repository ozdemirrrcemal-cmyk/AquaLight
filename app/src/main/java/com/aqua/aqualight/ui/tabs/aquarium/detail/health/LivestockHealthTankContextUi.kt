package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.application.care.CareTaskType
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.text.resolve
import com.aqua.aqualight.ui.tabs.maintenance.TankActivityUiState

internal fun formatLivestockHealthDateTime(
    fragment: Fragment,
    millis: Long
): String =
    LocaleFormatter.formatDate(fragment.requireContext(), millis) +
        " · " +
        LocaleFormatter.formatTime(fragment.requireContext(), millis)

internal fun TankActivityUiState.resolveLivestockHealthLastWaterChange(
    fragment: Fragment
): String {
    val hasWaterChange = completedTasks.any { task ->
        task.type == CareTaskType.WATER_CHANGE
    }
    return if (hasWaterChange) {
        fragment.requireContext().resolve(lastWaterChangeText).toString()
    } else {
        fragment.getString(R.string.livestock_health_last_water_change_value)
    }
}
