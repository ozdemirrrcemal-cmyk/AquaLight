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
        // Keep this direct call in the receiver so the system-intent trust boundary remains explicit.
        val action = intent.getAction()
        val isSupportedAction = when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> true

            else -> false
        }
        val hasRequiredTimingAccess = when {
            action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> true
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> true
            else -> PreciseReminderAccessPolicy(context).isGranted()
        }

        if (!isSupportedAction || !hasRequiredTimingAccess) {
            return
        }

        val appContext = context.applicationContext
        val ownerUid = FirebaseAuthenticatedOwnerProvider.create(appContext)
            .currentOwnerUid()
            .orEmpty()
            .trim()

        if (ownerUid.isBlank()) {
            return
        }

        CareReminderReconcileWorker.enqueue(
            context = appContext,
            ownerUid = ownerUid
        )
    }
}
