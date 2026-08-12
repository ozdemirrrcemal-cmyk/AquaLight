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
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.hourly.DeviceDosingHourlyScheduleContract
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.single.DeviceDosingSingleScheduleContract
import java.text.NumberFormat

/** UI-only child destination for one Dosing detail menu entry. */
class DeviceDosingChannelMenuFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingChannelMenuFragmentArgs by navArgs()
    private val menuItem: DosingDetailMenuItem?
        get() = DosingDetailMenuItem.fromRouteKey(args.menuKey)

    private var reservoirCapacityMl by mutableDoubleStateOf(DEFAULT_RESERVOIR_CAPACITY_ML)
    private var dailyDoseMicroliters by mutableLongStateOf(DEFAULT_DAILY_DOSE_MICROLITERS)
    private var singleDoseStartTimeMs by mutableLongStateOf(DEFAULT_SINGLE_DOSE_START_TIME_MS)
    private var hourlyStartTimeMs by mutableLongStateOf(DEFAULT_HOURLY_START_TIME_MS)
    private var selectedScheduleMode by mutableStateOf(DosingPlanScheduleMode.SINGLE)

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
        reservoirCapacityMl = savedInstanceState?.getDouble(
            STATE_RESERVOIR_CAPACITY_ML,
            DEFAULT_RESERVOIR_CAPACITY_ML
        ) ?: DEFAULT_RESERVOIR_CAPACITY_ML
        dailyDoseMicroliters = savedInstanceState?.getLong(
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
        selectedScheduleMode = savedInstanceState
            ?.getString(STATE_SELECTED_SCHEDULE_MODE)
            ?.let { savedMode ->
                DosingPlanScheduleMode.entries.firstOrNull { mode -> mode.name == savedMode }
            }
            ?: DosingPlanScheduleMode.SINGLE
        if (item == DosingDetailMenuItem.RESERVOIR) {
            setupReservoirCapacityResult()
        }
        if (item == DosingDetailMenuItem.DOSING_PLAN) {
            setupSingleScheduleResult()
            setupHourlyScheduleResult()
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

    private fun setupMenuContent(view: View, item: DosingDetailMenuItem) {
        view.findViewById<ComposeView>(R.id.channelDetailContent).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DeviceDosingChannelMenuScreen(
                    item = item,
                    dailyDoseMicroliters = dailyDoseMicroliters,
                    selectedScheduleMode = selectedScheduleMode,
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
                    }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putDouble(STATE_RESERVOIR_CAPACITY_ML, reservoirCapacityMl)
        outState.putLong(STATE_DAILY_DOSE_MICROLITERS, dailyDoseMicroliters)
        outState.putLong(STATE_SINGLE_DOSE_START_TIME_MS, singleDoseStartTimeMs)
        outState.putLong(STATE_HOURLY_START_TIME_MS, hourlyStartTimeMs)
        outState.putString(STATE_SELECTED_SCHEDULE_MODE, selectedScheduleMode.name)
        super.onSaveInstanceState(outState)
    }

    private fun setupSingleScheduleResult() {
        parentFragmentManager.setFragmentResultListener(
            DeviceDosingSingleScheduleContract.RESULT_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(DeviceDosingSingleScheduleContract.RESULT_KEY) !=
                DeviceDosingSingleScheduleContract.RESULT_SAVED ||
                result.getString(DeviceDosingSingleScheduleContract.RESULT_SLOT_ID) != args.slotId
            ) {
                return@setFragmentResultListener
            }
            val resultStartTimeMs = result.getLong(
                DeviceDosingSingleScheduleContract.RESULT_START_TIME_MS,
                INVALID_START_TIME_MS
            )
            if (DeviceDosingSingleScheduleContract.isValidStartTime(resultStartTimeMs)) {
                singleDoseStartTimeMs =
                    DeviceDosingSingleScheduleContract.minuteAlignedStartTime(resultStartTimeMs)
                selectedScheduleMode = DosingPlanScheduleMode.SINGLE
            }
        }
    }

    private fun setupHourlyScheduleResult() {
        parentFragmentManager.setFragmentResultListener(
            DeviceDosingHourlyScheduleContract.RESULT_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(DeviceDosingHourlyScheduleContract.RESULT_KEY) !=
                DeviceDosingHourlyScheduleContract.RESULT_SAVED ||
                result.getString(DeviceDosingHourlyScheduleContract.RESULT_SLOT_ID) != args.slotId
            ) {
                return@setFragmentResultListener
            }
            val resultStartTimeMs = result.getLong(
                DeviceDosingHourlyScheduleContract.RESULT_START_TIME_MS,
                INVALID_START_TIME_MS
            )
            if (DeviceDosingHourlyScheduleContract.isValidStartTime(resultStartTimeMs)) {
                hourlyStartTimeMs =
                    DeviceDosingHourlyScheduleContract.minuteAlignedStartTime(resultStartTimeMs)
                selectedScheduleMode = DosingPlanScheduleMode.HOURLY
            }
        }
    }

    private fun openScheduleEditor(mode: DosingPlanScheduleMode) {
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
                    dailyDoseMicroliters = dailyDoseMicroliters,
                    startTimeMs = singleDoseStartTimeMs
                )
            DosingPlanScheduleMode.HOURLY -> DeviceDosingChannelMenuFragmentDirections
                .actionDeviceDosingChannelMenuFragmentToDeviceDosingHourlyScheduleFragment(
                    deviceUid = args.deviceUid,
                    slotId = args.slotId,
                    channelTitle = args.channelTitle,
                    pumpCount = args.pumpCount,
                    channelNumber = args.channelNumber,
                    dailyDoseMicroliters = dailyDoseMicroliters,
                    startTimeMs = hourlyStartTimeMs
                )
            DosingPlanScheduleMode.CUSTOM,
            DosingPlanScheduleMode.TIMER -> return
        }
        navController.navigate(direction)
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

    private fun formatReservoirCapacityInput(capacityMl: Double): String {
        val locale = resources.configuration.locales[0]
        return NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }.format(capacityMl)
    }

    private fun parseReservoirCapacity(rawValue: String): Double? {
        return rawValue
            .trim()
            .replace(',', '.')
            .toDoubleOrNull()
            ?.takeIf { capacityMl -> capacityMl > 0.0 }
    }

    private companion object {
        const val STATE_RESERVOIR_CAPACITY_ML = "reservoir_capacity_ml"
        const val STATE_DAILY_DOSE_MICROLITERS = "daily_dose_microliters"
        const val STATE_SINGLE_DOSE_START_TIME_MS = "single_dose_start_time_ms"
        const val STATE_HOURLY_START_TIME_MS = "hourly_start_time_ms"
        const val STATE_SELECTED_SCHEDULE_MODE = "selected_schedule_mode"
        const val RESERVOIR_CAPACITY_REQUEST_KEY = "dosing_reservoir_capacity_input"
        const val RESERVOIR_CAPACITY_PAYLOAD_ID = "reservoir_capacity"
        const val RESERVOIR_CAPACITY_MAX_LENGTH = 7
        const val DEFAULT_RESERVOIR_CAPACITY_ML = 450.0
        const val DEFAULT_DAILY_DOSE_MICROLITERS = 0L
        const val DEFAULT_SINGLE_DOSE_START_TIME_MS = 0L
        const val DEFAULT_HOURLY_START_TIME_MS = 0L
        const val INVALID_START_TIME_MS = -1L
    }
}
