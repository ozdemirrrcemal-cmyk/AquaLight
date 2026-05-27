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
import com.aqua.aqualight.databinding.FragmentDeviceDosingHourly24ModeSettingsBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class DeviceDosingHourly24ModeSettingsFragment :
    Fragment(R.layout.fragment_device_dosing_hourly24_mode_settings) {

    private var _binding: FragmentDeviceDosingHourly24ModeSettingsBinding? = null
    private val binding get() = _binding!!

    private var selectedMinute: Int = 15
    private var saveInProgress: Boolean = false

    private val channelIndex: Int
        get() = requireArguments().getInt(
            ARG_CHANNEL_INDEX,
            0
        ).coerceIn(
            minimumValue = 0,
            maximumValue = 3
        )

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
    }

    private fun bindHeader() {
        binding.tvTitle.text =
            "24 hourly"

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
            if (!saveInProgress) {
                findNavController().navigateUp()
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
                renderCalculatedDose()

                dialog.dismiss()
            }
            .show()
    }

    private fun handleSaveClick() {
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

        viewLifecycleOwner.lifecycleScope.launch {
            delay(
                timeMillis = 700L
            )

            setLoading(
                show = false
            )

            saveInProgress =
                false

            if (_binding == null) {
                return@launch
            }

            renderSavingState()

            showSnackBar(
                message = "24 hourly save will be connected after screen design is finalized.",
                type = BaseActivity.SnackType.NORMAL
            )
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
                "Save 24 hourly"
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
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_DEVICE_IP = "deviceIp"
        private const val ARG_DEVICE_TITLE = "deviceTitle"
        private const val ARG_CHANNEL_INDEX = "channelIndex"
    }
}