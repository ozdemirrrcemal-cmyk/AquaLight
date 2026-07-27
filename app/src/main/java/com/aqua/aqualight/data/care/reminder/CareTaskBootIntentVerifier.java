package com.aqua.aqualight.data.care.reminder;

import android.app.AlarmManager;
import android.content.Intent;

/**
 * Verifies that a boot-reconciliation broadcast carries an explicitly supported system action.
 *
 * <p>The verification is centralized here so every caller performs the same fail-closed check
 * before touching authenticated owner state or scheduling work.</p>
 */
final class CareTaskBootIntentVerifier {

    private CareTaskBootIntentVerifier() {
        // Utility class.
    }

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
