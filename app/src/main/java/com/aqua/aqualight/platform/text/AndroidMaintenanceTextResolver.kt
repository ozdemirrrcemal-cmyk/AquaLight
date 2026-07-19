package com.aqua.aqualight.platform.text

import android.content.Context
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.aqua.aqualight.application.care.CareTaskSource
import com.aqua.aqualight.application.care.CareTaskType
import com.aqua.aqualight.localization.LocaleFormatters
import com.aqua.aqualight.ui.tabs.maintenance.text.CareTaskTypeCatalog
import com.aqua.aqualight.ui.tabs.maintenance.text.CareTaskTypePresentation
import com.aqua.aqualight.ui.tabs.maintenance.text.MaintenanceTextResolver

class AndroidMaintenanceTextResolver(
    context: Context
) : MaintenanceTextResolver {

    private val appContext = context.applicationContext

    override fun typePresentation(type: CareTaskType): CareTaskTypePresentation {
        val definition = CareTaskTypeCatalog.get(type)
        return CareTaskTypePresentation(
            title = definition.title(appContext),
            defaultDescription = definition.defaultDescription(appContext),
            iconRes = definition.iconRes,
            accentColor = ContextCompat.getColor(appContext, definition.accentColorRes)
        )
    }

    override fun waterChangeTitle(typeTitle: String, percent: Int): String =
        appContext.getString(
            R.string.maintenance_task_title_with_percent,
            typeTitle,
            percent
        )

    override fun sourceLabel(source: CareTaskSource): String = when (source) {
        CareTaskSource.MANUAL -> appContext.getString(R.string.maintenance_manual_source)
        CareTaskSource.AUTOMATIC -> appContext.getString(R.string.maintenance_smart_source)
    }

    override fun completedStatus(): String =
        appContext.getString(R.string.maintenance_status_completed)

    override fun completedTime(timeText: String): String =
        appContext.getString(R.string.maintenance_completed_time, timeText)

    override fun repeatTime(timeText: String, repeatDays: Int): String =
        appContext.getString(
            R.string.maintenance_time_repeat_days,
            timeText,
            repeatDays
        )

    override fun reminderWithMissedDays(days: Int): String =
        appContext.getString(
            R.string.maintenance_reminder_active_missed_days,
            days
        )

    override fun reminderActive(): String =
        appContext.getString(R.string.maintenance_reminder_active)

    override fun overdue(): String = appContext.getString(R.string.maintenance_overdue)

    override fun today(): String = appContext.getString(R.string.maintenance_today)

    override fun tomorrow(): String = appContext.getString(R.string.maintenance_tomorrow)

    override fun daysLater(days: Long): String =
        appContext.getString(R.string.maintenance_days_later, days)

    override fun oneDayAgo(): String =
        appContext.getString(R.string.maintenance_one_day_ago)

    override fun daysAgo(days: Long): String =
        appContext.getString(R.string.maintenance_days_ago, days)

    override fun unknownAquarium(): String =
        appContext.getString(R.string.maintenance_unknown_aquarium)

    override fun formatTime(millis: Long): String =
        LocaleFormatters.formatPattern(
            context = appContext,
            millis = millis,
            pattern = "HH:mm"
        )
}
