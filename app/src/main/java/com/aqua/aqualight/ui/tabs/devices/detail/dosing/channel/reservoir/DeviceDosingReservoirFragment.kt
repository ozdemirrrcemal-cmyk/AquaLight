package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirCapacityPolicy
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.common.notification.NotificationEnablementCallbacks
import com.aqua.aqualight.ui.common.notification.NotificationEnablementCoordinator
import com.aqua.aqualight.ui.common.notification.NotificationEnablementDependencies
import com.aqua.aqualight.ui.common.notification.NotificationEnablementRequest
import com.aqua.aqualight.ui.common.notification.NotificationEnablementStep
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.common.DeviceDosingChannelDestinationFragment
import java.util.Locale

/** Render/input host for the ViewModel-owned reservoir draft. */
@Suppress("TooManyFunctions") // Lifecycle and notification-gated user intents stay local.
class DeviceDosingReservoirFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingReservoirFragmentArgs by navArgs()
    private val viewModel: DeviceDosingReservoirViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private val appContainer by lazy {
        requireContext().requireAppContainer()
    }
    private val notificationEnablementCoordinator = NotificationEnablementCoordinator(
        fragment = this,
        instanceKey = "dosing-reservoir-low-level-alert",
        dependencies = NotificationEnablementDependencies(
            notificationPreferencesProvider = {
                appContainer.notificationPreferenceUseCase
            },
            ownerUidProvider = {
                appContainer.authenticatedOwnerIdentity.requireOwnerUid()
            },
            requestResolver = { actionToken ->
                if (actionToken == ACTION_ENABLE_LOW_LEVEL_ALERT) {
                    NotificationEnablementRequest(
                        category = NotificationCategory.DEVICE_ALERTS,
                        requiresPreciseReminders = false
                    )
                } else {
                    null
                }
            }
        ),
        callbacks = NotificationEnablementCallbacks(
            onReady = { actionToken ->
                if (
                    actionToken == ACTION_ENABLE_LOW_LEVEL_ALERT &&
                    viewModel.currentDraft().trackingEnabled
                ) {
                    viewModel.setNotificationAvailability(
                        DeviceDosingReservoirNotificationAvailability.AVAILABLE
                    )
                    viewModel.setLowLevelAlertEnabled(true)
                }
            },
            onStateChanged = { actionToken, state ->
                if (actionToken == ACTION_ENABLE_LOW_LEVEL_ALERT) {
                    val alertEnabled = viewModel.currentDraft().lowLevelAlertEnabled
                    viewModel.setNotificationAvailability(
                        when {
                            !alertEnabled || state.canDeliver -> {
                                DeviceDosingReservoirNotificationAvailability.AVAILABLE
                            }
                            state.step != NotificationEnablementStep.READY -> {
                                DeviceDosingReservoirNotificationAvailability.ANDROID_BLOCKED
                            }
                            else -> {
                                DeviceDosingReservoirNotificationAvailability
                                    .OWNER_PREFERENCE_DISABLED
                            }
                        }
                    )
                }
            },
            onFailure = { actionToken, _ ->
                if (actionToken == ACTION_ENABLE_LOW_LEVEL_ALERT) {
                    handleNotificationAccessFailure()
                }
            }
        )
    )

    override val destinationTitle: String
        get() = getString(R.string.device_dosing_detail_reservoir_title)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.bindInitial(savedInstanceState?.toReservoirDraft())
        setupReservoirCapacityResult()
        setupSelectedPump(
            view = view,
            deviceUid = args.deviceUid,
            slotId = args.slotId,
            pumpCount = args.pumpCount,
            channelNumber = args.channelNumber
        )
        setupContent(view)
    }

    override fun onResume() {
        super.onResume()
        refreshLowLevelNotificationState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewModel.currentDraft().writeTo(outState)
        super.onSaveInstanceState(outState)
    }

    private fun setupContent(view: View) {
        view.findViewById<ComposeView>(R.id.channelDetailContent).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val draft by viewModel.draft.collectAsStateWithLifecycle()
                val notificationAvailability by
                    viewModel.notificationAvailability.collectAsStateWithLifecycle()
                val capacityRejection by viewModel.capacityRejection.collectAsStateWithLifecycle()
                DeviceDosingReservoirScreen(
                    state = DeviceDosingReservoirUiState(
                        trackingEnabled = draft.trackingEnabled,
                        capacityValue = getString(
                            R.string.device_dosing_detail_value_container_ml,
                            DeviceDosingReservoirCapacityPolicy.format(
                                draft.reservoirCapacityMicroliters,
                                currentLocale()
                            )
                        ),
                        capacityRejection = capacityRejection,
                        lowLevelAlertEnabled = draft.lowLevelAlertEnabled,
                        lowLevelAlertNotificationAvailability = notificationAvailability
                    ),
                    actions = DeviceDosingReservoirActions(
                        onTrackingEnabledChange = ::setTrackingEnabled,
                        onCapacityClick = ::showReservoirCapacityEditor,
                        onLowLevelAlertEnabledChange = ::setLowLevelAlertEnabled,
                        onRepairLowLevelAlertNotifications = ::repairLowLevelNotifications,
                        onSaveClick = null
                    )
                )
            }
        }
    }

    private fun setTrackingEnabled(enabled: Boolean) {
        viewModel.setTrackingEnabled(enabled)
        if (!enabled) {
            notificationEnablementCoordinator.cancelPending()
        } else if (viewModel.currentDraft().lowLevelAlertEnabled) {
            refreshLowLevelNotificationState()
        }
    }

    private fun setLowLevelAlertEnabled(enabled: Boolean) {
        if (!enabled) {
            notificationEnablementCoordinator.cancelPending()
            viewModel.setLowLevelAlertEnabled(false)
            return
        }
        if (!viewModel.currentDraft().trackingEnabled) return
        notificationEnablementCoordinator.requestEnable(ACTION_ENABLE_LOW_LEVEL_ALERT)
    }

    private fun repairLowLevelNotifications() {
        val draft = viewModel.currentDraft()
        if (!draft.trackingEnabled || !draft.lowLevelAlertEnabled) return
        notificationEnablementCoordinator.requestEnable(ACTION_ENABLE_LOW_LEVEL_ALERT)
    }

    private fun refreshLowLevelNotificationState() {
        notificationEnablementCoordinator.refresh(ACTION_ENABLE_LOW_LEVEL_ALERT)
    }

    private fun handleNotificationAccessFailure() {
        viewModel.setNotificationAvailability(
            if (viewModel.currentDraft().lowLevelAlertEnabled) {
                DeviceDosingReservoirNotificationAvailability.ANDROID_BLOCKED
            } else {
                DeviceDosingReservoirNotificationAvailability.AVAILABLE
            }
        )
        (activity as? BaseActivity)?.showSnackBar(
            getString(R.string.notification_feature_access_check_failed),
            BaseActivity.SnackType.ERROR
        )
    }

    private fun setupReservoirCapacityResult() {
        childFragmentManager.setFragmentResultListener(
            RESERVOIR_CAPACITY_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            val expected = result.getString(TextInputBottomSheet.RESULT_PAYLOAD_ID) ==
                RESERVOIR_CAPACITY_PAYLOAD_ID &&
                result.getString(TextInputBottomSheet.RESULT_KEY) == TextInputBottomSheet.RESULT_SAVED
            if (!expected) return@setFragmentResultListener
            viewModel.setCapacityInput(
                rawValue = result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty(),
                locale = currentLocale()
            )
        }
    }

    private fun showReservoirCapacityEditor() {
        val draft = viewModel.currentDraft()
        if (!draft.trackingEnabled) return
        TextInputBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.device_dosing_detail_container_volume),
            label = getString(R.string.device_dosing_detail_container_volume_input_label),
            hint = getString(R.string.device_dosing_detail_container_volume_hint),
            initialValue = DeviceDosingReservoirCapacityPolicy.format(
                draft.reservoirCapacityMicroliters,
                currentLocale()
            ),
            saveText = getString(R.string.common_save),
            cancelText = getString(R.string.common_cancel),
            required = true,
            requiredMessage = getString(R.string.device_dosing_detail_container_volume_required),
            requestKey = RESERVOIR_CAPACITY_REQUEST_KEY,
            payloadId = RESERVOIR_CAPACITY_PAYLOAD_ID,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            disableSaveWhenUnchanged = true,
            requestFocus = true
        )
    }

    private fun currentLocale(): Locale = resources.configuration.locales[0]
}

private fun Bundle.toReservoirDraft() = DeviceDosingReservoirDraft(
    reservoirCapacityMicroliters = DeviceDosingReservoirCapacityPolicy
        .normalizePersistedMicroliters(
            getLong(
                STATE_RESERVOIR_CAPACITY_MICROLITERS,
                DeviceDosingReservoirCapacityPolicy.DEFAULT_CAPACITY_MICROLITERS
            )
        ),
    trackingEnabled = getBoolean(STATE_TRACKING_ENABLED, false),
    lowLevelAlertEnabled = getBoolean(STATE_LOW_LEVEL_ALERT_ENABLED, false)
)

private fun DeviceDosingReservoirDraft.writeTo(outState: Bundle) {
    outState.putLong(STATE_RESERVOIR_CAPACITY_MICROLITERS, reservoirCapacityMicroliters)
    outState.putBoolean(STATE_TRACKING_ENABLED, trackingEnabled)
    outState.putBoolean(STATE_LOW_LEVEL_ALERT_ENABLED, lowLevelAlertEnabled)
}

private const val STATE_RESERVOIR_CAPACITY_MICROLITERS = "reservoir_capacity_microliters"
private const val STATE_TRACKING_ENABLED = "reservoir_tracking_enabled"
private const val STATE_LOW_LEVEL_ALERT_ENABLED = "reservoir_low_level_alert_enabled"
private const val RESERVOIR_CAPACITY_REQUEST_KEY = "dosing_reservoir_capacity_input"
private const val RESERVOIR_CAPACITY_PAYLOAD_ID = "reservoir_capacity"
private const val ACTION_ENABLE_LOW_LEVEL_ALERT = "enable-dosing-low-level-alert"
