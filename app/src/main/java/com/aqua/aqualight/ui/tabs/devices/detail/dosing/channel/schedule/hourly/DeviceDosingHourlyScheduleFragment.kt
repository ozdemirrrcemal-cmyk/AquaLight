package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.hourly

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.bottomsheet.AquaTimePickerBottomSheet
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.common.DeviceDosingChannelDestinationFragment

/** Draft editor for the 24-dose hourly schedule of one centrally identified Dosing channel. */
class DeviceDosingHourlyScheduleFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingHourlyScheduleFragmentArgs by navArgs()
    private var startTimeMs by mutableLongStateOf(DEFAULT_START_TIME_MS)

    override val destinationTitle: String
        get() = getString(R.string.device_dosing_detail_schedule_hourly)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (
            args.dailyDoseMicroliters < 0L ||
            !DeviceDosingHourlyScheduleContract.isValidStartTime(args.startTimeMs)
        ) {
            findNavController().navigateUp()
            return
        }

        val restoredStartTimeMs = savedInstanceState
            ?.takeIf { state -> state.containsKey(STATE_START_TIME_MS) }
            ?.getLong(STATE_START_TIME_MS)
            ?.takeIf(DeviceDosingHourlyScheduleContract::isValidStartTime)
            ?: args.startTimeMs
        startTimeMs = restoredStartTimeMs
        setupTimePickerResult()
        setupSelectedPump(
            view = view,
            deviceUid = args.deviceUid,
            slotId = args.slotId,
            pumpCount = args.pumpCount,
            channelNumber = args.channelNumber
        )
        view.findViewById<ComposeView>(R.id.channelDetailContent).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DeviceDosingHourlyScheduleScreen(
                    state = DeviceDosingHourlyScheduleUiState(
                        dailyDoseMicroliters = args.dailyDoseMicroliters,
                        startTimeMs = startTimeMs
                    ),
                    onStartTimeClick = ::showTimePicker,
                    onSaveClick = ::saveDraft
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(STATE_START_TIME_MS, startTimeMs)
        super.onSaveInstanceState(outState)
    }

    private fun setupTimePickerResult() {
        childFragmentManager.setFragmentResultListener(
            TIME_PICKER_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(AquaTimePickerBottomSheet.RESULT_PAYLOAD_ID) != args.slotId ||
                result.getString(AquaTimePickerBottomSheet.RESULT_KEY) !=
                AquaTimePickerBottomSheet.RESULT_SELECTED ||
                result.getString(AquaTimePickerBottomSheet.RESULT_SELECTION_MODE) !=
                AquaTimePickerBottomSheet.SelectionMode.TIME_OF_DAY.name
            ) {
                return@setFragmentResultListener
            }
            val minutesOfDay = result.getInt(
                AquaTimePickerBottomSheet.RESULT_MINUTES_OF_DAY,
                INVALID_MINUTES_OF_DAY
            )
            if (minutesOfDay in 0 until DeviceDosingHourlyScheduleContract.MINUTES_PER_DAY) {
                startTimeMs = DeviceDosingHourlyScheduleContract.startTimeMs(minutesOfDay)
            }
        }
    }

    private fun showTimePicker() {
        val minutesOfDay = DeviceDosingHourlyScheduleContract.minutesOfDay(startTimeMs)
        AquaTimePickerBottomSheet.show(
            fragmentManager = childFragmentManager,
            request = AquaTimePickerBottomSheet.Request(
                title = getString(R.string.device_dosing_hourly_start_time_picker_title),
                message = getString(R.string.device_dosing_hourly_start_time_picker_message),
                initialHour = minutesOfDay / DeviceDosingHourlyScheduleContract.MINUTES_PER_HOUR,
                initialMinute = minutesOfDay % DeviceDosingHourlyScheduleContract.MINUTES_PER_HOUR,
                selectionMode = AquaTimePickerBottomSheet.SelectionMode.TIME_OF_DAY,
                confirmText = getString(R.string.device_dosing_hourly_start_time_picker_confirm),
                cancelText = getString(R.string.common_cancel),
                resultTarget = AquaTimePickerBottomSheet.ResultTarget(
                    requestKey = TIME_PICKER_REQUEST_KEY,
                    payloadId = args.slotId
                )
            )
        )
    }

    private fun saveDraft() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceDosingHourlyScheduleFragment) return
        parentFragmentManager.setFragmentResult(
            DeviceDosingHourlyScheduleContract.RESULT_REQUEST_KEY,
            bundleOf(
                DeviceDosingHourlyScheduleContract.RESULT_KEY to
                    DeviceDosingHourlyScheduleContract.RESULT_SAVED,
                DeviceDosingHourlyScheduleContract.RESULT_SLOT_ID to args.slotId,
                DeviceDosingHourlyScheduleContract.RESULT_START_TIME_MS to startTimeMs
            )
        )
        navController.navigateUp()
    }

    private companion object {
        const val STATE_START_TIME_MS = "hourly_schedule_start_time_ms"
        const val TIME_PICKER_REQUEST_KEY = "hourly_schedule_time_picker"
        const val DEFAULT_START_TIME_MS = 0L
        const val INVALID_MINUTES_OF_DAY = -1
    }
}
