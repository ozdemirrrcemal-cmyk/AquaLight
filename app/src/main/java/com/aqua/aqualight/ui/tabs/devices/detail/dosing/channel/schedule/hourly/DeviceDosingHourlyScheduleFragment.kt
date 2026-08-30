package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.hourly

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.common.bottomsheet.AquaTimePickerBottomSheet
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.common.DeviceDosingChannelDestinationFragment

/** Draft editor for firmware Hourly24: one selected minute repeated in all 24 local hours. */
class DeviceDosingHourlyScheduleFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingHourlyScheduleFragmentArgs by navArgs()
    private var minuteOfHour by mutableIntStateOf(DEFAULT_MINUTE_OF_HOUR)

    override val destinationTitle: String
        get() = getString(R.string.device_dosing_detail_schedule_hourly)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (
            args.dailyDoseMicroliters < 0L ||
            !DeviceDosingHourlyScheduleContract.isValidMinuteOfHour(args.minuteOfHour)
        ) {
            findNavController().navigateUp()
            return
        }

        val restoredMinuteOfHour = savedInstanceState
            ?.takeIf { state -> state.containsKey(STATE_MINUTE_OF_HOUR) }
            ?.getInt(STATE_MINUTE_OF_HOUR)
            ?.takeIf(DeviceDosingHourlyScheduleContract::isValidMinuteOfHour)
            ?: args.minuteOfHour
        minuteOfHour = restoredMinuteOfHour
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
                        minuteOfHour = minuteOfHour
                    ),
                    onMinuteOfHourClick = ::showMinutePicker,
                    onSaveClick = ::saveDraft
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_MINUTE_OF_HOUR, minuteOfHour)
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
                AquaTimePickerBottomSheet.SelectionMode.MINUTE_OF_HOUR.name
            ) {
                return@setFragmentResultListener
            }
            val selectedMinute = result.getInt(
                AquaTimePickerBottomSheet.RESULT_MINUTE,
                INVALID_MINUTE_OF_HOUR
            )
            if (DeviceDosingHourlyScheduleContract.isValidMinuteOfHour(selectedMinute)) {
                minuteOfHour = selectedMinute
            }
        }
    }

    private fun showMinutePicker() {
        AquaTimePickerBottomSheet.show(
            fragmentManager = childFragmentManager,
            request = AquaTimePickerBottomSheet.Request(
                title = getString(R.string.device_dosing_hourly_minute_of_hour_picker_title),
                message = getString(R.string.device_dosing_hourly_minute_of_hour_picker_message),
                initialHour = 0,
                initialMinute = minuteOfHour,
                selectionMode = AquaTimePickerBottomSheet.SelectionMode.MINUTE_OF_HOUR,
                confirmText = getString(R.string.device_dosing_hourly_minute_of_hour_picker_confirm),
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
                DeviceDosingHourlyScheduleContract.RESULT_MINUTE_OF_HOUR to minuteOfHour
            )
        )
        navController.navigateUp()
    }

    private companion object {
        const val STATE_MINUTE_OF_HOUR = "hourly_schedule_minute_of_hour"
        const val TIME_PICKER_REQUEST_KEY = "hourly_schedule_minute_picker"
        const val DEFAULT_MINUTE_OF_HOUR = 0
        const val INVALID_MINUTE_OF_HOUR = -1
    }
}
