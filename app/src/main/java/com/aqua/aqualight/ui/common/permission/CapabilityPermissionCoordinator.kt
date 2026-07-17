package com.aqua.aqualight.ui.common.permission

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.platform.permissions.PermissionDecision
import com.aqua.aqualight.platform.permissions.PermissionPolicy

/**
 * Fragment-scoped coordinator for permission requests, rationale/settings UI, and
 * settings-return continuation.
 *
 * Activity Result launchers remain owned by the lifecycle Fragment. Pending work is
 * represented by a stable string token and saved through SavedStateRegistry, so no
 * lambda or Fragment reference must survive process recreation.
 */
class CapabilityPermissionCoordinator(
    private val fragment: Fragment,
    instanceKey: String = DEFAULT_INSTANCE_KEY,
    private val onGranted: (actionToken: String) -> Unit
) : DefaultLifecycleObserver {

    private val stateKey = "capability_permission_state:${fragment::class.java.name}:$instanceKey"
    private val resultRequestKey =
        "capability_permission_result:${fragment::class.java.name}:$instanceKey"
    private val sheetTag =
        "CapabilityPermissionBottomSheet:${fragment::class.java.name}:$instanceKey"

    private var pendingCapability: AppCapability? = null
    private var pendingActionToken: String? = null
    private var waitingForSettings = false
    private var stateProviderRegistered = false

    private val permissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onPermissionResult()
    }

    private val settingsLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onSettingsReturned()
    }

    init {
        fragment.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        restoreState(fragment.savedStateRegistry.consumeRestoredStateForKey(stateKey))
        fragment.savedStateRegistry.registerSavedStateProvider(stateKey, ::saveState)
        stateProviderRegistered = true

        fragment.childFragmentManager.setFragmentResultListener(
            resultRequestKey,
            fragment
        ) { _, bundle ->
            when (bundle.getString(CapabilityPermissionBottomSheet.RESULT_KEY)) {
                CapabilityPermissionBottomSheet.RESULT_ALLOW -> launchPermissionRequest()
                CapabilityPermissionBottomSheet.RESULT_OPEN_SETTINGS -> launchSettings()
                CapabilityPermissionBottomSheet.RESULT_CANCEL -> clearPending()
            }
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        when {
            waitingForSettings -> onSettingsReturned()
            pendingCapability != null && policy().isGranted(requireCapability()) -> complete()
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        if (stateProviderRegistered) {
            fragment.savedStateRegistry.unregisterSavedStateProvider(stateKey)
            stateProviderRegistered = false
        }
        fragment.lifecycle.removeObserver(this)
    }

    fun runWhenGranted(capability: AppCapability, actionToken: String) {
        require(actionToken.isNotBlank()) { "Permission action token must not be blank." }

        pendingCapability = capability
        pendingActionToken = actionToken
        waitingForSettings = false
        dispatchCurrentDecision()
    }

    /**
     * Presents the same central settings explanation for a system-level capability
     * block that is not represented by a missing runtime permission (for example,
     * notifications disabled at Android app level).
     */
    fun openSettingsFor(capability: AppCapability, actionToken: String) {
        require(actionToken.isNotBlank()) { "Permission action token must not be blank." }

        pendingCapability = capability
        pendingActionToken = actionToken
        waitingForSettings = false
        showSheet(CapabilityPermissionBottomSheet.Mode.OPEN_SETTINGS)
    }

    fun isGranted(capability: AppCapability): Boolean = policy().isGranted(capability)

    fun cancelPending() {
        clearPending()
    }

    private fun dispatchCurrentDecision() {
        val capability = pendingCapability ?: return

        when (
            policy().evaluate(
                capability = capability,
                shouldShowRationale = fragment::shouldShowRequestPermissionRationale
            )
        ) {
            PermissionDecision.GRANTED -> complete()
            PermissionDecision.REQUEST -> launchPermissionRequest()
            PermissionDecision.RATIONALE -> showSheet(
                CapabilityPermissionBottomSheet.Mode.RATIONALE
            )
            PermissionDecision.OPEN_SETTINGS -> showSheet(
                CapabilityPermissionBottomSheet.Mode.OPEN_SETTINGS
            )
        }
    }

    private fun launchPermissionRequest() {
        val capability = pendingCapability ?: return
        val policy = policy()
        val permissions = policy.requiredPermissions(capability)

        if (permissions.isEmpty()) {
            complete()
            return
        }

        policy.markRequested(capability)
        runCatching {
            permissionLauncher.launch(permissions)
        }.onFailure {
            clearPending()
        }
    }

    private fun onPermissionResult() {
        val capability = pendingCapability ?: return
        if (policy().isGranted(capability)) {
            complete()
        } else {
            dispatchCurrentDecision()
        }
    }

    private fun showSheet(mode: CapabilityPermissionBottomSheet.Mode) {
        val capability = pendingCapability ?: return
        val manager = fragment.childFragmentManager
        if (manager.isStateSaved || manager.findFragmentByTag(sheetTag) != null) return

        CapabilityPermissionBottomSheet.newInstance(
            capability = capability,
            mode = mode,
            requestKey = resultRequestKey
        ).show(manager, sheetTag)
    }

    private fun launchSettings() {
        val capability = pendingCapability ?: return
        waitingForSettings = true

        val primaryIntent = settingsIntent(capability)
        runCatching {
            settingsLauncher.launch(primaryIntent)
        }.recoverCatching {
            settingsLauncher.launch(Intent(Settings.ACTION_SETTINGS))
        }.onFailure {
            waitingForSettings = false
            clearPending()
        }
    }

    private fun onSettingsReturned() {
        if (!waitingForSettings) return
        waitingForSettings = false

        val capability = pendingCapability ?: return
        if (policy().isGranted(capability)) {
            complete()
        } else {
            clearPending()
        }
    }

    private fun settingsIntent(capability: AppCapability): Intent {
        val context = fragment.requireContext()
        return if (
            capability == AppCapability.NOTIFICATIONS &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        }
    }

    private fun complete() {
        val action = pendingActionToken ?: return
        clearPending()
        onGranted(action)
    }

    private fun clearPending() {
        pendingCapability = null
        pendingActionToken = null
        waitingForSettings = false
    }

    private fun requireCapability(): AppCapability {
        return checkNotNull(pendingCapability) { "No pending permission capability." }
    }

    private fun policy(): PermissionPolicy {
        return PermissionPolicy(fragment.requireContext())
    }

    private fun saveState(): Bundle {
        return bundleOf(
            STATE_CAPABILITY to pendingCapability?.name,
            STATE_ACTION to pendingActionToken,
            STATE_WAITING_FOR_SETTINGS to waitingForSettings
        )
    }

    private fun restoreState(bundle: Bundle?) {
        pendingCapability = bundle
            ?.getString(STATE_CAPABILITY)
            ?.let { name -> runCatching { AppCapability.valueOf(name) }.getOrNull() }
        pendingActionToken = bundle?.getString(STATE_ACTION)
        waitingForSettings = bundle?.getBoolean(STATE_WAITING_FOR_SETTINGS) == true

        if (pendingCapability == null || pendingActionToken.isNullOrBlank()) {
            clearPending()
        }
    }

    private companion object {
        const val DEFAULT_INSTANCE_KEY = "default"
        const val STATE_CAPABILITY = "capability"
        const val STATE_ACTION = "action"
        const val STATE_WAITING_FOR_SETTINGS = "waiting_for_settings"
    }
}
