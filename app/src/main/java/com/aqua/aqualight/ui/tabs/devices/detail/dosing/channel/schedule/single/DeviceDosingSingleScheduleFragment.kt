package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.single

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
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.DeviceDosingChannelDestinationFragment

/** Single-dose draft editor for one centrally identified Dosing channel. */
class DeviceDosingSingleScheduleFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingSingleScheduleFragmentArgs by navArgs()
    private var startTimeMs by mutableLongStateOf(DEFAULT_START_TIME_MS)

    override val destinationTitle: String
        get() = getString(R.string.device_dosing_single_title)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (
            args.dailyDoseMicroliters < 0L ||
            !DeviceDosingSingleScheduleContract.isValidStartTime(args.startTimeMs)
        ) {
            findNavController().navigateUp()
            return
        }

        val restoredStartTimeMs = savedInstanceState
            ?.takeIf { state -> state.containsKey(STATE_START_TIME_MS) }
            ?.getLong(STATE_START_TIME_MS)
            ?.takeIf(DeviceDosingSingleScheduleContract::isValidStartTime)
            ?: args.startTimeMs
        startTimeMs = DeviceDosingSingleScheduleContract.minuteAlignedStartTime(restoredStartTimeMs)
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
                DeviceDosingSingleScheduleScreen(
                    state = DeviceDosingSingleScheduleUiState(
                        dailyDoseMicroliters = args.dailyDoseMicroliters,
                        startTimeMs = startTimeMs
                    ),
                    onTimeClick = ::showTimePicker,
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
            if (result.getString(AquaTimePickerBottomSheet.RESULT_PAYLOAD_ID) != args.slotId) {
                return@setFragmentResultListener
            }
            if (result.getString(AquaTimePickerBottomSheet.RESULT_KEY) !=
                AquaTimePickerBottomSheet.RESULT_SELECTED
            ) {
                return@setFragmentResultListener
            }
            val minutesOfDay = result.getInt(
                AquaTimePickerBottomSheet.RESULT_MINUTES_OF_DAY,
                INVALID_MINUTES_OF_DAY
            )
            if (minutesOfDay in 0 until DeviceDosingSingleScheduleContract.MINUTES_PER_DAY) {
                startTimeMs = DeviceDosingSingleScheduleContract.startTimeMs(minutesOfDay)
            }
        }
    }

    private fun showTimePicker() {
        val minutesOfDay = DeviceDosingSingleScheduleContract.minutesOfDay(startTimeMs)
        AquaTimePickerBottomSheet.show(
            fragmentManager = childFragmentManager,
            request = AquaTimePickerBottomSheet.Request(
                title = getString(R.string.device_dosing_single_time_picker_title),
                message = getString(R.string.device_dosing_single_time_picker_message),
                initialHour = minutesOfDay / MINUTES_PER_HOUR,
                initialMinute = minutesOfDay % MINUTES_PER_HOUR,
                confirmText = getString(R.string.device_dosing_single_time_picker_confirm),
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
        if (navController.currentDestination?.id != R.id.deviceDosingSingleScheduleFragment) {
            return
        }
        parentFragmentManager.setFragmentResult(
            DeviceDosingSingleScheduleContract.RESULT_REQUEST_KEY,
            bundleOf(
                DeviceDosingSingleScheduleContract.RESULT_KEY to
                    DeviceDosingSingleScheduleContract.RESULT_SAVED,
                DeviceDosingSingleScheduleContract.RESULT_SLOT_ID to args.slotId,
                DeviceDosingSingleScheduleContract.RESULT_START_TIME_MS to startTimeMs
            )
        )
        navController.navigateUp()
    }

    private companion object {
        const val STATE_START_TIME_MS = "single_schedule_start_time_ms"
        const val TIME_PICKER_REQUEST_KEY = "single_schedule_time_picker"
        const val DEFAULT_START_TIME_MS = 0L
        const val INVALID_MINUTES_OF_DAY = -1
        const val MINUTES_PER_HOUR = 60
    }
}
