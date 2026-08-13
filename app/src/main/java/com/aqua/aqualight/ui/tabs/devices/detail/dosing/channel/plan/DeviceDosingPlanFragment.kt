package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.common.DeviceDosingChannelDestinationFragment
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.DeviceDosingScheduleAmountContract
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom.DeviceDosingCustomScheduleContract
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer.DeviceDosingTimerScheduleContract
import kotlinx.coroutines.launch

/** Navigation/render host for the firmware-backed Dosing Plan editor. */
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
            deviceUid = args.deviceUid,
            slotId = args.slotId,
            restoredDraft = savedInstanceState?.let(DosingPlanDraft::restore)
        )
        setupDailyDoseResult()
        bindDosingPlanScheduleResults(
            host = DosingPlanScheduleResultHost(
                fragment = this,
                slotId = args.slotId,
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
        setupContent(view)
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
                val saveEnabled by viewModel.saveEnabled.collectAsStateWithLifecycle()
                val saving by viewModel.saving.collectAsStateWithLifecycle()
                DeviceDosingPlanScreen(
                    state = DeviceDosingPlanUiState(
                        dailyDoseMicroliters = draft.displayedDailyDoseMicroliters(),
                        selectedScheduleMode = draft.selectedScheduleMode,
                        scheduleEnabled = draft.scheduleEnabled,
                        recurrenceState = draft.recurrenceState,
                        saveEnabled = saveEnabled,
                        saving = saving
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
                        onSaveClick = ::savePlan
                    )
                )
            }
        }
    }

    private fun savePlan() {
        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.save()) findNavController().navigateUp()
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

    private fun openScheduleEditor(mode: DosingPlanScheduleMode) {
        val draft = viewModel.currentDraft()
        if (!draft.scheduleEnabled) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceDosingPlanFragment) return

        val direction = when (mode) {
            DosingPlanScheduleMode.SINGLE -> DeviceDosingPlanFragmentDirections
                .actionDeviceDosingPlanFragmentToDeviceDosingSingleScheduleFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId,
                    channelTitle = args.channelTitle,
                    pumpCount = args.pumpCount,
                    channelNumber = args.channelNumber,
                    dailyDoseMicroliters = draft.distributedDailyDoseMicroliters,
                    startTimeMs = draft.singleDoseStartTimeMs
                )
            DosingPlanScheduleMode.HOURLY -> DeviceDosingPlanFragmentDirections
                .actionDeviceDosingPlanFragmentToDeviceDosingHourlyScheduleFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId,
                    channelTitle = args.channelTitle,
                    pumpCount = args.pumpCount,
                    channelNumber = args.channelNumber,
                    dailyDoseMicroliters = draft.distributedDailyDoseMicroliters,
                    startTimeMs = draft.hourlyStartTimeMs
                )
            DosingPlanScheduleMode.CUSTOM -> DeviceDosingPlanFragmentDirections
                .actionDeviceDosingPlanFragmentToDeviceDosingCustomScheduleFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId,
                    channelTitle = args.channelTitle,
                    pumpCount = args.pumpCount,
                    channelNumber = args.channelNumber,
                    dailyDoseMicroliters = draft.distributedDailyDoseMicroliters,
                    periodsDraft = DeviceDosingCustomScheduleContract.encodeDraft(draft.customPeriods)
                )
            DosingPlanScheduleMode.TIMER -> DeviceDosingPlanFragmentDirections
                .actionDeviceDosingPlanFragmentToDeviceDosingTimerScheduleFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId,
                    channelTitle = args.channelTitle,
                    pumpCount = args.pumpCount,
                    channelNumber = args.channelNumber,
                    dosesDraft = DeviceDosingTimerScheduleContract.encodeDraft(draft.timerDoses)
                )
        }
        navController.navigate(direction)
    }

    private fun showDailyDoseEditor() {
        val draft = viewModel.currentDraft()
        if (!draft.scheduleEnabled || draft.selectedScheduleMode == DosingPlanScheduleMode.TIMER) return
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

    private companion object {
        const val DAILY_DOSE_REQUEST_KEY = "dosing_daily_dose_input"
        const val DAILY_DOSE_INPUT_MAX_LENGTH = 12
    }
}
