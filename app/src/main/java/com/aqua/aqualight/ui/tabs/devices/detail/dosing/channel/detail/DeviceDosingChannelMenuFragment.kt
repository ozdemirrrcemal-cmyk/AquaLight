package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.DeviceDosingChannelDestinationFragment
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.DeviceDosingScheduleAmountContract
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom.DeviceDosingCustomPeriod
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom.DeviceDosingCustomScheduleContract
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer.DeviceDosingTimerDose
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer.DeviceDosingTimerScheduleContract
import java.text.NumberFormat

/** UI-only child destination for one Dosing detail menu entry. */
class DeviceDosingChannelMenuFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingChannelMenuFragmentArgs by navArgs()
    private val menuItem: DosingDetailMenuItem?
        get() = DosingDetailMenuItem.fromRouteKey(args.menuKey)

    private var reservoirCapacityMl by mutableDoubleStateOf(DEFAULT_RESERVOIR_CAPACITY_ML)
    private var distributedDailyDoseMicroliters by
        mutableLongStateOf(DEFAULT_DAILY_DOSE_MICROLITERS)
    private var singleDoseStartTimeMs by mutableLongStateOf(DEFAULT_SINGLE_DOSE_START_TIME_MS)
    private var hourlyStartTimeMs by mutableLongStateOf(DEFAULT_HOURLY_START_TIME_MS)
    private var customPeriods by mutableStateOf<List<DeviceDosingCustomPeriod>>(emptyList())
    private var timerDoses by mutableStateOf<List<DeviceDosingTimerDose>>(emptyList())
    private var selectedScheduleMode by mutableStateOf(DosingPlanScheduleMode.SINGLE)
    private var scheduleEnabled by mutableStateOf(DEFAULT_SCHEDULE_ENABLED)
    private var recurrenceState by mutableStateOf(DosingPlanRecurrenceState())

    override val destinationTitle: String
        get() = menuItem
            ?.let { item -> getString(item.titleRes) }
            ?: getString(R.string.device_family_dosing)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val item = menuItem ?: run {
            findNavController().navigateUp()
            return
        }
        restoreState(savedInstanceState)
        if (item == DosingDetailMenuItem.RESERVOIR) {
            setupReservoirCapacityResult()
        }
        if (item == DosingDetailMenuItem.DOSING_PLAN) {
            setupDailyDoseResult()
            bindDosingScheduleResults(
                host = DeviceDosingScheduleResultHost(
                    fragment = this,
                    slotId = args.slotId,
                    updateSingle = { startTimeMs ->
                        singleDoseStartTimeMs = startTimeMs
                        selectedScheduleMode = DosingPlanScheduleMode.SINGLE
                    },
                    updateHourly = { startTimeMs ->
                        hourlyStartTimeMs = startTimeMs
                        selectedScheduleMode = DosingPlanScheduleMode.HOURLY
                    },
                    updateCustom = { updatedPeriods ->
                        customPeriods = updatedPeriods
                        selectedScheduleMode = DosingPlanScheduleMode.CUSTOM
                    },
                    updateTimer = { updatedDoses ->
                        timerDoses = updatedDoses
                        selectedScheduleMode = DosingPlanScheduleMode.TIMER
                    }
                ),
                lifecycleOwner = viewLifecycleOwner
            )
        }
        setupSelectedPump(
            view = view,
            deviceUid = args.deviceUid,
            slotId = args.slotId,
            pumpCount = args.pumpCount,
            channelNumber = args.channelNumber
        )
        setupMenuContent(view, item)
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        reservoirCapacityMl = savedInstanceState?.getDouble(
            STATE_RESERVOIR_CAPACITY_ML,
            DEFAULT_RESERVOIR_CAPACITY_ML
        ) ?: DEFAULT_RESERVOIR_CAPACITY_ML
        distributedDailyDoseMicroliters = savedInstanceState?.getLong(
            STATE_DAILY_DOSE_MICROLITERS,
            DEFAULT_DAILY_DOSE_MICROLITERS
        ) ?: DEFAULT_DAILY_DOSE_MICROLITERS
        singleDoseStartTimeMs = savedInstanceState?.getLong(
            STATE_SINGLE_DOSE_START_TIME_MS,
            DEFAULT_SINGLE_DOSE_START_TIME_MS
        ) ?: DEFAULT_SINGLE_DOSE_START_TIME_MS
        hourlyStartTimeMs = savedInstanceState?.getLong(
            STATE_HOURLY_START_TIME_MS,
            DEFAULT_HOURLY_START_TIME_MS
        ) ?: DEFAULT_HOURLY_START_TIME_MS
        customPeriods = savedInstanceState
            ?.getString(STATE_CUSTOM_PERIODS_DRAFT)
            ?.let(DeviceDosingCustomScheduleContract::decodeDraft)
            ?: emptyList()
        timerDoses = savedInstanceState
            ?.getString(STATE_TIMER_DOSES_DRAFT)
            ?.let(DeviceDosingTimerScheduleContract::decodeDraft)
            ?: emptyList()
        selectedScheduleMode = savedInstanceState
            ?.getString(STATE_SELECTED_SCHEDULE_MODE)
            ?.let { savedMode ->
                DosingPlanScheduleMode.entries.firstOrNull { mode -> mode.name == savedMode }
            }
            ?: DosingPlanScheduleMode.SINGLE
        scheduleEnabled = savedInstanceState?.getBoolean(
            STATE_SCHEDULE_ENABLED,
            DEFAULT_SCHEDULE_ENABLED
        ) ?: DEFAULT_SCHEDULE_ENABLED
        recurrenceState = savedInstanceState
            ?.getBooleanArray(STATE_SCHEDULE_WEEKDAYS)
            ?.let(DosingPlanRecurrenceState::fromWeekdayFlags)
            ?: DosingPlanRecurrenceState()
    }

    private fun setupMenuContent(view: View, item: DosingDetailMenuItem) {
        view.findViewById<ComposeView>(R.id.channelDetailContent).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DeviceDosingChannelMenuScreen(
                    item = item,
                    dailyDoseMicroliters = displayedDailyDoseMicroliters(),
                    selectedScheduleMode = selectedScheduleMode,
                    scheduleEnabled = scheduleEnabled,
                    recurrenceState = recurrenceState,
                    reservoirCapacityValue = getString(
                        R.string.device_dosing_detail_value_container_ml,
                        reservoirCapacityMl
                    ),
                    onReservoirCapacityClick = if (item == DosingDetailMenuItem.RESERVOIR) {
                        ::showReservoirCapacityEditor
                    } else {
                        null
                    },
                    onScheduleOptionClick = if (item == DosingDetailMenuItem.DOSING_PLAN) {
                        ::openScheduleEditor
                    } else {
                        null
                    },
                    onDailyDoseClick = if (
                        item == DosingDetailMenuItem.DOSING_PLAN &&
                        selectedScheduleMode != DosingPlanScheduleMode.TIMER
                    ) {
                        ::showDailyDoseEditor
                    } else {
                        null
                    },
                    onScheduleEnabledChange = if (item == DosingDetailMenuItem.DOSING_PLAN) {
                        { enabled -> scheduleEnabled = enabled }
                    } else {
                        null
                    },
                    onEveryDayClick = if (item == DosingDetailMenuItem.DOSING_PLAN) {
                        { recurrenceState = recurrenceState.selectEveryDay() }
                    } else {
                        null
                    },
                    onWeekdaySelectionChange = if (item == DosingDetailMenuItem.DOSING_PLAN) {
                        { weekday, selected ->
                            recurrenceState = recurrenceState.withDaySelection(
                                weekday = weekday,
                                selected = selected
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putDouble(STATE_RESERVOIR_CAPACITY_ML, reservoirCapacityMl)
        outState.putLong(STATE_DAILY_DOSE_MICROLITERS, distributedDailyDoseMicroliters)
        outState.putLong(STATE_SINGLE_DOSE_START_TIME_MS, singleDoseStartTimeMs)
        outState.putLong(STATE_HOURLY_START_TIME_MS, hourlyStartTimeMs)
        outState.putString(
            STATE_CUSTOM_PERIODS_DRAFT,
            DeviceDosingCustomScheduleContract.encodeDraft(customPeriods)
        )
        outState.putString(
            STATE_TIMER_DOSES_DRAFT,
            DeviceDosingTimerScheduleContract.encodeDraft(timerDoses)
        )
        outState.putString(STATE_SELECTED_SCHEDULE_MODE, selectedScheduleMode.name)
        outState.putBoolean(STATE_SCHEDULE_ENABLED, scheduleEnabled)
        outState.putBooleanArray(
            STATE_SCHEDULE_WEEKDAYS,
            recurrenceState.toWeekdayFlags()
        )
        super.onSaveInstanceState(outState)
    }

    private fun displayedDailyDoseMicroliters(): Long =
        if (selectedScheduleMode == DosingPlanScheduleMode.TIMER) {
            DeviceDosingTimerScheduleContract.totalDoseMicroliters(timerDoses)
        } else {
            distributedDailyDoseMicroliters
        }

    private fun setupDailyDoseResult() {
        childFragmentManager.setFragmentResultListener(
            DAILY_DOSE_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(TextInputBottomSheet.RESULT_PAYLOAD_ID) != args.slotId ||
                result.getString(TextInputBottomSheet.RESULT_KEY) != TextInputBottomSheet.RESULT_SAVED
            ) {
                return@setFragmentResultListener
            }
            DeviceDosingScheduleAmountContract.parseMicroliters(
                result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty()
            )?.let { microliters -> distributedDailyDoseMicroliters = microliters }
        }
    }

    private fun openScheduleEditor(mode: DosingPlanScheduleMode) {
        if (!scheduleEnabled) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceDosingChannelMenuFragment) {
            return
        }
        val direction = when (mode) {
            DosingPlanScheduleMode.SINGLE -> DeviceDosingChannelMenuFragmentDirections
                .actionDeviceDosingChannelMenuFragmentToDeviceDosingSingleScheduleFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId,
                    channelTitle = args.channelTitle,
                    pumpCount = args.pumpCount,
                    channelNumber = args.channelNumber,
                    dailyDoseMicroliters = distributedDailyDoseMicroliters,
                    startTimeMs = singleDoseStartTimeMs
                )
            DosingPlanScheduleMode.HOURLY -> DeviceDosingChannelMenuFragmentDirections
                .actionDeviceDosingChannelMenuFragmentToDeviceDosingHourlyScheduleFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId,
                    channelTitle = args.channelTitle,
                    pumpCount = args.pumpCount,
                    channelNumber = args.channelNumber,
                    dailyDoseMicroliters = distributedDailyDoseMicroliters,
                    startTimeMs = hourlyStartTimeMs
                )
            DosingPlanScheduleMode.CUSTOM -> DeviceDosingChannelMenuFragmentDirections
                .actionDeviceDosingChannelMenuFragmentToDeviceDosingCustomScheduleFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId,
                    channelTitle = args.channelTitle,
                    pumpCount = args.pumpCount,
                    channelNumber = args.channelNumber,
                    dailyDoseMicroliters = distributedDailyDoseMicroliters,
                    periodsDraft = DeviceDosingCustomScheduleContract.encodeDraft(customPeriods)
                )
            DosingPlanScheduleMode.TIMER -> DeviceDosingChannelMenuFragmentDirections
                .actionDeviceDosingChannelMenuFragmentToDeviceDosingTimerScheduleFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId,
                    channelTitle = args.channelTitle,
                    pumpCount = args.pumpCount,
                    channelNumber = args.channelNumber,
                    dosesDraft = DeviceDosingTimerScheduleContract.encodeDraft(timerDoses)
                )
        }
        navController.navigate(direction)
    }

    private fun showDailyDoseEditor() {
        if (!scheduleEnabled || selectedScheduleMode == DosingPlanScheduleMode.TIMER) return
        TextInputBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.device_dosing_daily_dose_editor_title),
            label = getString(R.string.device_dosing_daily_dose_editor_label),
            hint = getString(R.string.device_dosing_daily_dose_editor_hint),
            initialValue = DeviceDosingScheduleAmountContract.formatInput(
                distributedDailyDoseMicroliters,
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

    private fun setupReservoirCapacityResult() {
        childFragmentManager.setFragmentResultListener(
            RESERVOIR_CAPACITY_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(TextInputBottomSheet.RESULT_PAYLOAD_ID) !=
                RESERVOIR_CAPACITY_PAYLOAD_ID
            ) {
                return@setFragmentResultListener
            }
            if (result.getString(TextInputBottomSheet.RESULT_KEY) != TextInputBottomSheet.RESULT_SAVED) {
                return@setFragmentResultListener
            }
            parseReservoirCapacity(result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty())
                ?.let { capacityMl -> reservoirCapacityMl = capacityMl }
        }
    }

    private fun showReservoirCapacityEditor() {
        TextInputBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.device_dosing_detail_container_volume),
            label = getString(R.string.device_dosing_detail_container_volume_input_label),
            hint = getString(R.string.device_dosing_detail_container_volume_hint),
            initialValue = formatReservoirCapacityInput(reservoirCapacityMl),
            saveText = getString(R.string.common_save),
            cancelText = getString(R.string.common_cancel),
            required = true,
            requiredMessage = getString(R.string.device_dosing_detail_container_volume_required),
            requestKey = RESERVOIR_CAPACITY_REQUEST_KEY,
            payloadId = RESERVOIR_CAPACITY_PAYLOAD_ID,
            maxLength = RESERVOIR_CAPACITY_MAX_LENGTH,
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            disableSaveWhenUnchanged = true,
            requestFocus = true
        )
    }

    private companion object {
        const val STATE_RESERVOIR_CAPACITY_ML = "reservoir_capacity_ml"
        const val STATE_DAILY_DOSE_MICROLITERS = "daily_dose_microliters"
        const val STATE_SINGLE_DOSE_START_TIME_MS = "single_dose_start_time_ms"
        const val STATE_HOURLY_START_TIME_MS = "hourly_start_time_ms"
        const val STATE_CUSTOM_PERIODS_DRAFT = "custom_periods_draft"
        const val STATE_TIMER_DOSES_DRAFT = "timer_doses_draft"
        const val STATE_SELECTED_SCHEDULE_MODE = "selected_schedule_mode"
        const val STATE_SCHEDULE_ENABLED = "schedule_enabled"
        const val STATE_SCHEDULE_WEEKDAYS = "schedule_weekdays"
        const val DAILY_DOSE_REQUEST_KEY = "dosing_daily_dose_input"
        const val RESERVOIR_CAPACITY_REQUEST_KEY = "dosing_reservoir_capacity_input"
        const val RESERVOIR_CAPACITY_PAYLOAD_ID = "reservoir_capacity"
        const val RESERVOIR_CAPACITY_MAX_LENGTH = 7
        const val DAILY_DOSE_INPUT_MAX_LENGTH = 12
        const val DEFAULT_RESERVOIR_CAPACITY_ML = 450.0
        const val DEFAULT_DAILY_DOSE_MICROLITERS = 0L
        const val DEFAULT_SINGLE_DOSE_START_TIME_MS = 0L
        const val DEFAULT_HOURLY_START_TIME_MS = 0L
        const val DEFAULT_SCHEDULE_ENABLED = true
    }
}

private fun DeviceDosingChannelMenuFragment.formatReservoirCapacityInput(
    capacityMl: Double
): String {
    val locale = resources.configuration.locales[0]
    return NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }.format(capacityMl)
}

private fun parseReservoirCapacity(rawValue: String): Double? = rawValue
    .trim()
    .replace(',', '.')
    .toDoubleOrNull()
    ?.takeIf { capacityMl -> capacityMl > 0.0 }
