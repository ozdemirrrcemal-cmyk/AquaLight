package com.aqua.aqualight.data.care.reminder;

import android.content.Intent;

/** Verifies that the action observed by the receiver still matches the received Intent. */
final class CareTaskBootIntentVerifier {

    private CareTaskBootIntentVerifier() {
        // Utility class.
    }

    static boolean matchesObservedAction(Intent intent, String observedAction) {
        final String action = intent.getAction();
        return action != null && action.equals(observedAction);
    }
}
