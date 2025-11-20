// com/aqua/aqualight/utils/LoginAlerts.kt
package com.aqua.aqualight.utils

import android.content.Context
import com.aqua.aqualight.R
import com.aqua.aqualight.data.UserPreferencesManager
import kotlinx.coroutines.flow.first

suspend fun maybeShowLoginAlert(context: Context) {
    val prefsManager = UserPreferencesManager.create(context)
    val prefs = prefsManager.userPrefsFlow.first()

    // Hem genel bildirim izni (notificationsEnabled)
    // hem de loginAlertsEnabled açık olmalı
    if (prefs.notificationsEnabled && prefs.loginAlertsEnabled) {
        NotificationHelper.showLocalNotification(
            context,
            context.getString(R.string.login_alert_title),
            context.getString(R.string.login_alert_message)
        )
    }
}