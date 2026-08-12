package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.common.DeviceDosingChannelDestinationFragment
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.DeviceDosingScheduleAmountContract
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom.DeviceDosingCustomScheduleContract
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer.DeviceDosingTimerScheduleContract

/** Process-safe UI draft owner for the Dosing Plan child feature. */
class DeviceDosingPlanFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingPlanFragmentArgs by navArgs()
    private var draft by mutableStateOf(DosingPlanDraft())

    override val destinationTitle: String
        get() = getString(R.string.device_dosing_detail_plan_title)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        draft = DosingPlanDraft.restore(savedInstanceState)
        setupDailyDoseResult()
        bindDosingPlanScheduleResults(
            host = DosingPlanScheduleResultHost(
                fragment = this,
                slotId = args.slotId,
                updateSingle = { startTimeMs ->
                    draft = draft.copy(
                        singleDoseStartTimeMs = startTimeMs,
                        selectedScheduleMode = DosingPlanScheduleMode.SINGLE
                    )
                },
                updateHourly = { startTimeMs ->
                    draft = draft.copy(
                        hourlyStartTimeMs = startTimeMs,
                        selectedScheduleMode = DosingPlanScheduleMode.HOURLY
                    )
                },
                updateCustom = { periods ->
                    draft = draft.copy(
                        customPeriods = periods,
                        selectedScheduleMode = DosingPlanScheduleMode.CUSTOM
                    )
                },
                updateTimer = { doses ->
                    draft = draft.copy(
                        timerDoses = doses,
                        selectedScheduleMode = DosingPlanScheduleMode.TIMER
                    )
                }
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
        draft.writeTo(outState)
        super.onSaveInstanceState(outState)
    }

    private fun setupContent(view: View) {
        view.findViewById<ComposeView>(R.id.channelDetailContent).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DeviceDosingPlanScreen(
                    state = DeviceDosingPlanUiState(
                        dailyDoseMicroliters = draft.displayedDailyDoseMicroliters(),
                        selectedScheduleMode = draft.selectedScheduleMode,
                        scheduleEnabled = draft.scheduleEnabled,
                        recurrenceState = draft.recurrenceState
                    ),
                    actions = DeviceDosingPlanActions(
                        onScheduleOptionClick = ::openScheduleEditor,
                        onDailyDoseClick = if (
                            draft.selectedScheduleMode != DosingPlanScheduleMode.TIMER
                        ) {
                            ::showDailyDoseEditor
                        } else {
                            null
                        },
                        onScheduleEnabledChange = { enabled ->
                            draft = draft.copy(scheduleEnabled = enabled)
                        },
                        recurrence = DosingPlanRecurrenceActions(
                            onEveryDayClick = {
                                draft = draft.copy(
                                    recurrenceState = draft.recurrenceState.selectEveryDay()
                                )
                            },
                            onWeekdaySelectionChange = { weekday, selected ->
                                draft = draft.copy(
                                    recurrenceState = draft.recurrenceState.withDaySelection(
                                        weekday = weekday,
                                        selected = selected
                                    )
                                )
                            }
                        ),
                        onSaveClick = null
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
            )?.let { microliters ->
                draft = draft.copy(distributedDailyDoseMicroliters = microliters)
            }
        }
    }

    private fun openScheduleEditor(mode: DosingPlanScheduleMode) {
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
