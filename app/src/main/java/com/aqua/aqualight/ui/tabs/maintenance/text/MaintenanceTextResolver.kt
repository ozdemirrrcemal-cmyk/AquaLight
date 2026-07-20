package com.aqua.aqualight.ui.tabs.maintenance.text

import androidx.annotation.ColorInt
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.care.CareTaskSnapshot
import com.aqua.aqualight.application.care.CareTaskSource
import com.aqua.aqualight.application.care.CareTaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Presentation-only text/icon boundary for maintenance screens and reminder delivery. */
interface MaintenanceTextResolver {
    /** Re-emits presentation state whenever the active application language changes. */
    val localeChanges: Flow<String>
        get() = flowOf("")

    fun typePresentation(type: CareTaskType): CareTaskTypePresentation

    fun automaticTaskPresentation(
        task: CareTaskSnapshot,
        tank: AquariumTankSnapshot?
    ): CareTaskTextPresentation? = null

    /**
     * Resolves the application-owned title and description from semantic task data.
     *
     * Standard manual copy and Smart Care copy follow the active app locale. Custom manual titles
     * remain user-owned. Persisted automatic strings are used only as a defensive fallback when a
     * semantic Smart Care rule cannot be resolved.
     */
    fun taskPresentation(
        task: CareTaskSnapshot,
        tank: AquariumTankSnapshot?
    ): CareTaskTextPresentation {
        val typePresentation = typePresentation(task.type)
        val automaticPresentation = if (task.source == CareTaskSource.AUTOMATIC) {
            automaticTaskPresentation(task, tank)
        } else {
            null
        }

        val title = when {
            task.source == CareTaskSource.AUTOMATIC -> {
                automaticPresentation?.title?.takeIf(String::isNotBlank)
                    ?: task.title.ifBlank { typePresentation.title }
            }
            task.type == CareTaskType.CUSTOM -> {
                task.title.ifBlank { typePresentation.title }
            }
            task.type == CareTaskType.WATER_CHANGE &&
                task.waterChangePercent != null &&
                task.waterChangePercent > 0 -> {
                waterChangeTitle(typePresentation.title, task.waterChangePercent)
            }
            else -> typePresentation.title
        }

        val description = if (task.source == CareTaskSource.AUTOMATIC) {
            automaticPresentation?.description?.takeIf(String::isNotBlank)
                ?: task.description.ifBlank { typePresentation.defaultDescription }
        } else {
            typePresentation.defaultDescription
        }

        return CareTaskTextPresentation(
            title = title,
            description = description
        )
    }

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
