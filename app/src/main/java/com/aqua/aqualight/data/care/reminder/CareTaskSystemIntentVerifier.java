package com.aqua.aqualight.data.care.reminder;

import android.app.AlarmManager;
import android.content.Intent;

import androidx.annotation.Nullable;

/** Strict allowlist for system broadcasts that may trigger care-reminder reconciliation. */
final class CareTaskSystemIntentVerifier {

    private CareTaskSystemIntentVerifier() {
        // Utility class.
    }

    @Nullable
    static String verifiedAction(Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) {
            return action;
        }
        return null;
    }
}
