package com.aqua.aqualight.data.care.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aqua.aqualight.data.auth.FirebaseAuthenticatedOwnerProvider
import com.aqua.aqualight.platform.permissions.PreciseReminderAccessPolicy

/** Enqueues owner reminder restoration after reboot, app replacement or timing access grant. */
class CareTaskBootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val action = intent.action
        if (!CareTaskBootIntentVerifier.isVerified(intent, action)) {
            return
        }

        if (
            action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED &&
            !PreciseReminderAccessPolicy(context).isGranted()
        ) {
            return
        }

        val ownerUid = FirebaseAuthenticatedOwnerProvider.create(
            context.applicationContext
        ).currentOwnerUid().orEmpty().trim()

        if (ownerUid.isBlank()) {
            return
        }

        CareReminderReconcileWorker.enqueue(
            context = context.applicationContext,
            ownerUid = ownerUid
        )
    }
}
