package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.dosing.EspDosingCommandClient
import com.aqua.aqualight.data.devices.dosing.EspDosingSettingsClient
import com.aqua.aqualight.databinding.FragmentDeviceDosingHourly24ModeSettingsBinding
import kotlinx.coroutines.launch
import java.util.Locale

class DeviceDosingHourly24ModeSettingsFragment :
    Fragment(R.layout.fragment_device_dosing_hourly24_mode_settings) {

    private var _binding: FragmentDeviceDosingHourly24ModeSettingsBinding? = null
    private val binding get() = _binding!!

    private var selectedMinute: Int = 15
    private var selectedWeekDays: List<Boolean> =
        List(
            size = 7
        ) {
            true
        }

    private var timerIndexForSave: Int? = null
    private var channelGpioPwmForSave: String = "-"
    private var channelCalibrationYeMsPerMlForSave: Long = -1L
    private var saveInProgress: Boolean = false

    private val channelIndex: Int
        get() = requireArguments().getInt(
            ARG_CHANNEL_INDEX,
            0
        ).coerceIn(
            minimumValue = 0,
            maximumValue = 3
        )

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
            FragmentDeviceDosingHourly24ModeSettingsBinding.bind(
                view
            )

        selectedMinute =
            defaultMinuteForChannel(
                channelIndex = channelIndex
            )

        bindHeader()
        bindSelectedPumpIndicator()
        bindDoseWatcher()
        bindClicks()
        renderDoseMinute()
        renderCalculatedDose()
        loadCurrentValues()
    }

    private fun bindHeader() {
        binding.tvTitle.text =
            "24 hourly"

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun bindSelectedPumpIndicator() {
        binding.selectedIndicatorPump1.visibility =
            if (channelIndex == 0) View.VISIBLE else View.GONE

        binding.selectedIndicatorPump2.visibility =
            if (channelIndex == 1) View.VISIBLE else View.GONE

        binding.selectedIndicatorPump3.visibility =
            if (channelIndex == 2) View.VISIBLE else View.GONE

        binding.selectedIndicatorPump4.visibility =
            if (channelIndex == 3) View.VISIBLE else View.GONE
    }

    private fun bindDoseWatcher() {
        binding.etDailyDoseMl.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    renderCalculatedDose()
                }

                override fun afterTextChanged(
                    s: Editable?
                ) = Unit
            }
        )
    }

    private fun bindClicks() {
        binding.btnCancel.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.rowDoseMinute.setOnClickListener {
            showDoseMinutePicker()
        }

        binding.btnSave.setOnClickListener {
            saveHourly24Mode()
        }
    }

    private fun loadCurrentValues() {
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

            channelCalibrationYeMsPerMlForSave =
                channel.calibrationYeMsPerMl

            timerIndexForSave =
                timer?.timerIndex ?: channelIndex

            if (timer != null) {
                val dailyDose =
                    if (timer.count > 1) {
                        timer.doseMl * timer.count
                    } else {
                        timer.doseMl
                    }

                if (dailyDose > 0f) {
                    binding.etDailyDoseMl.setText(
                        formatDoseInput(
                            value = dailyDose
                        )
                    )
                }

                selectedMinute =
                    parseMinuteFromTime(
                        value = timer.timeStart
                    ) ?: selectedMinute

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
            }

            renderDoseMinute()
            renderCalculatedDose()
        }
    }

    private fun showDoseMinutePicker() {
        hideKeyboard()

        val options =
            arrayOf(
                ":00",
                ":15",
                ":30",
                ":45"
            )

        val values =
            listOf(
                0,
                15,
                30,
                45
            )

        val checkedIndex =
            values.indexOf(
                selectedMinute
            ).takeIf { index ->
                index >= 0
            } ?: 1

        AlertDialog.Builder(
            requireContext()
        )
            .setTitle(
                "Dose minute"
            )
            .setSingleChoiceItems(
                options,
                checkedIndex
            ) { dialog, which ->
                selectedMinute =
                    values[which]

                renderDoseMinute()

                dialog.dismiss()
            }
            .show()
    }

    private fun saveHourly24Mode() {
        if (saveInProgress) {
            return
        }

        hideKeyboard()

        val dailyDoseMl =
            binding.etDailyDoseMl.text
                ?.toString()
                ?.trim()
                ?.replace(
                    oldValue = ",",
                    newValue = "."
                )
                ?.toFloatOrNull()

        if (
            dailyDoseMl == null ||
            dailyDoseMl <= 0f
        ) {
            showComingNext(
                message = "Please enter a valid daily dose."
            )
            return
        }

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

        if (channelCalibrationYeMsPerMlForSave <= 0L) {
            showComingNext(
                message = "Please calibrate this channel first."
            )
            return
        }

        saveInProgress =
            true

        renderSavingState()

        viewLifecycleOwner.lifecycleScope.launch {
            val saved =
                EspDosingCommandClient.saveHourly24ModeSchedule(
                    deviceIp = deviceIp,
                    channelIndex = channelIndex,
                    timerIndex = timerIndexForSave ?: channelIndex,
                    channelGpioPwm = safeGpioPwm,
                    channelCalibrationYeMsPerMl = channelCalibrationYeMsPerMlForSave,
                    dailyDoseMl = dailyDoseMl,
                    selectedMinute = selectedMinute,
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
                    message = "24 hourly mode saved."
                )

                findNavController().navigateUp()
            } else {
                showComingNext(
                    message = "24 hourly mode could not be saved to device."
                )
            }
        }
    }

    private fun renderDoseMinute() {
        binding.tvDoseMinuteValue.text =
            String.format(
                Locale.US,
                ":%02d",
                selectedMinute
            )
    }

    private fun renderCalculatedDose() {
        val dailyDoseMl =
            binding.etDailyDoseMl.text
                ?.toString()
                ?.trim()
                ?.replace(
                    oldValue = ",",
                    newValue = "."
                )
                ?.toFloatOrNull()

        val perDose =
            if (
                dailyDoseMl != null &&
                dailyDoseMl > 0f
            ) {
                dailyDoseMl / 24f
            } else {
                0f
            }

        binding.tvCalculatedDoseValue.text =
            "${formatMl(perDose)} every hour at ${
                String.format(
                    Locale.US,
                    ":%02d",
                    selectedMinute
                )
            }"
    }

    private fun renderSavingState() {
        binding.btnSave.isEnabled =
            !saveInProgress

        binding.btnCancel.isEnabled =
            !saveInProgress

        binding.rowDoseMinute.isEnabled =
            !saveInProgress

        binding.etDailyDoseMl.isEnabled =
            !saveInProgress

        binding.btnSave.alpha =
            if (saveInProgress) 0.55f else 1f

        binding.btnCancel.alpha =
            if (saveInProgress) 0.55f else 1f

        binding.btnSave.text =
            if (saveInProgress) {
                "Saving..."
            } else {
                "Save 24 hourly"
            }
    }

    private fun defaultMinuteForChannel(
        channelIndex: Int
    ): Int {
        return when (channelIndex.coerceIn(0, 3)) {
            0 -> 0
            1 -> 15
            2 -> 30
            else -> 45
        }
    }

    private fun parseMinuteFromTime(
        value: String
    ): Int? {
        return value.split(
            delimiter = ":"
        ).getOrNull(
            index = 1
        )?.toIntOrNull()
            ?.coerceIn(
                minimumValue = 0,
                maximumValue = 59
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

    private fun formatMl(
        value: Float
    ): String {
        return if (value % 1f == 0f) {
            "${value.toInt()} ml"
        } else {
            String.format(
                Locale.US,
                "%.3f ml",
                value
            ).trimEnd(
                '0'
            ).trimEnd(
                '.'
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

        binding.etDailyDoseMl.clearFocus()
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