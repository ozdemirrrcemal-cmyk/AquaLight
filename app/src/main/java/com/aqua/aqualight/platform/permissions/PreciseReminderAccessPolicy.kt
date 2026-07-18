package com.aqua.aqualight.platform.permissions

import android.app.AlarmManager
import android.content.Context
import android.os.Build

/**
 * Single Android boundary for the Alarms & reminders special app access.
 *
 * Android 12+ requires user-granted SCHEDULE_EXACT_ALARM access before a persisted
 * PendingIntent alarm can be scheduled exactly. Older supported API levels do not
 * require special access.
 */
class PreciseReminderAccessPolicy(
    context: Context
) {
    private val alarmManager = context.applicationContext
        .getSystemService(AlarmManager::class.java)

    fun isGranted(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager?.canScheduleExactAlarms() == true
    }

    companion object {
        internal fun requiresSpecialAccess(sdkInt: Int): Boolean {
            return sdkInt >= Build.VERSION_CODES.S
        }
    }
}
