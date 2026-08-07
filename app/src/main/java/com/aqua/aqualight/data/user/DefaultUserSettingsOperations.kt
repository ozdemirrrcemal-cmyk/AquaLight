package com.aqua.aqualight.data.user

import com.aqua.aqualight.application.user.UsageAnalyticsSnapshot
import com.aqua.aqualight.application.user.UserSettingsOperations
import com.aqua.aqualight.i18n.AppLanguageController
import com.aqua.aqualight.i18n.SupportedLocaleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Existing non-notification settings behavior wired behind the application contract. */
class DefaultUserSettingsOperations(
    private val preferences: UserPreferencesManager,
    private val startupAppearanceCache: StartupAppearanceCache,
    private val reconcileDeviceUpdateWork: suspend () -> Unit = {}
) : UserSettingsOperations {

    override val themeMode: Flow<String> = preferences.themeMode
    override val languageCode: Flow<String> = preferences.languageCode
        .map { AppLanguageController.current() }
        .distinctUntilChanged()
    override val autoUpdateEnabled: Flow<Boolean> = preferences.autoUpdateEnabled
    override val usageAnalytics: Flow<UsageAnalyticsSnapshot> =
        preferences.usageAnalyticsFlow.map { usage ->
            UsageAnalyticsSnapshot(
                weeklyAutomationCount = usage.weeklyAutomationCount,
                weeklyAlertCount = usage.weeklyAlertCount,
                todayAutomationCount = usage.todayAutomationCount,
                todayManualActionCount = usage.todayManualActionCount,
                lastEventTimeMillis = usage.lastEventTimeMillis,
                lastEventDescription = usage.lastEventDescription
            )
        }

    override suspend fun updateThemeMode(mode: String) {
        preferences.updateThemeMode(mode)
        startupAppearanceCache.writeThemeMode(mode)
    }

    override suspend fun updateLanguage(code: String) {
        val supportedCode = requireNotNull(
            SupportedLocaleRegistry.supportedCanonicalOrNull(code)
        ) {
            "Selected language must be an explicit supported locale."
        }

        preferences.updateLanguage(supportedCode)
        startupAppearanceCache.writeLanguageCode(supportedCode)
        withContext(Dispatchers.Main.immediate) {
            AppLanguageController.apply(supportedCode)
        }
    }

    override suspend fun updateAutoUpdateEnabled(enabled: Boolean) {
        preferences.updateAutoUpdateEnabled(enabled)
        reconcileDeviceUpdateWork()
    }
}
