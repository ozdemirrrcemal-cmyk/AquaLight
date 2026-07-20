package com.aqua.aqualight.data.care.reminder

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.aquarium.toApplicationSnapshot as toAquariumSnapshot
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.toApplicationSnapshot as toCareSnapshot
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.platform.text.AndroidMaintenanceTextResolver

/** Final notification copy resolved at delivery time from the active application locale. */
internal data class CareReminderText(
    val title: String,
    val message: String
)

/**
 * Applies the same text-ownership contract used by maintenance cards and task details.
 *
 * Application-owned standard and Smart Care copy is localized at delivery time. User-owned custom
 * titles, notes and aquarium names are preserved verbatim and are never translated or overwritten.
 */
internal class CareReminderTextResolver(
    context: Context,
    private val localizedContextProvider: ((Context) -> Context)? = null
) {
    private val appContext = context.applicationContext
    private val maintenanceTextResolver = AndroidMaintenanceTextResolver(
        context = appContext,
        localizedContextProvider = localizedContextProvider
    )

    fun resolve(
        task: CareTask,
        tank: SavedAquariumTank?
    ): CareReminderText {
        val localizedContext = localizedContextProvider?.invoke(appContext)
            ?: LocaleFormatter.localizedContext(appContext)
        val taskPresentation = maintenanceTextResolver.taskPresentation(
            task = task.toCareSnapshot(),
            tank = tank?.toAquariumSnapshot()
        )
        val bodyText = task.note
            .takeIf(String::isNotBlank)
            ?: taskPresentation.description.takeIf(String::isNotBlank)
            ?: localizedContext.getString(R.string.maintenance_notification_due_now)
        val message = tank?.name
            ?.takeIf(String::isNotBlank)
            ?.let { aquariumName ->
                localizedContext.getString(
                    R.string.maintenance_notification_message_with_aquarium,
                    aquariumName,
                    bodyText
                )
            }
            ?: bodyText

        return CareReminderText(
            title = taskPresentation.title,
            message = message
        )
    }
}
