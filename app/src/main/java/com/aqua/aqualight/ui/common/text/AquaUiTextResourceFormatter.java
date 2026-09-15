package com.aqua.aqualight.ui.common.text;

import android.content.Context;

/** Calls Android's Java vararg resource APIs without a Kotlin spread-array copy. */
final class AquaUiTextResourceFormatter {

    private AquaUiTextResourceFormatter() {
    }

    static String formatString(Context context, int resourceId, Object[] arguments) {
        return context.getString(resourceId, arguments);
    }

    static String formatPlural(
            Context context,
            int resourceId,
            int quantity,
            Object[] arguments
    ) {
        return context.getResources().getQuantityString(resourceId, quantity, arguments);
    }
}
