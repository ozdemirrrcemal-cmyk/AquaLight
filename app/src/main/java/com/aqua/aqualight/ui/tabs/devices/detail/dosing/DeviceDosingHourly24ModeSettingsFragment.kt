package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.dosing.esp.DosingEspRepository
import com.aqua.aqualight.data.devices.dosing.esp.DosingEspState
import com.aqua.aqualight.data.devices.dosing.esp.DosingScheduleMode
import com.aqua.aqualight.databinding.FragmentDeviceDosingHourly24ModeSettingsBinding
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.bottomsheet.DosingBottomSheets
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class DeviceDosingHourly24ModeSettingsFragment :
    Fragment(R.layout.fragment_device_dosing_hourly24_mode_settings) {

    private var _binding: FragmentDeviceDosingHourly24ModeSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var dosingEspRepository: DosingEspRepository

    private var espDosingState: DosingEspState? =
        null

    private var selectedMinute: Int =
        0

    private var saveInProgress: Boolean =
        false

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
        get() = requireArguments().getString(
            ARG_DEVICE_IP
        ).orEmpty()

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

        dosingEspRepository =
            DosingEspRepository()

        selectedMinute =
            defaultMinuteForChannel(
                channelIndex = channelIndex
            )

        bindHeader()
        bindSelectedPumpIndicator()
        bindClicks()
        renderDoseMinute()
        fetchHourly24ModeStateFromEsp()
    }

    private fun bindHeader() {
        binding.tvTitle.text =
            "24 Hourly"

        binding.btnBack.setOnClickListener {
            if (!saveInProgress) {
                findNavController().navigateUp()
            }
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
            if (!saveInProgress) {
                findNavController().navigateUp()
            }
        }

        binding.cardDoseMinute.setOnClickListener {
            if (!saveInProgress) {
                showDoseMinutePicker()
            }
        }

        binding.rowDoseMinute.setOnClickListener {
            if (!saveInProgress) {
                showDoseMinutePicker()
            }
        }

        binding.btnSave.setOnClickListener {
            handleSaveClick()
        }
    }

    private fun fetchHourly24ModeStateFromEsp() {
        if (deviceIp.isBlank()) {
            showSnackBar(
                message = "Device IP address is missing.",
                type = BaseActivity.SnackType.WARNING
            )

            return
        }

        setLoading(
            show = true
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val result =
                runCatching {
                    dosingEspRepository.fetchDosingState(
                        deviceIp = deviceIp,
                        channelIndex = channelIndex
                    )
                }

            setLoading(
                show = false
            )

            if (_binding == null) {
                return@launch
            }

            result.onSuccess { state ->
                applyEspState(
                    state = state
                )
            }.onFailure { throwable ->
                DialogManager.showConfirmDialog(
                    context = requireContext(),
                    type = DialogType.ERROR,
                    title = "Device Data Failed",
                    message = throwable.message
                        ?: "24 Hourly mode data could not be loaded from the device.",
                    onConfirm = {
                        fetchHourly24ModeStateFromEsp()
                    }
                )
            }
        }
    }

    private fun applyEspState(
        state: DosingEspState
    ) {
        espDosingState =
            state

        if (state.activeMode != DosingScheduleMode.HOURLY_24) {
            return
        }

        val hourlyTimer =
            findHourly24Timer(
                state = state
            ) ?: return

        binding.etDailyDoseMl.setText(
            formatDoseMl(
                value = normalizeHourly24DailyDoseForDisplay(
                    value = hourlyTimer.configuredDailyDoseMl
                )
            )
        )

        selectedMinute =
            parseMinuteFromTime(
                value = hourlyTimer.timeStart
            )

        renderDoseMinute()
    }

    private fun findHourly24Timer(
        state: DosingEspState
    ) =
        state.channelTimers.firstOrNull { timer ->
            timer.name.contains(
                other = "HOURLY_24",
                ignoreCase = true
            ) &&
                timer.dosePerRunMl > 0f &&
                timer.count > 0
        } ?: state.channelTimers.firstOrNull { timer ->
            timer.count == 24 &&
                timer.dosePerRunMl > 0f
        } ?: state.timer.takeIf { timer ->
            timer.dosePerRunMl > 0f &&
                timer.count > 0
        }

    private fun getScheduleWeekDays(
        state: DosingEspState
    ): List<Boolean> {
        val timer =
            findHourly24Timer(
                state = state
            ) ?: state.timer

        return if (timer.weekDays.size == 7) {
            timer.weekDays
        } else {
            List(
                size = 7
            ) {
                true
            }
        }
    }

    private fun showDoseMinutePicker() {
        hideKeyboard()

        DosingBottomSheets.showMinutePicker(
            context = requireContext(),
            title = "Select Dose Minute",
            initialMinute = selectedMinute
        ) { minute ->
            selectedMinute =
                minute.coerceIn(
                    minimumValue = 0,
                    maximumValue = 59
                )

            renderDoseMinute()
        }
    }

    private fun handleSaveClick() {
        if (saveInProgress) {
            return
        }

        hideKeyboard()

        if (deviceIp.isBlank()) {
            showSnackBar(
                message = "Device IP address is missing.",
                type = BaseActivity.SnackType.WARNING
            )

            return
        }

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
            showSnackBar(
                message = "Please enter a valid daily dose.",
                type = BaseActivity.SnackType.WARNING
            )

            return
        }

        saveInProgress =
            true

        renderSavingState()

        setLoading(
            show = true
        )

        val startTime =
            formatTime(
                hour = 0,
                minute = selectedMinute
            )

        viewLifecycleOwner.lifecycleScope.launch {
            val result =
                runCatching {
                    val currentState =
                        espDosingState ?: dosingEspRepository.fetchDosingState(
                            deviceIp = deviceIp,
                            channelIndex = channelIndex
                        )

                    val gpioPwm =
                        currentState.channel.gpioPwm.takeIf { value ->
                            value.isNotBlank() && value != "-"
                        } ?: throw IllegalStateException(
                            "PWM channel information is missing."
                        )

                    dosingEspRepository.saveHourly24Schedule(
                        deviceIp = deviceIp,
                        channelIndex = channelIndex,
                        channelNumber = channelNumber,
                        gpioPwm = gpioPwm,
                        totalDailyDoseMl = dailyDoseMl,
                        weekDays = getScheduleWeekDays(
                            state = currentState
                        ),
                        startTime = startTime,
                        enabled = true
                    )
                }

            setLoading(
                show = false
            )

            if (_binding == null) {
                return@launch
            }

            saveInProgress =
                false

            renderSavingState()

            result.onSuccess {
                findNavController()
                    .previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(
                        RESULT_DOSING_SCHEDULE_UPDATED,
                        true
                    )

                findNavController().navigateUp()
            }.onFailure { throwable ->
                DialogManager.showConfirmDialog(
                    context = requireContext(),
                    type = DialogType.ERROR,
                    title = "Save Failed",
                    message = throwable.message
                        ?: "24 Hourly mode could not be saved. Please check the device connection and try again.",
                    onConfirm = {
                        handleSaveClick()
                    }
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

    private fun renderSavingState() {
        binding.btnSave.isEnabled =
            !saveInProgress

        binding.btnCancel.isEnabled =
            !saveInProgress

        binding.cardDoseMinute.isEnabled =
            !saveInProgress

        binding.rowDoseMinute.isEnabled =
            !saveInProgress

        binding.etDailyDoseMl.isEnabled =
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
                "Save 24 Hourly"
            }
    }

    private fun defaultMinuteForChannel(
        channelIndex: Int
    ): Int {
        return when (
            channelIndex.coerceIn(
                minimumValue = 0,
                maximumValue = 3
            )
        ) {
            0 -> 0
            1 -> 15
            2 -> 30
            else -> 45
        }
    }

    private fun parseMinuteFromTime(
        value: String
    ): Int {
        return value.ifBlank {
            "00:00"
        }.split(
            ":"
        ).getOrNull(
            index = 1
        )?.toIntOrNull()
            ?.coerceIn(
                minimumValue = 0,
                maximumValue = 59
            ) ?: defaultMinuteForChannel(
            channelIndex = channelIndex
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

    private fun normalizeHourly24DailyDoseForDisplay(
        value: Float
    ): Float {
        val safeValue =
            value.coerceAtLeast(
                minimumValue = 0f
            )

        val nearestWhole =
            safeValue.roundToInt().toFloat()

        return if (
            abs(
                safeValue - nearestWhole
            ) <= HOURLY24_DAILY_DOSE_ROUNDING_TOLERANCE_ML
        ) {
            nearestWhole
        } else {
            safeValue
        }
    }

    private fun formatDoseMl(
        value: Float
    ): String {
        return if (value % 1f == 0f) {
            value.toInt().toString()
        } else {
            String.format(
                Locale.US,
                "%.2f",
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

    private fun setLoading(
        show: Boolean
    ) {
        (activity as? BaseActivity)?.showLoading(
            show = show
        )
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
        if (saveInProgress) {
            setLoading(
                show = false
            )
        }

        saveInProgress =
            false

        _binding =
            null

        super.onDestroyView()
    }

    companion object {
        private const val ARG_DEVICE_IP = "deviceIp"
        private const val ARG_CHANNEL_INDEX = "channelIndex"

        private const val RESULT_DOSING_SCHEDULE_UPDATED =
            "dosingScheduleUpdated"

        private const val HOURLY24_DAILY_DOSE_ROUNDING_TOLERANCE_ML = 0.12f
    }
}