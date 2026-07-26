package com.aqua.aqualight.data.care.reminder;

import android.app.AlarmManager;
import android.content.Intent;

/** Strict allowlist for system broadcasts that may trigger care-reminder reconciliation. */
final class CareTaskSystemIntentVerifier {

    private CareTaskSystemIntentVerifier() {
        // Utility class.
    }

    static boolean hasAllowedAction(Intent intent) {
        final String action = intent.getAction();
        return Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action);
    }
}
