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

    private val continuation = CapabilityPermissionContinuationState()
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
        val capability = continuation.pendingCapability ?: return
        continuation.pendingExplanationMode?.let { explanationMode ->
            showSheet(explanationMode.toBottomSheetMode())
            return
        }
        when {
            continuation.waitingForSettings -> {
                completeAction(
                    continuation.consumeSettingsReturn(policy().isGranted(capability))
                )
            }
            continuation.waitingForRuntimePermission -> {
                completeAction(
                    continuation.consumeIfGranted(policy().isGranted(capability))
                )
            }
            else -> dispatchCurrentDecision()
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
        continuation.begin(
            capability = capability,
            actionToken = actionToken
        )
        dispatchCurrentDecision()
    }

    /**
     * Presents the same central settings explanation for a system-level capability
     * block that is not represented by a missing runtime permission.
     */
    fun openSettingsFor(capability: AppCapability, actionToken: String) {
        continuation.begin(
            capability = capability,
            actionToken = actionToken
        )
        showSheet(CapabilityPermissionBottomSheet.Mode.OPEN_SETTINGS)
    }

    /**
     * Routes a blocked notification category through the same process-safe sheet and
     * Activity Result lifecycle while keeping Settings Intent construction central.
     */
    fun openNotificationChannelSettingsFor(
        channelId: String,
        actionToken: String
    ) {
        require(channelId.isNotBlank()) { "Notification channel ID must not be blank." }
        continuation.begin(
            capability = AppCapability.NOTIFICATIONS,
            actionToken = actionToken,
            notificationChannelId = channelId
        )
        showSheet(CapabilityPermissionBottomSheet.Mode.OPEN_SETTINGS)
    }

    fun isGranted(capability: AppCapability): Boolean = policy().isGranted(capability)

    fun cancelPending() {
        clearPending()
    }

    private fun dispatchCurrentDecision() {
        val capability = continuation.pendingCapability ?: return

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
        val capability = continuation.pendingCapability ?: return
        val policy = policy()
        val permissions = policy.requiredPermissions(capability)

        if (permissions.isEmpty()) {
            if (policy.isGranted(capability)) {
                complete()
            } else {
                launchSettings()
            }
            return
        }

        continuation.markWaitingForRuntimePermission()
        policy.markRequested(capability)
        runCatching {
            permissionLauncher.launch(permissions)
        }.onFailure {
            clearPending()
        }
    }

    private fun onPermissionResult() {
        val capability = continuation.pendingCapability ?: return
        if (policy().isGranted(capability)) {
            complete()
        } else {
            dispatchCurrentDecision()
        }
    }

    private fun showSheet(mode: CapabilityPermissionBottomSheet.Mode) {
        val capability = continuation.pendingCapability ?: return
        continuation.markShowingExplanation(mode.toContinuationMode())
        val manager = fragment.childFragmentManager
        if (manager.isStateSaved || manager.findFragmentByTag(sheetTag) != null) return

        CapabilityPermissionBottomSheet.newInstance(
            capability = capability,
            mode = mode,
            requestKey = resultRequestKey
        ).show(manager, sheetTag)
    }

    private fun launchSettings() {
        val capability = continuation.pendingCapability ?: return
        continuation.markWaitingForSettings()

        val primaryIntent = settingsIntent(capability)
        runCatching {
            settingsLauncher.launch(primaryIntent)
        }.recoverCatching {
            settingsLauncher.launch(Intent(Settings.ACTION_SETTINGS))
        }.onFailure {
            clearPending()
        }
    }

    private fun onSettingsReturned() {
        val capability = continuation.pendingCapability ?: return
        completeAction(
            continuation.consumeSettingsReturn(policy().isGranted(capability))
        )
    }

    private fun settingsIntent(capability: AppCapability): Intent {
        val context = fragment.requireContext()
        val notificationChannelId = continuation.pendingNotificationChannelId

        return when {
            capability == AppCapability.PRECISE_REMINDERS &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.fromParts("package", context.packageName, null)
                )
            }

            capability == AppCapability.NOTIFICATIONS &&
                notificationChannelId != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, notificationChannelId)
                }
            }

            capability == AppCapability.NOTIFICATIONS &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            }

            else -> {
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            }
        }
    }

    private fun complete() {
        completeAction(continuation.consumeAction())
    }

    private fun completeAction(actionToken: String?) {
        actionToken?.let(onGranted)
    }

    private fun clearPending() {
        continuation.clear()
    }

    private fun policy(): PermissionPolicy {
        return PermissionPolicy(fragment.requireContext())
    }

    private fun saveState(): Bundle {
        val snapshot = continuation.snapshot()
        return bundleOf(
            STATE_CAPABILITY to snapshot.capabilityName,
            STATE_ACTION to snapshot.actionToken,
            STATE_NOTIFICATION_CHANNEL_ID to snapshot.notificationChannelId,
            STATE_EXPLANATION_MODE to snapshot.explanationModeName,
            STATE_WAITING_FOR_RUNTIME_PERMISSION to snapshot.waitingForRuntimePermission,
            STATE_WAITING_FOR_SETTINGS to snapshot.waitingForSettings
        )
    }

    private fun restoreState(bundle: Bundle?) {
        continuation.restore(
            bundle?.let { restored ->
                CapabilityPermissionContinuationSnapshot(
                    capabilityName = restored.getString(STATE_CAPABILITY),
                    actionToken = restored.getString(STATE_ACTION),
                    notificationChannelId = restored.getString(STATE_NOTIFICATION_CHANNEL_ID),
                    explanationModeName = restored.getString(STATE_EXPLANATION_MODE),
                    waitingForRuntimePermission = restored.getBoolean(
                        STATE_WAITING_FOR_RUNTIME_PERMISSION
                    ),
                    waitingForSettings = restored.getBoolean(STATE_WAITING_FOR_SETTINGS)
                )
            }
        )
    }

    private companion object {
        const val DEFAULT_INSTANCE_KEY = "default"
        const val STATE_CAPABILITY = "capability"
        const val STATE_ACTION = "action"
        const val STATE_NOTIFICATION_CHANNEL_ID = "notification_channel_id"
        const val STATE_EXPLANATION_MODE = "explanation_mode"
        const val STATE_WAITING_FOR_RUNTIME_PERMISSION = "waiting_for_runtime_permission"
        const val STATE_WAITING_FOR_SETTINGS = "waiting_for_settings"
    }
}

private fun CapabilityPermissionBottomSheet.Mode.toContinuationMode() =
    when (this) {
        CapabilityPermissionBottomSheet.Mode.RATIONALE ->
            CapabilityPermissionExplanationMode.RATIONALE
        CapabilityPermissionBottomSheet.Mode.OPEN_SETTINGS ->
            CapabilityPermissionExplanationMode.OPEN_SETTINGS
    }

private fun CapabilityPermissionExplanationMode.toBottomSheetMode() =
    when (this) {
        CapabilityPermissionExplanationMode.RATIONALE ->
            CapabilityPermissionBottomSheet.Mode.RATIONALE
        CapabilityPermissionExplanationMode.OPEN_SETTINGS ->
            CapabilityPermissionBottomSheet.Mode.OPEN_SETTINGS
    }
