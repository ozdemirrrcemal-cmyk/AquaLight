package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir

import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.ui.common.notification.NotificationEnablementCallbacks
import com.aqua.aqualight.ui.common.notification.NotificationEnablementCoordinator
import com.aqua.aqualight.ui.common.notification.NotificationEnablementDependencies
import com.aqua.aqualight.ui.common.notification.NotificationEnablementRequest
import com.aqua.aqualight.ui.common.notification.NotificationEnablementStep

/** Owns Android notification permission orchestration for the low-level alert preference. */
internal class DeviceDosingReservoirNotificationController(
    fragment: DeviceDosingReservoirFragment,
    private val viewModel: DeviceDosingReservoirViewModel,
    private val onFailure: () -> Unit
) {
    private val appContainer by lazy { fragment.requireContext().requireAppContainer() }
    private val coordinator = NotificationEnablementCoordinator(
        fragment = fragment,
        instanceKey = "dosing-reservoir-low-level-alert",
        dependencies = NotificationEnablementDependencies(
            notificationPreferencesProvider = { appContainer.notificationPreferenceUseCase },
            ownerUidProvider = { appContainer.authenticatedOwnerIdentity.requireOwnerUid() },
            requestResolver = { action ->
                action.takeIf { it == ACTION }?.let {
                    NotificationEnablementRequest(
                        category = NotificationCategory.DEVICE_ALERTS,
                        requiresPreciseReminders = false
                    )
                }
            }
        ),
        callbacks = NotificationEnablementCallbacks(
            onReady = { action -> if (action == ACTION) enableAlertWhenTracked() },
            onStateChanged = { action, state ->
                if (action == ACTION) updateAvailability(state.canDeliver, state.step)
            },
            onFailure = { action, _ -> if (action == ACTION) handleFailure() }
        )
    )

    fun setTrackingEnabled(enabled: Boolean) {
        viewModel.setTrackingEnabled(enabled)
        if (!enabled) coordinator.cancelPending()
        else if (viewModel.currentDraft().lowLevelAlertEnabled) refresh()
    }

    fun setLowLevelAlertEnabled(enabled: Boolean) {
        if (!enabled) {
            coordinator.cancelPending()
            viewModel.setLowLevelAlertEnabled(false)
        } else if (viewModel.currentDraft().trackingEnabled) {
            coordinator.requestEnable(ACTION)
        }
    }

    fun repair() {
        val draft = viewModel.currentDraft()
        if (draft.trackingEnabled && draft.lowLevelAlertEnabled) coordinator.requestEnable(ACTION)
    }

    fun refresh() = coordinator.refresh(ACTION)

    private fun enableAlertWhenTracked() {
        if (!viewModel.currentDraft().trackingEnabled) return
        viewModel.setNotificationAvailability(DeviceDosingReservoirNotificationAvailability.AVAILABLE)
        viewModel.setLowLevelAlertEnabled(true)
    }

    private fun updateAvailability(canDeliver: Boolean, step: NotificationEnablementStep) {
        val availability = when {
            !viewModel.currentDraft().lowLevelAlertEnabled || canDeliver ->
                DeviceDosingReservoirNotificationAvailability.AVAILABLE
            step != NotificationEnablementStep.READY ->
                DeviceDosingReservoirNotificationAvailability.ANDROID_BLOCKED
            else -> DeviceDosingReservoirNotificationAvailability.OWNER_PREFERENCE_DISABLED
        }
        viewModel.setNotificationAvailability(availability)
    }

    private fun handleFailure() {
        val availability = if (viewModel.currentDraft().lowLevelAlertEnabled) {
            DeviceDosingReservoirNotificationAvailability.ANDROID_BLOCKED
        } else {
            DeviceDosingReservoirNotificationAvailability.AVAILABLE
        }
        viewModel.setNotificationAvailability(availability)
        onFailure()
    }

    private companion object {
        const val ACTION = "enable-dosing-low-level-alert"
    }
}
