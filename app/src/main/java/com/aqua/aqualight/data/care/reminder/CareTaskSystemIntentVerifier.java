package com.aqua.aqualight.data.care.reminder;

import android.content.Intent;

/** Strict allowlist for system broadcasts that may trigger care-reminder reconciliation. */
final class CareTaskSystemIntentVerifier {

    private static final String EXACT_ALARM_PERMISSION_CHANGED_ACTION =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED";

    private CareTaskSystemIntentVerifier() {
        // Utility class.
    }

    static boolean hasAllowedAction(Intent intent) {
        final String action = intent.getAction();
        return Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || EXACT_ALARM_PERMISSION_CHANGED_ACTION.equals(action);
    }
}
