package com.aqua.aqualight.app

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import com.aqua.aqualight.base.accessibility.AccessibilityRuntimeInstaller
import com.aqua.aqualight.base.theme.AppThemeController
import com.aqua.aqualight.composition.AppContainer
import com.aqua.aqualight.composition.DefaultAppContainer
import com.aqua.aqualight.data.auth.AccountDeletionManager
import com.aqua.aqualight.data.media.AppMediaRecoveryManager
import com.aqua.aqualight.data.notifications.NotificationPlatform
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.user.StartupAppearanceCache
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.i18n.AppLanguageController
import com.aqua.aqualight.i18n.SupportedLocaleRegistry
import kotlinx.coroutines.CompletableDeferred
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
    private val startupAppearanceSync = CompletableDeferred<Unit>()

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
        }.invokeOnCompletion(::completeStartupAppearanceSync)

        // Local media reconciliation belongs to process startup. It preserves candidates already
        // referenced by the active owner's durable stores and expires only unreferenced candidates.
        applicationScope.launch {
            runCatching {
                AppMediaRecoveryManager(this@AquaApp).reconcileActiveOwner()
            }
        }

        // A confirmed account deletion can outlive the UI process. Resume its durable checkpoint
        // so cloud/auth/local cleanup remains one idempotent commercial transaction.
        applicationScope.launch(Dispatchers.IO) {
            val result = runCatching {
                AccountDeletionManager.create(this@AquaApp)
                    .resumePendingDeletion()
            }.getOrElse { error ->
                Log.e(TAG, "Pending account deletion recovery failed.", error)
                null
            }

            if (result?.accountDeleteError != null || result?.hasPostDeleteCleanupErrors == true) {
                Log.w(TAG, "Pending account deletion recovery remains incomplete.")
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

    /**
     * Release-smoke barrier for inspecting the post-bootstrap appearance state.
     *
     * The release-smoke source set is absent from production Release APKs, so its sole caller and
     * this method are removed by release shrinking.
     */
    internal suspend fun awaitStartupAppearanceSyncForProcess() {
        startupAppearanceSync.await()
    }

    private fun completeStartupAppearanceSync(error: Throwable?) {
        if (error == null) {
            startupAppearanceSync.complete(Unit)
        } else {
            startupAppearanceSync.completeExceptionally(error)
        }
    }

    private fun applyTheme(mode: String) {
        AppThemeController.apply(
            context = this,
            mode = mode
        )
    }

    private companion object {
        const val TAG = "AquaApp"
    }
}
