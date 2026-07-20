package com.aqua.aqualight.platform.text

import android.content.Context
import androidx.core.content.ContextCompat
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.care.CareTaskSnapshot
import com.aqua.aqualight.application.care.CareTaskSource
import com.aqua.aqualight.application.care.CareTaskType
import com.aqua.aqualight.i18n.AppLanguageController
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.tabs.maintenance.text.CareTaskTextPresentation
import com.aqua.aqualight.ui.tabs.maintenance.text.CareTaskTypeCatalog
import com.aqua.aqualight.ui.tabs.maintenance.text.CareTaskTypePresentation
import com.aqua.aqualight.ui.tabs.maintenance.text.MaintenanceTextResolver
import kotlinx.coroutines.flow.Flow

class AndroidMaintenanceTextResolver(
    context: Context,
    private val localizedContextProvider: ((Context) -> Context)? = null
) : MaintenanceTextResolver {

    private val appContext = context.applicationContext

    override val localeChanges: Flow<String> = AppLanguageController.languageChanges

    override fun typePresentation(type: CareTaskType): CareTaskTypePresentation {
        val context = localizedContext()
        val definition = CareTaskTypeCatalog.get(type)
        return CareTaskTypePresentation(
            title = definition.title(context),
            defaultDescription = definition.defaultDescription(context),
            iconRes = definition.iconRes,
            accentColor = ContextCompat.getColor(appContext, definition.accentColorRes)
        )
    }

    override fun automaticTaskPresentation(
        task: CareTaskSnapshot,
        tank: AquariumTankSnapshot?
    ): CareTaskTextPresentation? {
        return AndroidSmartCareTextResolver(localizedContext()).resolve(task, tank)
    }

    override fun waterChangeTitle(typeTitle: String, percent: Int): String =
        localizedContext().getString(
            R.string.maintenance_task_title_with_percent,
            typeTitle,
            percent
        )

    override fun sourceLabel(source: CareTaskSource): String = when (source) {
        CareTaskSource.MANUAL -> localizedContext().getString(R.string.maintenance_manual_source)
        CareTaskSource.AUTOMATIC -> localizedContext().getString(R.string.maintenance_smart_source)
    }

    override fun formatTime(timeMillis: Long): String =
        LocaleFormatter.formatTime(localizedContext(), timeMillis)

    override fun completedStatus(): String =
        localizedContext().getString(R.string.maintenance_status_completed)

    override fun completedTime(timeText: String): String =
        localizedContext().getString(R.string.maintenance_completed_time, timeText)

    override fun repeatTime(timeText: String, repeatDays: Int): String =
        localizedContext().getString(
            R.string.maintenance_time_repeat_days,
            timeText,
            repeatDays
        )

    override fun reminderWithMissedDays(days: Int): String =
        localizedContext().getString(
            R.string.maintenance_reminder_active_missed_days,
            days
        )

    override fun reminderActive(): String =
        localizedContext().getString(R.string.maintenance_reminder_active)

    override fun overdue(): String = localizedContext().getString(R.string.maintenance_overdue)

    override fun today(): String = localizedContext().getString(R.string.maintenance_today)

    override fun tomorrow(): String = localizedContext().getString(R.string.maintenance_tomorrow)

    override fun daysLater(days: Long): String =
        localizedContext().getString(R.string.maintenance_days_later, days)

    override fun oneDayAgo(): String =
        localizedContext().getString(R.string.maintenance_one_day_ago)

    override fun daysAgo(days: Long): String =
        localizedContext().getString(R.string.maintenance_days_ago, days)

    override fun unknownAquarium(): String =
        localizedContext().getString(R.string.maintenance_unknown_aquarium)

    private fun localizedContext(): Context =
        localizedContextProvider?.invoke(appContext)
            ?: LocaleFormatter.localizedContext(appContext)
}
