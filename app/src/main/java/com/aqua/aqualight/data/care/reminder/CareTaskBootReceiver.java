package com.aqua.aqualight.data.care.reminder;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.aqua.aqualight.data.auth.FirebaseAuthenticatedOwnerProvider;
import com.aqua.aqualight.platform.permissions.PreciseReminderAccessPolicy;

/** Enqueues owner reminder restoration after reboot, app replacement or timing access grant. */
public final class CareTaskBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        // Keep the system-intent trust boundary in this Java receiver. CodeQL's Android
        // verification query follows the received Intent directly into this getAction call.
        final String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) {
            return;
        }

        if (AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !new PreciseReminderAccessPolicy(context).isGranted()) {
            return;
        }

        final Context appContext = context.getApplicationContext();
        final String currentOwnerUid = FirebaseAuthenticatedOwnerProvider.Companion
                .create(appContext)
                .currentOwnerUid();
        final String ownerUid = currentOwnerUid == null ? "" : currentOwnerUid.trim();
        if (ownerUid.isEmpty()) {
            return;
        }

        CareReminderReconcileWorker.Companion.enqueue(appContext, ownerUid);
    }
}
