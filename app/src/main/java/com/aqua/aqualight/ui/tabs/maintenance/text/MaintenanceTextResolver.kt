package com.aqua.aqualight.ui.tabs.maintenance.text

import androidx.annotation.ColorInt
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.care.CareTaskSnapshot
import com.aqua.aqualight.application.care.CareTaskSource
import com.aqua.aqualight.application.care.CareTaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Presentation-only text/icon boundary for maintenance screens. */
interface MaintenanceTextResolver {
    /** Re-emits presentation state whenever the active application language changes. */
    val localeChanges: Flow<String>
        get() = flowOf("")

    fun typePresentation(type: CareTaskType): CareTaskTypePresentation

    fun automaticTaskPresentation(
        task: CareTaskSnapshot,
        tank: AquariumTankSnapshot?
    ): CareTaskTextPresentation? = null

    fun waterChangeTitle(typeTitle: String, percent: Int): String

    fun sourceLabel(source: CareTaskSource): String

    fun formatTime(timeMillis: Long): String

    fun completedStatus(): String

    fun completedTime(timeText: String): String

    fun repeatTime(timeText: String, repeatDays: Int): String

    fun reminderWithMissedDays(days: Int): String

    fun reminderActive(): String

    fun overdue(): String

    fun today(): String

    fun tomorrow(): String

    fun daysLater(days: Long): String

    fun oneDayAgo(): String

    fun daysAgo(days: Long): String

    fun unknownAquarium(): String
}

data class CareTaskTextPresentation(
    val title: String,
    val description: String
)

data class CareTaskTypePresentation(
    val title: String,
    val defaultDescription: String,
    val iconRes: Int,
    @ColorInt val accentColor: Int
)
