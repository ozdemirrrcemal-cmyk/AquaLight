package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.dosing.DosingCalibrationDataStoreManager
import com.aqua.aqualight.databinding.FragmentDeviceDosingChannelSettingsBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceDosingChannelSettingsFragment :
    Fragment(R.layout.fragment_device_dosing_channel_settings) {

    private var _binding: FragmentDeviceDosingChannelSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var calibrationDataStoreManager: DosingCalibrationDataStoreManager

    private val channelIndex: Int
        get() = requireArguments().getInt(
            ARG_CHANNEL_INDEX,
            0
        ).coerceIn(
            minimumValue = 0,
            maximumValue = 3
        )

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

        _binding =
            FragmentDeviceDosingChannelSettingsBinding.bind(
                view
            )

        calibrationDataStoreManager =
            DosingCalibrationDataStoreManager(
                context = requireContext()
            )

        bindHeaderActions()
        bindStaticPreview()
        bindCalibrationState()
        bindSelectedPumpIndicator()
        bindClicks()

        selectDosingMode(
            mode = selectedMode
        )

        renderWeekDays(
            weekDays = List(
                size = 7
            ) {
                false
            }
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
            "Not set"

        makeDailyDoseCardDisplayOnly()
    }

    private fun makeDailyDoseCardDisplayOnly() {
        binding.cardDailyDose.setOnClickListener(
            null
        )

        binding.cardDailyDose.isClickable =
            false

        binding.cardDailyDose.isFocusable =
            false
    }

    private fun bindCalibrationState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                calibrationDataStoreManager.observeCalibration(
                    deviceId = deviceId,
                    channelIndex = channelIndex
                ).collect { calibration ->
                    binding.tvLastCalibrated.text =
                        if (calibration == null) {
                            "Last calibrated: Not calibrated"
                        } else {
                            "Last calibrated: ${
                                formatCalibrationDate(
                                    millis = calibration.lastCalibratedAtMillis
                                )
                            }"
                        }
                }
            }
        }
    }

    private fun formatCalibrationDate(
        millis: Long
    ): String {
        return SimpleDateFormat(
            "dd MMM yyyy, HH:mm",
            Locale.getDefault()
        ).format(
            Date(millis)
        )
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

        binding.rowModeSingle.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.SINGLE
            )

            openSingleModeSettings()
        }

        binding.radioModeSingle.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.SINGLE
            )

            openSingleModeSettings()
        }

        binding.rowModeHourly.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.HOURLY_24
            )

            openHourly24ModeSettings()
        }

        binding.radioModeHourly.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.HOURLY_24
            )

            openHourly24ModeSettings()
        }

        binding.rowModeCustomPeriods.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.CUSTOM_PERIODS
            )

            openCustomPeriodsSettings()
        }

        binding.radioModeCustomPeriods.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.CUSTOM_PERIODS
            )

            openCustomPeriodsSettings()
        }

        binding.rowModeTimer.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.TIMER
            )

            showSnackBar(
                message = "Timer settings screen will be added next.",
                type = BaseActivity.SnackType.NORMAL
            )
        }

        binding.radioModeTimer.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.TIMER
            )

            showSnackBar(
                message = "Timer settings screen will be added next.",
                type = BaseActivity.SnackType.NORMAL
            )
        }

        binding.rowEveryDay.setOnClickListener {
            binding.radioEveryDay.isChecked =
                true

            showSnackBar(
                message = "Recurrence selection will be added here.",
                type = BaseActivity.SnackType.NORMAL
            )
        }

        binding.rowContainerVolume.setOnClickListener {
            showSnackBar(
                message = "Container volume editor will open here.",
                type = BaseActivity.SnackType.NORMAL
            )
        }

        binding.btnCalibrate.setOnClickListener {
            openCalibrationWizard()
        }

        binding.btnManualDosing.setOnClickListener {
            showSnackBar(
                message = "Manual dosing will open for Channel $channelNumber.",
                type = BaseActivity.SnackType.NORMAL
            )
        }

        binding.btnResetChannel.setOnClickListener {
            showSnackBar(
                message = "Reset confirmation will open for Channel $channelNumber.",
                type = BaseActivity.SnackType.NORMAL
            )
        }
    }

    private fun renderWeekDays(
        weekDays: List<Boolean>
    ) {
        val chips =
            listOf(
                binding.chipDayMon,
                binding.chipDayTue,
                binding.chipDayWed,
                binding.chipDayThu,
                binding.chipDayFri,
                binding.chipDaySat,
                binding.chipDaySun
            )

        chips.forEachIndexed { index, chip ->
            val selected =
                weekDays.getOrNull(
                    index = index
                ) == true

            chip.alpha =
                if (selected) {
                    1f
                } else {
                    0.35f
                }

            chip.setBackgroundColor(
                Color.parseColor(
                    if (selected) {
                        "#702536"
                    } else {
                        "#24314F"
                    }
                )
            )
        }
    }

    private fun openSingleModeSettings() {
        findNavController().navigate(
            R.id.action_deviceDosingChannelSettingsFragment_to_deviceDosingSingleModeSettingsFragment,
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

    private fun openHourly24ModeSettings() {
        findNavController().navigate(
            R.id.action_deviceDosingChannelSettingsFragment_to_deviceDosingHourly24ModeSettingsFragment,
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

    private fun openCustomPeriodsSettings() {
        findNavController().navigate(
            R.id.action_deviceDosingChannelSettingsFragment_to_deviceDosingCustomPeriodsSettingsFragment,
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
        selectedMode =
            mode

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
        val contentAlpha =
            if (enabled) {
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
            true

        makeDailyDoseCardDisplayOnly()

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

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType = BaseActivity.SnackType.NORMAL
    ) {
        (activity as? BaseActivity)?.showSnackBar(
            message = message,
            type = type
        )
    }

    override fun onDestroyView() {
        _binding =
            null

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