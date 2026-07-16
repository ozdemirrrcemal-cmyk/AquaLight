package com.aqua.aqualight.ui.tabs.maintenance.text

import com.aqua.aqualight.application.care.CareTaskSource
import com.aqua.aqualight.application.care.CareTaskType

/** Presentation-only text/icon boundary for maintenance screens. */
interface MaintenanceTextResolver {
    fun typePresentation(type: CareTaskType): CareTaskTypePresentation

    fun waterChangeTitle(typeTitle: String, percent: Int): String

    fun sourceLabel(source: CareTaskSource): String

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

data class CareTaskTypePresentation(
    val title: String,
    val defaultDescription: String,
    val iconRes: Int,
    val accentColor: String
)
