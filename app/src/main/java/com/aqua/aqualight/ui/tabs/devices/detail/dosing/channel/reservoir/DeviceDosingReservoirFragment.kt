package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirCapacityPolicy
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.common.dialog.UnsavedChangesExitGuard
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.common.notification.NotificationEnablementCallbacks
import com.aqua.aqualight.ui.common.notification.NotificationEnablementCoordinator
import com.aqua.aqualight.ui.common.notification.NotificationEnablementDependencies
import com.aqua.aqualight.ui.common.notification.NotificationEnablementRequest
import com.aqua.aqualight.ui.common.notification.NotificationEnablementStep
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.common.DeviceDosingChannelDestinationFragment
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Render/input host for the authoritative reservoir editor. */
@Suppress("TooManyFunctions") // Lifecycle and notification-gated user intents stay local.
class DeviceDosingReservoirFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingReservoirFragmentArgs by navArgs()
    private val viewModel: DeviceDosingReservoirViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private val appContainer by lazy { requireContext().requireAppContainer() }
    private lateinit var unsavedChangesExitGuard: UnsavedChangesExitGuard
    private val notificationEnablementCoordinator = NotificationEnablementCoordinator(
        fragment = this,
        instanceKey = "dosing-reservoir-low-level-alert",
        dependencies = NotificationEnablementDependencies(
            notificationPreferencesProvider = { appContainer.notificationPreferenceUseCase },
            ownerUidProvider = { appContainer.authenticatedOwnerIdentity.requireOwnerUid() },
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
                            !alertEnabled || state.canDeliver ->
                                DeviceDosingReservoirNotificationAvailability.AVAILABLE
                            state.step != NotificationEnablementStep.READY ->
                                DeviceDosingReservoirNotificationAvailability.ANDROID_BLOCKED
                            else ->
                                DeviceDosingReservoirNotificationAvailability.OWNER_PREFERENCE_DISABLED
                        }
                    )
                }
            },
            onFailure = { actionToken, _ ->
                if (actionToken == ACTION_ENABLE_LOW_LEVEL_ALERT) handleNotificationAccessFailure()
            }
        )
    )

    override val destinationTitle: String
        get() = getString(R.string.device_dosing_detail_reservoir_title)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.bind(deviceUidText = args.deviceUid, slotIdText = args.slotId)
        setupUnsavedChangesExitGuard()
        setupReservoirCapacityResult()
        setupSelectedPump(
            view = view,
            deviceUid = args.deviceUid,
            slotId = args.slotId,
            pumpCount = args.pumpCount,
            channelNumber = args.channelNumber
        )
        observeReservoirEvents()
        observeOperationLoading()
        setupContent(view)
    }

    override fun onBackRequested() = unsavedChangesExitGuard.requestExit()

    override fun onResume() {
        super.onResume()
        refreshLowLevelNotificationState()
    }

    private fun setupContent(view: View) {
        view.findViewById<ComposeView>(R.id.channelDetailContent).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val editorState by viewModel.editorState.collectAsStateWithLifecycle()
                val draft = editorState.draft
                DeviceDosingReservoirScreen(
                    state = DeviceDosingReservoirUiState(
                        trackingEnabled = draft.trackingEnabled,
                        capacityValue = formatCapacity(draft.reservoirCapacityMicroliters),
                        remainingValue = editorState.remainingMicroliters
                            ?.takeIf {
                                draft.trackingEnabled && editorState.remainingAccountingCertain
                            }
                            ?.let(::formatRemainingVolume)
                            ?: getString(R.string.device_dosing_detail_value_unavailable),
                        capacityRejection = editorState.capacityRejection,
                        lowLevelAlertEnabled = draft.lowLevelAlertEnabled,
                        reservoirNeedsAttention = editorState.reservoirNeedsAttention,
                        editorEnabled = editorState.editable && !editorState.operationInProgress,
                        canSave = editorState.canSave,
                        canRefill = editorState.canRefill,
                        lowLevelAlertNotificationAvailability = editorState.notificationAvailability
                    ),
                    actions = DeviceDosingReservoirActions(
                        onTrackingEnabledChange = ::setTrackingEnabled,
                        onCapacityClick = ::showReservoirCapacityEditor,
                        onRefillClick = viewModel::refill,
                        onLowLevelAlertEnabledChange = ::setLowLevelAlertEnabled,
                        onRepairLowLevelAlertNotifications = ::repairLowLevelNotifications,
                        onSaveClick = viewModel::save
                    )
                )
            }
        }
    }

    private fun setupUnsavedChangesExitGuard() {
        unsavedChangesExitGuard = UnsavedChangesExitGuard.attach(
            fragment = this,
            requestKey = UNSAVED_CHANGES_REQUEST_KEY,
            actionId = ACTION_EXIT_WITHOUT_SAVING,
            hasUnsavedChanges = { viewModel.currentEditorState().dirty },
            isExitBlocked = { viewModel.currentEditorState().operationInProgress },
            exit = {
                val navController = findNavController()
                if (navController.currentDestination?.id == R.id.deviceDosingReservoirFragment) {
                    navController.navigateUp()
                }
            }
        )
    }

    private fun formatCapacity(microliters: Long): String = getString(
        R.string.device_dosing_detail_value_container_ml,
        DeviceDosingReservoirCapacityPolicy.format(microliters, currentLocale())
    )

    private fun formatRemainingVolume(microliters: Long): String = getString(
        R.string.device_dosing_detail_value_container_ml,
        DeviceDosingReservoirCapacityPolicy.formatRuntimeVolume(microliters, currentLocale())
    )

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
        showReservoirMessage(
            R.string.notification_feature_access_check_failed,
            BaseActivity.SnackType.ERROR
        )
    }

    private fun observeReservoirEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        DeviceDosingReservoirEvent.Saved -> {
                            showReservoirMessage(R.string.device_dosing_reservoir_saved)
                            findNavController().navigateUp()
                        }
                        is DeviceDosingReservoirEvent.SaveRejected -> showReservoirMessage(
                            event.reason.messageRes,
                            BaseActivity.SnackType.ERROR
                        )
                        DeviceDosingReservoirEvent.Refilled ->
                            showReservoirMessage(R.string.device_dosing_reservoir_refilled)
                        DeviceDosingReservoirEvent.SaveFailed,
                        DeviceDosingReservoirEvent.RefillFailed -> showReservoirMessage(
                            R.string.device_dosing_detail_operation_failed,
                            BaseActivity.SnackType.ERROR
                        )
                    }
                }
            }
        }
    }

    private fun observeOperationLoading() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.editorState
                    .map { state -> state.operationInProgress }
                    .distinctUntilChanged()
                    .collect { loading -> setFragmentGlobalLoading(loading) }
            }
        }
    }

    private fun showReservoirMessage(
        messageRes: Int,
        type: BaseActivity.SnackType = BaseActivity.SnackType.SUCCESS
    ) {
        (activity as? BaseActivity)?.showSnackBar(getString(messageRes), type)
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
        val state = viewModel.currentEditorState()
        val draft = state.draft
        if (!draft.trackingEnabled || !state.editable || state.operationInProgress) return
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

private val DeviceDosingChannelRejection.messageRes: Int
    get() = when (this) {
        DeviceDosingChannelRejection.INVALID_DRAFT -> R.string.device_dosing_detail_error_invalid_input
        DeviceDosingChannelRejection.NOT_EDITABLE -> R.string.device_dosing_detail_error_not_editable
        DeviceDosingChannelRejection.NOT_CALIBRATED ->
            R.string.device_dosing_detail_error_calibration_required
        DeviceDosingChannelRejection.BUSY -> R.string.device_dosing_detail_error_busy
        DeviceDosingChannelRejection.CONFLICT -> R.string.device_dosing_detail_error_state_changed
        DeviceDosingChannelRejection.UNSAFE -> R.string.device_dosing_detail_error_safety_blocked
        DeviceDosingChannelRejection.UNKNOWN -> R.string.device_dosing_detail_operation_failed
    }

private const val RESERVOIR_CAPACITY_REQUEST_KEY = "dosing_reservoir_capacity_input"
private const val RESERVOIR_CAPACITY_PAYLOAD_ID = "reservoir_capacity"
private const val ACTION_ENABLE_LOW_LEVEL_ALERT = "enable-dosing-low-level-alert"
private const val UNSAVED_CHANGES_REQUEST_KEY = "dosing_reservoir_unsaved_changes"
private const val ACTION_EXIT_WITHOUT_SAVING = "exit_dosing_reservoir_without_saving"
