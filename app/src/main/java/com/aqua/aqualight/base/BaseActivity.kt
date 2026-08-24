package com.aqua.aqualight.base

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.aqua.aqualight.R
import com.aqua.aqualight.base.loading.LoadingOverlayDialogFragment
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.debug.dosing.DosingDebugOverlayView
import com.aqua.aqualight.debug.dosing.DosingDebugTrace
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

open class BaseActivity : AppCompatActivity() {

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = requireAppContainer().defaultViewModelFactory

    enum class SnackType {
        NORMAL,
        SUCCESS,
        ERROR,
        WARNING
    }

    private val activityJob = SupervisorJob()

    protected val uiScope: CoroutineScope = CoroutineScope(
        Dispatchers.Main.immediate + activityJob
    )

    private val loadingOwners: MutableSet<String> = linkedSetOf()
    private var dosingDebugOverlay: DosingDebugOverlayView? = null
    private var dosingDebugTraceJob: Job? = null

    private val legacyLoadingOwner =
        "${BaseActivity::class.java.name}.LegacyLoadingOwner"

    override fun onCreate(savedInstanceState: Bundle?) {
        val stateFromCurrentProcess = savedInstanceState?.takeIf { state ->
            ProcessUiStateRestorePolicy.canRestore(
                savedProcessToken = state.getString(
                    ProcessUiStateRestorePolicy.STATE_PROCESS_TOKEN
                ),
                currentProcessToken = AppProcessIdentity.token
            )
        }

        // Android can terminate the app process when a runtime permission is revoked.
        // In-memory owner repositories do not survive that event, so an owner Fragment
        // graph saved by the previous process must not be restored before session commit.
        // Configuration changes inside the current process keep their normal restoration.
        super.onCreate(stateFromCurrentProcess)

        stateFromCurrentProcess
            ?.getStringArrayList(STATE_LOADING_OWNERS)
            ?.filter(String::isNotBlank)
            ?.let(loadingOwners::addAll)
    }

    override fun onPostResume() {
        super.onPostResume()
        if (DosingDebugOverlayView.enabled(this) && !isFinishing && !isDestroyed) {
            val root = findViewById<ViewGroup>(android.R.id.content)
            if (root != null) {
                val existing = dosingDebugOverlay
                if (existing?.parent !== root) {
                    (existing?.parent as? ViewGroup)?.removeView(existing)
                    dosingDebugTraceJob?.cancel()
                    val overlay = DosingDebugOverlayView(this)
                    root.addView(
                        overlay,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                    overlay.bringToFront()
                    dosingDebugOverlay = overlay
                    dosingDebugTraceJob = uiScope.launch {
                        DosingDebugTrace.lines.collect(overlay::submit)
                    }
                } else {
                    existing.bringToFront()
                }
            }
        }
        renderGlobalLoading()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(
            ProcessUiStateRestorePolicy.STATE_PROCESS_TOKEN,
            AppProcessIdentity.token
        )
        outState.putStringArrayList(
            STATE_LOADING_OWNERS,
            ArrayList(loadingOwners)
        )
        super.onSaveInstanceState(outState)
    }

    fun setGlobalLoading(
        owner: Any,
        show: Boolean
    ) {
        setGlobalLoading(
            ownerKey = owner.toLoadingOwnerKey(),
            show = show
        )
    }

    fun setGlobalLoading(
        ownerKey: String,
        show: Boolean
    ) {
        val normalizedOwnerKey = ownerKey.trim().ifBlank {
            legacyLoadingOwner
        }

        val changed = if (show) {
            loadingOwners.add(normalizedOwnerKey)
        } else {
            loadingOwners.remove(normalizedOwnerKey)
        }

        if (changed) renderGlobalLoading()
    }

    fun clearGlobalLoading(owner: Any) {
        clearGlobalLoading(ownerKey = owner.toLoadingOwnerKey())
    }

    fun clearGlobalLoading(ownerKey: String) {
        setGlobalLoading(
            ownerKey = ownerKey,
            show = false
        )
    }

    fun showLoading(show: Boolean) {
        setGlobalLoading(
            ownerKey = legacyLoadingOwner,
            show = show
        )
    }

    fun showDeviceOfflineDialog(
        deviceTitle: String,
        @StringRes messageRes: Int = R.string.device_menu_offline_message
    ) {
        if (isFinishing || isDestroyed) return

        val safeTitle = deviceTitle.trim().ifBlank {
            getString(R.string.device_menu_default_title)
        }
        val safeMessage = getString(messageRes).trim().ifBlank {
            getString(R.string.device_menu_offline_message)
        }

        DialogManager.showInfoDialog(
            context = this,
            type = DialogType.WARNING,
            title = getString(R.string.device_menu_offline_dialog_title),
            message = getString(
                R.string.device_menu_offline_dialog_message,
                safeTitle,
                safeMessage
            ),
            buttonTextResId = android.R.string.ok
        )
    }

    private fun Any.toLoadingOwnerKey(): String {
        return "${this::class.java.name}@${System.identityHashCode(this)}"
    }

    private fun renderGlobalLoading() {
        if (isFinishing || isDestroyed) return
        if (loadingOwners.isNotEmpty()) {
            LoadingOverlayDialogFragment.show(supportFragmentManager)
        } else {
            LoadingOverlayDialogFragment.hide(supportFragmentManager)
        }
    }

    fun showSnackBar(
        message: String,
        type: SnackType = SnackType.NORMAL
    ) {
        val root = findViewById<View>(android.R.id.content) ?: return
        val snackbar = Snackbar.make(
            root,
            message,
            Snackbar.LENGTH_LONG
        )

        val backgroundColor = when (type) {
            SnackType.SUCCESS -> ContextCompat.getColor(this, R.color.snackbar_success)
            SnackType.ERROR -> ContextCompat.getColor(this, R.color.snackbar_error)
            SnackType.WARNING -> ContextCompat.getColor(this, R.color.snackbar_warning)
            SnackType.NORMAL -> ContextCompat.getColor(this, R.color.aqua_button_blue)
        }

        snackbar.setBackgroundTint(backgroundColor)
        snackbar.setTextColor(
            ContextCompat.getColor(this, android.R.color.white)
        )
        snackbar.animationMode = Snackbar.ANIMATION_MODE_FADE

        val snackView = snackbar.view
        val textView = snackView.findViewById<TextView>(
            com.google.android.material.R.id.snackbar_text
        )
        textView.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(R.dimen.feedback_snackbar_text_size)
        )
        textView.maxLines = 2
        textView.setPadding(0, 0, 0, 0)

        val params = snackView.layoutParams
        if (params is ViewGroup.MarginLayoutParams) {
            val horizontalMargin = resources.getDimensionPixelSize(
                R.dimen.feedback_snackbar_horizontal_margin
            )
            params.setMargins(
                horizontalMargin,
                0,
                horizontalMargin,
                resources.getDimensionPixelSize(R.dimen.feedback_snackbar_bottom_margin)
            )
            snackView.layoutParams = params
        }

        snackView.elevation = resources.getDimension(R.dimen.feedback_snackbar_elevation)
        snackView.background = ContextCompat.getDrawable(
            this,
            R.drawable.bg_snackbar
        )
        snackbar.show()
    }

    protected fun logError(
        tag: String,
        message: String?,
        throwable: Throwable? = null
    ) {
        android.util.Log.e(
            tag,
            message,
            throwable
        )
    }

    override fun onDestroy() {
        dosingDebugTraceJob?.cancel()
        dosingDebugTraceJob = null
        (dosingDebugOverlay?.parent as? ViewGroup)?.removeView(dosingDebugOverlay)
        dosingDebugOverlay = null
        activityJob.cancel()
        if (isFinishing) loadingOwners.clear()
        super.onDestroy()
    }

    private companion object {
        const val STATE_LOADING_OWNERS = "base_activity_loading_owners"
    }
}
