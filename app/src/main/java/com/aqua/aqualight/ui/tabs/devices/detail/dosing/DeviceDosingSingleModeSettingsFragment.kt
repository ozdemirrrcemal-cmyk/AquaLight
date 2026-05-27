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
import com.aqua.aqualight.databinding.FragmentDeviceDosingSingleModeSettingsBinding
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.bottomsheet.DosingBottomSheets
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch
import java.util.Locale

class DeviceDosingSingleModeSettingsFragment :
    Fragment(R.layout.fragment_device_dosing_single_mode_settings) {

    private var _binding: FragmentDeviceDosingSingleModeSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var dosingEspRepository: DosingEspRepository

    private var espDosingState: DosingEspState? =
        null

    private var selectedHour: Int =
        0

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
            FragmentDeviceDosingSingleModeSettingsBinding.bind(
                view
            )

        dosingEspRepository =
            DosingEspRepository()

        bindHeader()
        bindSelectedPumpIndicator()
        bindInitialValues()
        bindClicks()
        fetchSingleModeStateFromEsp()
    }

    private fun bindHeader() {
        binding.tvTitle.text =
            "Single dose"

        binding.btnBack.setOnClickListener {
            if (!saveInProgress) {
                findNavController().navigateUp()
            }
        }
    }

    private fun bindInitialValues() {
        applyStartTime(
            value = "00:00"
        )

        binding.etSingleDoseMl.setText("")
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

        binding.rowStartTime.setOnClickListener {
            if (!saveInProgress) {
                showTimePicker()
            }
        }

        binding.btnSave.setOnClickListener {
            handleSaveClick()
        }
    }

    private fun fetchSingleModeStateFromEsp() {
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
                        ?: "Single mode data could not be loaded from the device.",
                    onConfirm = {
                        fetchSingleModeStateFromEsp()
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

        if (state.activeMode == DosingScheduleMode.SINGLE) {
            binding.etSingleDoseMl.setText(
                formatDoseMl(
                    value = state.configuredDailyDoseMl
                )
            )

            applyStartTime(
                value = state.timer.timeStart
            )
        }
    }

    private fun showTimePicker() {
        hideKeyboard()

        DosingBottomSheets.showTimePicker(
            context = requireContext(),
            title = "Select Start Time",
            initialHour = selectedHour,
            initialMinute = selectedMinute
        ) { hour, minute ->
            selectedHour =
                hour

            selectedMinute =
                minute

            binding.tvStartTimeValue.text =
                formatTime(
                    hour = selectedHour,
                    minute = selectedMinute
                )
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
            showSnackBar(
                message = "Please enter a valid dose amount.",
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
                hour = selectedHour,
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
                        currentState.timer.gpioPwm.takeIf { value ->
                            value.isNotBlank() && value != "-"
                        } ?: currentState.channel.gpioPwm

                    if (
                        gpioPwm.isBlank() ||
                        gpioPwm == "-"
                    ) {
                        throw IllegalStateException(
                            "PWM channel information is missing."
                        )
                    }

                    dosingEspRepository.saveSingleSchedule(
                        deviceIp = deviceIp,
                        channelIndex = channelIndex,
                        channelNumber = channelNumber,
                        gpioPwm = gpioPwm,
                        totalDailyDoseMl = doseMl,
                        weekDays = currentState.timer.weekDays,
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
                        ?: "Single mode could not be saved. Please check the device connection and try again.",
                    onConfirm = {
                        handleSaveClick()
                    }
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
            safeValue.split(
                ":"
            )

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

        binding.etSingleDoseMl.clearFocus()
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
    }
}