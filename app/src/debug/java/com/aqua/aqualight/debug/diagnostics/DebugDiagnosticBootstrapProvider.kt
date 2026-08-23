package com.aqua.aqualight.debug.diagnostics

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.base.diagnostics.AppDiagnosticTrace
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Installs the process-local trace recorder for debug builds before the first Activity. */
class DebugDiagnosticBootstrapProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val application = context?.applicationContext as? Application ?: return true
        val recorder = DebugDiagnosticRecorder()
        AppDiagnosticTrace.install(recorder)
        application.registerActivityLifecycleCallbacks(
            DebugDiagnosticActivityInstaller(recorder)
        )
        AppDiagnosticTrace.event(
            category = "diagnostic",
            name = "recorder.installed"
        )
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

private class DebugDiagnosticActivityInstaller(
    private val recorder: DebugDiagnosticRecorder
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        scheduleOverlayInstallation(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        (activity as? BaseActivity)?.let(::ensureOverlay)
    }

    private fun scheduleOverlayInstallation(activity: Activity) {
        val baseActivity = activity as? BaseActivity ?: return
        baseActivity.window.decorView.post {
            ensureOverlay(baseActivity)
        }
    }

    private fun ensureOverlay(activity: BaseActivity) {
        if (activity.isFinishing || activity.isDestroyed) return

        val windowHost = activity.window.decorView as? FrameLayout ?: return
        val existing = windowHost.findViewWithTag<DebugDiagnosticOverlayView>(OVERLAY_TAG)
        if (existing != null) {
            applyCurrentTopInset(windowHost, existing)
            existing.bringToFront()
            ViewCompat.requestApplyInsets(existing)
            return
        }
        installOverlay(activity, windowHost)
    }

    private fun installOverlay(activity: BaseActivity, windowHost: FrameLayout) {
        val overlay = DebugDiagnosticOverlayView(activity)
        windowHost.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        applyStatusBarInset(windowHost, overlay)
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                recorder.records.collect(overlay::render)
            }
        }
        overlay.bringToFront()
        AppDiagnosticTrace.event(
            category = "lifecycle",
            name = "diagnostic_overlay.attached",
            "activity" to activity.javaClass.simpleName
        )
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun applyStatusBarInset(
        windowHost: FrameLayout,
        overlay: DebugDiagnosticOverlayView
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(overlay) { view, insets ->
            updateTopInset(view, insets)
            insets
        }
        if (!applyCurrentTopInset(windowHost, overlay)) {
            windowHost.post {
                if (overlay.parent === windowHost) {
                    applyCurrentTopInset(windowHost, overlay)
                }
            }
        }
        ViewCompat.requestApplyInsets(overlay)
    }

    private fun applyCurrentTopInset(
        windowHost: FrameLayout,
        overlay: DebugDiagnosticOverlayView
    ): Boolean {
        val insets = ViewCompat.getRootWindowInsets(windowHost) ?: return false
        updateTopInset(overlay, insets)
        return true
    }

    private fun updateTopInset(view: View, insets: WindowInsetsCompat) {
        val topInset = insets.getInsets(
            WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.displayCutout()
        ).top
        val layoutParams = view.layoutParams as FrameLayout.LayoutParams
        if (layoutParams.topMargin != topInset) {
            layoutParams.topMargin = topInset
            view.layoutParams = layoutParams
        }
    }

    private companion object {
        const val OVERLAY_TAG = "aqualight-debug-diagnostic-overlay"
    }
}
