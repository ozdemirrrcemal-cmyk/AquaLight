package com.aqua.aqualight.app

import android.app.Application
import android.content.Context
import android.os.Build
import com.aqua.aqualight.base.accessibility.AccessibilityRuntimeInstaller
import com.aqua.aqualight.base.theme.AppThemeController
import com.aqua.aqualight.composition.AppContainer
import com.aqua.aqualight.composition.DefaultAppContainer
import com.aqua.aqualight.data.media.AppMediaRecoveryManager
import com.aqua.aqualight.data.notifications.NotificationPlatform
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.user.StartupAppearanceCache
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.i18n.AppLanguageController
import com.aqua.aqualight.i18n.SupportedLocaleRegistry
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

    override fun attachBaseContext(base: Context) {
        // Attach the framework ContextImpl first. On older Android versions the
        // Application's applicationContext is not guaranteed to exist before this call.
        super.attachBaseContext(base)

        // Before API 33 AppCompat has no framework-managed per-app locale storage. The startup
        // mirror is applied before any Activity is created so the first frame uses the right locale.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val cachedLanguage = StartupAppearanceCache.create(this)
                .read()
                .languageCode
            AppLanguageController.apply(
                SupportedLocaleRegistry.resolve(cachedLanguage)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(
            AccessibilityRuntimeInstaller()
        )

        appContainer = DefaultAppContainer(this)
        LocalDataRecoveryTracker.initialize(this)

        val appearanceCache = appContainer.startupAppearanceCache
        val cachedAppearance = appearanceCache.read()

        applyTheme(cachedAppearance.themeMode)

        // Android/AppCompat is the language source of truth. When no explicit app locale exists,
        // initialize the supported device default and then mirror that effective language locally.
        val startupLanguage = AppLanguageController.current()
        if (AppLanguageController.currentOrNull() == null) {
            AppLanguageController.apply(startupLanguage)
        }
        appearanceCache.write(
            themeMode = cachedAppearance.themeMode,
            languageCode = startupLanguage
        )

        val userPrefs = appContainer.userPreferencesManager
        applicationScope.launch {
            val preferences = userPrefs.userPrefsFlow.first()
            val resolvedThemeMode = preferences.themeMode.ifBlank {
                UserPreferencesManager.DEFAULT_THEME_MODE
            }
            val effectiveLanguage = AppLanguageController.current()

            // A previous installation can leave a valid but stale preference value. Never let that
            // mirror disagree with the locale that is actually rendering the application.
            if (preferences.languageCode != effectiveLanguage) {
                userPrefs.updateLanguage(effectiveLanguage)
            }

            appearanceCache.write(
                themeMode = resolvedThemeMode,
                languageCode = effectiveLanguage
            )

            if (cachedAppearance.themeMode != resolvedThemeMode) {
                withContext(Dispatchers.Main.immediate) {
                    applyTheme(resolvedThemeMode)
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
}
