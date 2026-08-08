package com.aqua.aqualight.debug.devices

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.aqua.aqualight.R
import com.aqua.aqualight.app.AquaApp
import com.aqua.aqualight.ui.splash.SplashActivity

/**
 * Debug-source-set bootstrap for the Installable Debug APK only.
 *
 * Content providers are initialized before Application.onCreate, so installation is deferred until
 * SplashActivity has been created. AquaApp has completed its normal production bootstrap by then,
 * while MainActivity and owner ViewModels have not yet been created.
 */
class InstallableDebugFixtureBootstrapProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val providerContext = context
        val enabled = providerContext?.resources?.getBoolean(
            R.bool.installable_debug_device_fixture_enabled
        ) == true
        val application = providerContext?.applicationContext as? AquaApp

        if (enabled && application != null) {
            application.registerActivityLifecycleCallbacks(
                FixtureCompositionInstaller(application)
            )
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}

private class FixtureCompositionInstaller(
    private val application: AquaApp
) : Application.ActivityLifecycleCallbacks {

    private var installed = false

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (installed || activity !is SplashActivity) return

        application.replaceAppContainerForProcess(
            DebugDeviceFixtureAppContainer(
                context = activity.applicationContext,
                delegate = application.appContainer
            )
        )
        installed = true
        application.unregisterActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
