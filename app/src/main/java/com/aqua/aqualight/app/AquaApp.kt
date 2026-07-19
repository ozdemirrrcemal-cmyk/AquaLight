package com.aqua.aqualight.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.aqua.aqualight.base.theme.AppThemeController
import com.aqua.aqualight.composition.AppContainer
import com.aqua.aqualight.composition.DefaultAppContainer
import com.aqua.aqualight.data.media.AppMediaRecoveryManager
import com.aqua.aqualight.data.notifications.NotificationPlatform
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.localization.SupportedLocaleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AquaApp : Application() {

    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        appContainer = DefaultAppContainer(this)
        LocalDataRecoveryTracker.initialize(this)

        val appearanceCache = appContainer.startupAppearanceCache
        val cachedAppearance = appearanceCache.read()

        // The first frame uses a tiny SharedPreferences mirror. Encrypted Proto
        // DataStore is reconciled asynchronously and never blocks app startup.
        applyTheme(cachedAppearance.themeMode)
        applyLanguage(cachedAppearance.languageCode)

        val userPrefs = appContainer.userPreferencesManager
        applicationScope.launch {
            val preferences = userPrefs.userPrefsFlow.first()
            val resolvedThemeMode = preferences.themeMode.ifBlank {
                UserPreferencesManager.DEFAULT_THEME_MODE
            }
            val resolvedLanguageCode = SupportedLocaleRegistry.normalize(
                preferences.languageCode.ifBlank {
                    UserPreferencesManager.DEFAULT_LANGUAGE_CODE
                }
            )

            appearanceCache.write(
                themeMode = resolvedThemeMode,
                languageCode = resolvedLanguageCode
            )

            if (
                cachedAppearance.themeMode != resolvedThemeMode ||
                SupportedLocaleRegistry.normalize(cachedAppearance.languageCode) != resolvedLanguageCode
            ) {
                withContext(Dispatchers.Main.immediate) {
                    applyTheme(resolvedThemeMode)
                    applyLanguage(resolvedLanguageCode)
                }
            }
        }

        // Local media reconciliation belongs to process startup. It preserves candidates already
        // referenced by the active owner's durable stores and expires only unreferenced candidates.
        applicationScope.launch {
            runCatching {
                AppMediaRecoveryManager(this@AquaApp).reconcileActiveOwner()
            }
        }

        // Recovery belongs to process startup rather than the Feedback screen. The repository
        // performs server verification and IO dispatching internally; unavailable networking is
        // fail-safe and leaves the durable journal for the next process start.
        applicationScope.launch {
            runCatching {
                appContainer.feedbackSubmissionOperations.cleanupOrphans()
            }
        }

        // Channel creation is idempotent and preserves every user-controlled setting.
        NotificationPlatform.get(this).permissionPolicy.ensureChannels()
    }

    /**
     * Process-local composition replacement used by the minified release-smoke build.
     * The release-smoke source set is not packaged in the production Release APK, and
     * R8 removes this method from production when there is no caller.
     */
    internal fun replaceAppContainerForProcess(container: AppContainer) {
        appContainer = container
    }

    private fun applyTheme(mode: String) {
        AppThemeController.apply(
            context = this,
            mode = mode
        )
    }

    private fun applyLanguage(code: String) {
        val safeCode = SupportedLocaleRegistry.normalize(code)
        val localeList = LocaleListCompat.forLanguageTags(safeCode)
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
