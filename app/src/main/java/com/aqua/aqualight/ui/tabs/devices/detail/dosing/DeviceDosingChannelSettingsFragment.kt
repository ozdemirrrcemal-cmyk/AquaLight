package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentDeviceDosingChannelSettingsBinding

class DeviceDosingChannelSettingsFragment :
    Fragment(R.layout.fragment_device_dosing_channel_settings) {

    private var _binding: FragmentDeviceDosingChannelSettingsBinding? = null
    private val binding get() = _binding!!

    private val channelIndex: Int
        get() = requireArguments().getInt(ARG_CHANNEL_INDEX, 0)
            .coerceIn(0, 3)

    private val channelNumber: Int
        get() = channelIndex + 1

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    private val deviceIp: String
        get() = requireArguments().getString(ARG_DEVICE_IP).orEmpty()

    private val deviceTitle: String
        get() = requireArguments().getString(ARG_DEVICE_TITLE).orEmpty()

    private var selectedMode: DosingMode =
        DosingMode.SINGLE

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentDeviceDosingChannelSettingsBinding.bind(
            view
        )

        bindHeaderActions()
        bindStaticPreview()
        bindSelectedPumpIndicator()
        bindClicks()

        selectDosingMode(
            mode = selectedMode
        )

        updateScheduleEnabledState(
            enabled = binding.switchScheduleEnabled.isChecked
        )
    }

    private fun bindHeaderActions() {
        binding.tvChannelSettingsTitle.text =
            "Channel $channelNumber"

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun bindStaticPreview() {
        binding.tvDailyDoseValue.text =
            "0 ml"

        binding.tvLastCalibrated.text =
            "Last calibrated: Not calibrated"

        binding.tvContainerVolumeValue.text =
            "450.0 ml"
    }

    private fun bindSelectedPumpIndicator() {
        binding.selectedIndicatorPump1.visibility =
            if (channelIndex == 0) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.selectedIndicatorPump2.visibility =
            if (channelIndex == 1) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.selectedIndicatorPump3.visibility =
            if (channelIndex == 2) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.selectedIndicatorPump4.visibility =
            if (channelIndex == 3) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun bindClicks() {
        binding.switchScheduleEnabled.setOnCheckedChangeListener { _, isChecked ->
            updateScheduleEnabledState(
                enabled = isChecked
            )
        }

        binding.cardDailyDose.setOnClickListener {
            showComingNext(
                message = "Daily dose editor will open for Channel $channelNumber."
            )
        }

        binding.rowModeSingle.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.SINGLE
            )
        }

        binding.radioModeSingle.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.SINGLE
            )
        }

        binding.rowModeHourly.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.HOURLY_24
            )
        }

        binding.radioModeHourly.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.HOURLY_24
            )
        }

        binding.rowModeCustomPeriods.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.CUSTOM_PERIODS
            )
        }

        binding.radioModeCustomPeriods.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.CUSTOM_PERIODS
            )
        }

        binding.rowModeTimer.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.TIMER
            )
        }

        binding.radioModeTimer.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.TIMER
            )
        }

        binding.rowEveryDay.setOnClickListener {
            binding.radioEveryDay.isChecked =
                true

            showComingNext(
                message = "Recurrence selection will be added here."
            )
        }

        binding.rowContainerVolume.setOnClickListener {
            showComingNext(
                message = "Container volume editor will open here."
            )
        }

        binding.btnCalibrate.setOnClickListener {
            openCalibrationWizard()
        }

        binding.btnManualDosing.setOnClickListener {
            showComingNext(
                message = "Manual dosing will open for Channel $channelNumber."
            )
        }

        binding.btnResetChannel.setOnClickListener {
            showComingNext(
                message = "Reset confirmation will open for Channel $channelNumber."
            )
        }
    }

    private fun openCalibrationWizard() {
        findNavController().navigate(
            R.id.action_deviceDosingChannelSettingsFragment_to_deviceDosingCalibrationFragment,
            Bundle().apply {
                putLong(
                    ARG_DEVICE_ID,
                    deviceId
                )

                putString(
                    ARG_DEVICE_IP,
                    deviceIp
                )

                putString(
                    ARG_DEVICE_TITLE,
                    deviceTitle
                )

                putInt(
                    ARG_CHANNEL_INDEX,
                    channelIndex
                )
            }
        )
    }

    private fun selectDosingMode(
        mode: DosingMode
    ) {
        selectedMode = mode

        binding.radioModeSingle.isChecked =
            mode == DosingMode.SINGLE

        binding.radioModeHourly.isChecked =
            mode == DosingMode.HOURLY_24

        binding.radioModeCustomPeriods.isChecked =
            mode == DosingMode.CUSTOM_PERIODS

        binding.radioModeTimer.isChecked =
            mode == DosingMode.TIMER
    }

    private fun updateScheduleEnabledState(
        enabled: Boolean
    ) {
        val contentAlpha = if (enabled) {
            1f
        } else {
            0.45f
        }

        binding.cardDailyDose.alpha =
            contentAlpha

        binding.cardDosingSchedule.alpha =
            contentAlpha

        binding.cardRecurrence.alpha =
            contentAlpha

        binding.cardMissedDoseCompensation.alpha =
            contentAlpha

        binding.cardDailyDose.isEnabled =
            enabled

        binding.rowModeSingle.isEnabled =
            enabled

        binding.rowModeHourly.isEnabled =
            enabled

        binding.rowModeCustomPeriods.isEnabled =
            enabled

        binding.rowModeTimer.isEnabled =
            enabled

        binding.rowEveryDay.isEnabled =
            enabled

        binding.switchMissedDoseCompensation.isEnabled =
            enabled
    }

    private fun showComingNext(
        message: String
    ) {
        (activity as? BaseActivity)?.showSnackBar(
            message = message,
            type = BaseActivity.SnackType.NORMAL
        )
    }

    override fun onDestroyView() {
        _binding = null

        super.onDestroyView()
    }

    private enum class DosingMode {
        SINGLE,
        HOURLY_24,
        CUSTOM_PERIODS,
        TIMER
    }

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_DEVICE_IP = "deviceIp"
        private const val ARG_DEVICE_TITLE = "deviceTitle"
        private const val ARG_CHANNEL_INDEX = "channelIndex"

        fun newInstance(
            deviceId: Long,
            deviceIp: String,
            deviceTitle: String,
            channelIndex: Int
        ): DeviceDosingChannelSettingsFragment {
            return DeviceDosingChannelSettingsFragment().apply {
                arguments = Bundle().apply {
                    putLong(
                        ARG_DEVICE_ID,
                        deviceId
                    )

                    putString(
                        ARG_DEVICE_IP,
                        deviceIp
                    )

                    putString(
                        ARG_DEVICE_TITLE,
                        deviceTitle
                    )

                    putInt(
                        ARG_CHANNEL_INDEX,
                        channelIndex.coerceIn(
                            minimumValue = 0,
                            maximumValue = 3
                        )
                    )
                }
            }
        }
    }
}