package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

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
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.common.DeviceDosingChannelDestinationFragment
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.DeviceDosingScheduleAmountContract
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom.DeviceDosingCustomScheduleContract
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer.DeviceDosingTimerScheduleContract
import kotlinx.coroutines.launch

/** Navigation/render host for the ViewModel-owned, firmware-independent Dosing Plan draft. */
class DeviceDosingPlanFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingPlanFragmentArgs by navArgs()
    private val viewModel: DeviceDosingPlanViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    override val destinationTitle: String
        get() = getString(R.string.device_dosing_detail_plan_title)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.bind(
            deviceUidText = args.deviceUid,
            slotIdText = args.slotId,
            restoredDraft = savedInstanceState?.let(DosingPlanDraft::restore),
            restoredBaseRevision = savedInstanceState
                ?.takeIf { state -> state.containsKey(STATE_BASE_REVISION) }
                ?.getLong(STATE_BASE_REVISION),
            restoredDraftDirty = savedInstanceState?.getBoolean(STATE_DRAFT_DIRTY, false) == true
        )
        setupDailyDoseResult()
        bindDosingPlanScheduleResults(
            host = DosingPlanScheduleResultHost(
                fragment = this,
                slotId = args.slotId,
                scheduling = { viewModel.currentEditorState.scheduling },
                updateSchedule = viewModel::applyScheduleUpdate
            ),
            lifecycleOwner = viewLifecycleOwner
        )
        setupSelectedPump(
            view = view,
            deviceUid = args.deviceUid,
            slotId = args.slotId,
            pumpCount = args.pumpCount,
            channelNumber = args.channelNumber
        )
        observePlanEvents()
        setupContent(view)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val editorState = viewModel.currentEditorState
        editorState.draft.writeTo(outState)
        editorState.baseRevision?.let { revision ->
            outState.putLong(STATE_BASE_REVISION, revision)
        }
        outState.putBoolean(STATE_DRAFT_DIRTY, editorState.draftDirty)
        super.onSaveInstanceState(outState)
    }

    private fun setupContent(view: View) {
        view.findViewById<ComposeView>(R.id.channelDetailContent).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val editorState by viewModel.editorState.collectAsStateWithLifecycle()
                val draft = editorState.draft
                DeviceDosingPlanScreen(
                    state = DeviceDosingPlanUiState(
                        dailyDoseMicroliters = draft.displayedDailyDoseMicroliters(),
                        selectedScheduleMode = draft.selectedScheduleMode,
                        scheduleEnabled = draft.scheduleEnabled,
                        recurrenceState = draft.recurrenceState,
                        supportedModes = editorState.supportedModes,
                        recurrenceSupported = editorState.scheduling.supportsWeekdayRecurrence,
                        editorEnabled = editorState.editable &&
                            !editorState.operationInProgress,
                        canSave = editorState.canSave
                    ),
                    actions = DeviceDosingPlanActions(
                        onScheduleOptionClick = ::openScheduleEditor,
                        onDailyDoseClick = if (
                            draft.selectedScheduleMode != DosingPlanScheduleMode.TIMER
                        ) ::showDailyDoseEditor else null,
                        onScheduleEnabledChange = viewModel::setScheduleEnabled,
                        recurrence = DosingPlanRecurrenceActions(
                            onEveryDayClick = viewModel::selectEveryDay,
                            onWeekdaySelectionChange = viewModel::setWeekdaySelected
                        ),
                        onSaveClick = viewModel::save
                    )
                )
            }
        }
    }

    private fun setupDailyDoseResult() {
        childFragmentManager.setFragmentResultListener(
            DAILY_DOSE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            val expected = result.getString(TextInputBottomSheet.RESULT_PAYLOAD_ID) == args.slotId &&
                result.getString(TextInputBottomSheet.RESULT_KEY) == TextInputBottomSheet.RESULT_SAVED
            if (!expected) return@setFragmentResultListener
            DeviceDosingScheduleAmountContract.parseMicroliters(
                result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty()
            )?.let(viewModel::setDailyDoseMicroliters)
        }
    }

    @Suppress("LongMethod") // Safe Args keeps the four typed navigation contracts explicit.
    private fun openScheduleEditor(mode: DosingPlanScheduleMode) {
        val editorState = viewModel.currentEditorState
        val draft = editorState.draft
        val canOpenEditor = listOf(
            draft.scheduleEnabled,
            editorState.editable,
            !editorState.operationInProgress,
            mode in editorState.supportedModes
        ).all { enabled -> enabled }
        if (!canOpenEditor) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceDosingPlanFragment) return

        if (draft.hasSubMinuteTiming(mode)) {
            showPlanMessage(
                R.string.device_dosing_plan_subminute_timing,
                BaseActivity.SnackType.WARNING
            )
        }

        val direction = when (mode) {
            DosingPlanScheduleMode.SINGLE -> DeviceDosingPlanFragmentDirections
                .actionDeviceDosingPlanFragmentToDeviceDosingSingleScheduleFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId,
                    pumpCount = args.pumpCount,
                    channelNumber = args.channelNumber,
                    dailyDoseMicroliters = draft.distributedDailyDoseMicroliters,
                    startTimeMs = draft.singleDoseStartTimeMs
                )
            DosingPlanScheduleMode.HOURLY -> DeviceDosingPlanFragmentDirections
                .actionDeviceDosingPlanFragmentToDeviceDosingHourlyScheduleFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId,
                    pumpCount = args.pumpCount,
                    channelNumber = args.channelNumber,
                    dailyDoseMicroliters = draft.distributedDailyDoseMicroliters,
                    startTimeMs = draft.hourlyStartTimeMs
                )
            DosingPlanScheduleMode.CUSTOM -> DeviceDosingPlanFragmentDirections
                .actionDeviceDosingPlanFragmentToDeviceDosingCustomScheduleFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId,
                    pumpCount = args.pumpCount,
                    channelNumber = args.channelNumber,
                    dailyDoseMicroliters = draft.distributedDailyDoseMicroliters,
                    periodsDraft = DeviceDosingCustomScheduleContract.encodeDraft(
                        periods = draft.customPeriods,
                        maxEventsPerChannel = editorState.scheduling.maxEventsPerChannel,
                        maxPeriodsPerChannel =
                            editorState.scheduling.maxCustomPeriodsPerChannel
                    ),
                    maxEventsPerChannel = editorState.scheduling.maxEventsPerChannel,
                    maxCustomPeriodsPerChannel =
                        editorState.scheduling.maxCustomPeriodsPerChannel
                )
            DosingPlanScheduleMode.TIMER -> DeviceDosingPlanFragmentDirections
                .actionDeviceDosingPlanFragmentToDeviceDosingTimerScheduleFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId,
                    pumpCount = args.pumpCount,
                    channelNumber = args.channelNumber,
                    dosesDraft = DeviceDosingTimerScheduleContract.encodeDraft(
                        doses = draft.timerDoses,
                        maxEventsPerChannel = editorState.scheduling.maxEventsPerChannel
                    ),
                    maxEventsPerChannel = editorState.scheduling.maxEventsPerChannel
                )
        }
        navController.navigate(direction)
    }

    private fun showDailyDoseEditor() {
        val editorState = viewModel.currentEditorState
        val draft = editorState.draft
        val canEditDailyDose = listOf(
            draft.scheduleEnabled,
            editorState.editable,
            !editorState.operationInProgress,
            draft.selectedScheduleMode != DosingPlanScheduleMode.TIMER
        ).all { enabled -> enabled }
        if (!canEditDailyDose) return
        TextInputBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.device_dosing_daily_dose_editor_title),
            label = getString(R.string.device_dosing_daily_dose_editor_label),
            hint = getString(R.string.device_dosing_daily_dose_editor_hint),
            initialValue = DeviceDosingScheduleAmountContract.formatInput(
                draft.distributedDailyDoseMicroliters,
                resources.configuration.locales[0]
            ),
            supportingText = getString(R.string.device_dosing_daily_dose_editor_description),
            suffixText = getString(R.string.device_dosing_detail_ml_unit),
            saveText = getString(R.string.device_dosing_daily_dose_editor_save),
            cancelText = getString(R.string.common_cancel),
            required = true,
            requiredMessage = getString(R.string.device_dosing_daily_dose_editor_required),
            requestKey = DAILY_DOSE_REQUEST_KEY,
            payloadId = args.slotId,
            maxLength = DAILY_DOSE_INPUT_MAX_LENGTH,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            minimumNumericValueExclusive = 0.0,
            disableSaveWhenUnchanged = true,
            requestFocus = true
        )
    }

    private fun observePlanEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        DeviceDosingPlanEvent.Saved -> {
                            showPlanMessage(R.string.device_dosing_plan_saved)
                            findNavController().navigateUp()
                        }
                        is DeviceDosingPlanEvent.InvalidDraft -> showPlanMessage(
                            validationMessage(event.issue),
                            BaseActivity.SnackType.WARNING
                        )
                        is DeviceDosingPlanEvent.SaveRejected -> showPlanMessage(
                            rejectionMessage(event.reason),
                            BaseActivity.SnackType.WARNING
                        )
                        DeviceDosingPlanEvent.SaveUnavailable -> showPlanMessage(
                            R.string.device_dosing_plan_unavailable,
                            BaseActivity.SnackType.ERROR
                        )
                        DeviceDosingPlanEvent.SaveFailed -> showPlanMessage(
                            R.string.device_dosing_detail_operation_failed,
                            BaseActivity.SnackType.ERROR
                        )
                    }
                }
            }
        }
    }

    private fun showPlanMessage(
        messageRes: Int,
        type: BaseActivity.SnackType = BaseActivity.SnackType.SUCCESS
    ) {
        (activity as? BaseActivity)?.showSnackBar(getString(messageRes), type)
    }

    private companion object {
        const val DAILY_DOSE_REQUEST_KEY = "dosing_daily_dose_input"
        const val DAILY_DOSE_INPUT_MAX_LENGTH = 12
        const val STATE_BASE_REVISION = "dosing_plan_base_revision"
        const val STATE_DRAFT_DIRTY = "dosing_plan_draft_dirty"
    }
}

private fun validationMessage(issue: DosingPlanValidationIssue): Int = when (issue) {
    DosingPlanValidationIssue.DOSE_LIMIT -> R.string.device_dosing_plan_invalid_dose_limit
    DosingPlanValidationIssue.EVENT_LIMIT -> R.string.device_dosing_plan_invalid_event_limit
    DosingPlanValidationIssue.NO_DAYS -> R.string.device_dosing_plan_invalid_no_days
    DosingPlanValidationIssue.UNSUPPORTED_MODE -> R.string.device_dosing_plan_invalid_mode
    DosingPlanValidationIssue.RECOVERY_UNSUPPORTED ->
        R.string.device_dosing_plan_invalid_recovery
    DosingPlanValidationIssue.INVALID_SCHEDULE -> R.string.device_dosing_plan_invalid_schedule
}

private fun rejectionMessage(reason: DeviceDosingChannelRejection): Int = when (reason) {
    DeviceDosingChannelRejection.INVALID_DRAFT -> R.string.device_dosing_plan_invalid_schedule
    DeviceDosingChannelRejection.NOT_EDITABLE -> R.string.device_dosing_plan_rejected_not_editable
    DeviceDosingChannelRejection.NOT_CALIBRATED ->
        R.string.device_dosing_plan_rejected_not_calibrated
    DeviceDosingChannelRejection.BUSY -> R.string.device_dosing_plan_rejected_busy
    DeviceDosingChannelRejection.CONFLICT -> R.string.device_dosing_plan_rejected_conflict
    DeviceDosingChannelRejection.UNSAFE -> R.string.device_dosing_plan_rejected_unsafe
    DeviceDosingChannelRejection.UNKNOWN -> R.string.device_dosing_detail_operation_failed
}

private fun DosingPlanDraft.hasSubMinuteTiming(mode: DosingPlanScheduleMode): Boolean = when (mode) {
    DosingPlanScheduleMode.SINGLE -> singleDoseStartTimeMs.hasSubMinutePrecision()
    DosingPlanScheduleMode.HOURLY -> hourlyStartTimeMs.hasSubMinutePrecision()
    DosingPlanScheduleMode.CUSTOM -> customPeriods.any { period ->
        period.startTimeMs.hasSubMinutePrecision() || period.endTimeMs.hasSubMinutePrecision()
    }
    DosingPlanScheduleMode.TIMER -> timerDoses.any { dose ->
        dose.startTimeMs.hasSubMinutePrecision()
    }
}

private fun Long.hasSubMinutePrecision(): Boolean = this % MILLIS_PER_MINUTE != 0L

private const val MILLIS_PER_MINUTE = 60_000L
