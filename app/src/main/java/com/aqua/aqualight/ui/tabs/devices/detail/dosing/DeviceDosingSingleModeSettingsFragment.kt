package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.dosing.EspDosingCommandClient
import com.aqua.aqualight.data.devices.dosing.EspDosingSettingsClient
import com.aqua.aqualight.databinding.FragmentDeviceDosingSingleModeSettingsBinding
import kotlinx.coroutines.launch
import java.util.Locale

class DeviceDosingSingleModeSettingsFragment :
    Fragment(R.layout.fragment_device_dosing_single_mode_settings) {

    private var _binding: FragmentDeviceDosingSingleModeSettingsBinding? = null
    private val binding get() = _binding!!

    private var selectedHour: Int = 0
    private var selectedMinute: Int = 0
    private var selectedWeekDays: List<Boolean> =
        List(
            size = 7
        ) {
            true
        }

    private var timerIndexForSave: Int? = null
    private var channelGpioPwmForSave: String = "-"
    private var saveInProgress: Boolean = false

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

    private val deviceIp: String
        get() = requireArguments().getString(ARG_DEVICE_IP).orEmpty()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentDeviceDosingSingleModeSettingsBinding.bind(
                view
            )

        bindHeader()
        bindSelectedPumpIndicator()
        bindClicks()
        loadCurrentSingleModeValues()
    }

    private fun bindHeader() {
        binding.tvTitle.text =
            "Single dose"

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
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
        binding.btnCancel.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.rowStartTime.setOnClickListener {
            showTimePicker()
        }

        binding.btnSave.setOnClickListener {
            saveSingleMode()
        }
    }

    private fun loadCurrentSingleModeValues() {
        viewLifecycleOwner.lifecycleScope.launch {
            val snapshot =
                EspDosingSettingsClient.readChannelSettingsSnapshot(
                    deviceIp = deviceIp,
                    channelIndex = channelIndex
                )

            if (_binding == null || snapshot == null) {
                return@launch
            }

            val channel =
                snapshot.channel

            val timer =
                snapshot.timer

            channelGpioPwmForSave =
                channel.gpioPwm

            timerIndexForSave =
                timer?.timerIndex ?: channelIndex

            if (timer != null) {
                val doseForSingleMode =
                    if (timer.count > 1) {
                        timer.doseMl * timer.count
                    } else {
                        timer.doseMl
                    }

                if (doseForSingleMode > 0f) {
                    binding.etSingleDoseMl.setText(
                        formatDoseInput(
                            value = doseForSingleMode
                        )
                    )
                }

                applyStartTime(
                    value = timer.timeStart
                )

                val hasAnySelectedDay =
                    timer.weekDays.any { selected ->
                        selected
                    }

                selectedWeekDays =
                    if (hasAnySelectedDay) {
                        timer.weekDays
                    } else {
                        List(
                            size = 7
                        ) {
                            true
                        }
                    }
            } else {
                applyStartTime(
                    value = "00:00"
                )

                selectedWeekDays =
                    List(
                        size = 7
                    ) {
                        true
                    }
            }
        }
    }

    private fun showTimePicker() {
        hideKeyboard()

        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                selectedHour =
                    hourOfDay.coerceIn(
                        minimumValue = 0,
                        maximumValue = 23
                    )

                selectedMinute =
                    minute.coerceIn(
                        minimumValue = 0,
                        maximumValue = 59
                    )

                binding.tvStartTimeValue.text =
                    formatTime(
                        hour = selectedHour,
                        minute = selectedMinute
                    )
            },
            selectedHour,
            selectedMinute,
            true
        ).show()
    }

    private fun saveSingleMode() {
        if (saveInProgress) {
            return
        }

        hideKeyboard()

        val doseMl =
            binding.etSingleDoseMl.text
                ?.toString()
                ?.trim()
                ?.replace(
                    oldValue = ",",
                    newValue = "."
                )
                ?.toFloatOrNull()

        if (
            doseMl == null ||
            doseMl <= 0f
        ) {
            showComingNext(
                message = "Please enter a valid dose amount."
            )
            return
        }

        val safeTimerIndex =
            timerIndexForSave ?: channelIndex

        val safeGpioPwm =
            channelGpioPwmForSave.trim()

        if (
            safeGpioPwm.isBlank() ||
            safeGpioPwm == "-"
        ) {
            showComingNext(
                message = "Channel output could not be found."
            )
            return
        }

        val startTime =
            formatTime(
                hour = selectedHour,
                minute = selectedMinute
            )

        saveInProgress =
            true

        renderSavingState()

        viewLifecycleOwner.lifecycleScope.launch {
            val saved =
                EspDosingCommandClient.saveSingleModeSchedule(
                    deviceIp = deviceIp,
                    channelIndex = channelIndex,
                    timerIndex = safeTimerIndex,
                    channelGpioPwm = safeGpioPwm,
                    doseMl = doseMl,
                    startTime = startTime,
                    weekDays = selectedWeekDays,
                    enabled = true
                )

            saveInProgress =
                false

            if (_binding == null) {
                return@launch
            }

            renderSavingState()

            if (saved) {
                showComingNext(
                    message = "Single mode saved."
                )

                findNavController().navigateUp()
            } else {
                showComingNext(
                    message = "Single mode could not be saved to device."
                )
            }
        }
    }

    private fun renderSavingState() {
        binding.btnSave.isEnabled =
            !saveInProgress

        binding.btnCancel.isEnabled =
            !saveInProgress

        binding.rowStartTime.isEnabled =
            !saveInProgress

        binding.etSingleDoseMl.isEnabled =
            !saveInProgress

        binding.btnSave.alpha =
            if (saveInProgress) {
                0.55f
            } else {
                1f
            }

        binding.btnCancel.alpha =
            if (saveInProgress) {
                0.55f
            } else {
                1f
            }

        binding.btnSave.text =
            if (saveInProgress) {
                "Saving..."
            } else {
                "Save single mode"
            }
    }

    private fun applyStartTime(
        value: String
    ) {
        val safeValue =
            value.ifBlank {
                "00:00"
            }

        val parts =
            safeValue.split(":")

        selectedHour =
            parts.getOrNull(
                index = 0
            )?.toIntOrNull()
                ?.coerceIn(
                    minimumValue = 0,
                    maximumValue = 23
                ) ?: 0

        selectedMinute =
            parts.getOrNull(
                index = 1
            )?.toIntOrNull()
                ?.coerceIn(
                    minimumValue = 0,
                    maximumValue = 59
                ) ?: 0

        binding.tvStartTimeValue.text =
            formatTime(
                hour = selectedHour,
                minute = selectedMinute
            )
    }

    private fun formatTime(
        hour: Int,
        minute: Int
    ): String {
        return String.format(
            Locale.US,
            "%02d:%02d",
            hour.coerceIn(
                minimumValue = 0,
                maximumValue = 23
            ),
            minute.coerceIn(
                minimumValue = 0,
                maximumValue = 59
            )
        )
    }

    private fun formatDoseInput(
        value: Float
    ): String {
        return if (value % 1f == 0f) {
            value.toInt().toString()
        } else {
            String.format(
                Locale.US,
                "%.2f",
                value
            )
        }
    }

    private fun hideKeyboard() {
        val inputMethodManager =
            requireContext().getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

        inputMethodManager.hideSoftInputFromWindow(
            binding.root.windowToken,
            0
        )

        binding.etSingleDoseMl.clearFocus()
        binding.root.clearFocus()
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

    companion object {
        private const val ARG_DEVICE_IP = "deviceIp"
        private const val ARG_CHANNEL_INDEX = "channelIndex"
    }
}