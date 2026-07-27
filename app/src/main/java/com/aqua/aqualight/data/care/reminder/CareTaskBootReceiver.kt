package com.aqua.aqualight.data.care.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aqua.aqualight.data.auth.FirebaseAuthenticatedOwnerProvider
import com.aqua.aqualight.platform.permissions.PreciseReminderAccessPolicy

/** Enqueues owner reminder restoration after reboot, app replacement or timing access grant. */
class CareTaskBootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val action = intent.action
        val isSupportedAction = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> true

            else -> false
        }
        val requiresTimingAccess =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        val hasRequiredTimingAccess =
            !requiresTimingAccess || PreciseReminderAccessPolicy(context).isGranted()

        if (
            isSupportedAction &&
            CareTaskBootIntentVerifier.matchesObservedAction(intent, action) &&
            hasRequiredTimingAccess
        ) {
            val ownerUid = FirebaseAuthenticatedOwnerProvider.create(
                context.applicationContext
            ).currentOwnerUid().orEmpty().trim()

            if (ownerUid.isNotBlank()) {
                CareReminderReconcileWorker.enqueue(
                    context = context.applicationContext,
                    ownerUid = ownerUid
                )
            }
        }
    }
}
