package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightManualBinding
import kotlin.math.roundToInt

class DeviceLightManualFragment : Fragment(R.layout.fragment_device_light_manual) {

    private var _binding: FragmentDeviceLightManualBinding? = null
    private val binding get() = _binding!!

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightManualBinding.bind(view)

        configureSeekBars()
        renderPreviewState()
        setupSeekBars()
        setupClicks()
    }

    private fun configureSeekBars() = with(binding) {
        listOf(
            sliderMasterBrightness,
            sliderRed,
            sliderGreen,
            sliderBlue,
            sliderWhite
        ).forEach { seekBar ->
            seekBar.max = MAX_PERCENT
        }
    }

    private fun renderPreviewState() = with(binding) {
        tvManualConnectionStatus.text = "Online · Manual ready"
        tvManualPowerState.text = "LIVE"

        sliderMasterBrightness.progress = 78
        sliderRed.progress = 80
        sliderGreen.progress = 84
        sliderBlue.progress = 79
        sliderWhite.progress = 65

        updateMasterValue()
        updateChannelValues()
        updatePowerState()
        updatePreviewText()

        btnApplyToProgram.isEnabled = false
        btnApplyToProgram.alpha = DISABLED_ALPHA
    }

    private fun setupSeekBars() = with(binding) {
        bindMasterSeekBar(
            seekBar = sliderMasterBrightness
        )

        bindChannelSeekBar(
            seekBar = sliderRed,
            valueView = tvRedValue
        )

        bindChannelSeekBar(
            seekBar = sliderGreen,
            valueView = tvGreenValue
        )

        bindChannelSeekBar(
            seekBar = sliderBlue,
            valueView = tvBlueValue
        )

        bindChannelSeekBar(
            seekBar = sliderWhite,
            valueView = tvWhiteValue
        )
    }

    private fun bindMasterSeekBar(
        seekBar: SeekBar
    ) {
        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    updateMasterValue()
                    updatePowerState()
                    updatePreviewText()
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar?
                ) = Unit

                override fun onStopTrackingTouch(
                    seekBar: SeekBar?
                ) = Unit
            }
        )
    }

    private fun bindChannelSeekBar(
        seekBar: SeekBar,
        valueView: TextView
    ) {
        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    valueView.text = formatPercent(progress)
                    updateChannelValues()
                    updatePreviewText()
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar?
                ) = Unit

                override fun onStopTrackingTouch(
                    seekBar: SeekBar?
                ) = Unit
            }
        )
    }

    private fun setupClicks() = with(binding) {

        btnManualSync.setOnClickListener {
            showMessage("Syncing manual light data")
        }

        btnManualMore.setOnClickListener {
            showMessage("Manual options will be added")
        }

        btnAll100.setOnClickListener {
            applyAllChannels(100)
        }

        btnAll50.setOnClickListener {
            applyAllChannels(50)
        }

        btnAllOff.setOnClickListener {
            applyAllChannels(0)
        }

        btnResetChannels.setOnClickListener {
            sliderMasterBrightness.progress = 78
            sliderRed.progress = 80
            sliderGreen.progress = 84
            sliderBlue.progress = 79
            sliderWhite.progress = 65

            updateMasterValue()
            updateChannelValues()
            updatePowerState()
            updatePreviewText()
        }

        btnApplyTemporary.setOnClickListener {
            showMessage("Temporary manual output command will be sent")
        }

        btnSaveAsPreset.setOnClickListener {
            showMessage("Save as preset will be added")
        }

        btnApplyToProgram.setOnClickListener {
            if (!btnApplyToProgram.isEnabled) {
                return@setOnClickListener
            }

            showMessage("Apply to program will be added")
        }
    }

    private fun applyAllChannels(
        value: Int
    ) = with(binding) {
        val safeValue = value.coerceIn(0, MAX_PERCENT)

        sliderRed.progress = safeValue
        sliderGreen.progress = safeValue
        sliderBlue.progress = safeValue
        sliderWhite.progress = safeValue

        updateChannelValues()
        updatePreviewText()
    }

    private fun updateMasterValue() = with(binding) {
        tvManualOutputValue.text = formatPercent(
            value = sliderMasterBrightness.progress
        )
    }

    private fun updateChannelValues() = with(binding) {
        val red = sliderRed.progress
        val green = sliderGreen.progress
        val blue = sliderBlue.progress
        val white = sliderWhite.progress

        tvRedValue.text = formatPercent(red)
        tvGreenValue.text = formatPercent(green)
        tvBlueValue.text = formatPercent(blue)
        tvWhiteValue.text = formatPercent(white)

        tvManualRedSummaryValue.text = "R$red%"
        tvManualGreenSummaryValue.text = "G$green%"
        tvManualBlueSummaryValue.text = "B$blue%"
        tvManualWhiteSummaryValue.text = "W$white%"
    }

    private fun updatePowerState() = with(binding) {
        val master = sliderMasterBrightness.progress

        tvManualPowerState.text = if (master <= 0) {
            "OFF"
        } else {
            "LIVE"
        }
    }

    private fun updatePreviewText() = with(binding) {
        val master = sliderMasterBrightness.progress.toFloat()
        val red = sliderRed.progress.toFloat()
        val green = sliderGreen.progress.toFloat()
        val blue = sliderBlue.progress.toFloat()
        val white = sliderWhite.progress.toFloat()

        val channelAverage = (red + green + blue + white) / 4f
        val effectiveAverage = channelAverage * (master / 100f)

        val description = when {
            master <= 0f || effectiveAverage <= 5f -> {
                "Estimated appearance: Lights off"
            }

            blue > red + 20f -> {
                "Estimated appearance: Cool blue display"
            }

            red > blue + 20f -> {
                "Estimated appearance: Warm evening tone"
            }

            white >= 70f && effectiveAverage >= 70f -> {
                "Estimated appearance: Bright daylight"
            }

            effectiveAverage >= 45f -> {
                "Estimated appearance: Neutral daylight"
            }

            else -> {
                "Estimated appearance: Soft low light"
            }
        }

        tvPreviewAppearance.text = description
    }

    private fun formatPercent(
        value: Int
    ): String {
        return "${value.coerceIn(0, MAX_PERCENT)}%"
    }

    private fun showMessage(
        message: String
    ) {
        Toast.makeText(
            requireContext(),
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val MAX_PERCENT = 100
        private const val DISABLED_ALPHA = 0.45f

        fun newInstance(
            deviceId: Long
        ): DeviceLightManualFragment {
            return DeviceLightManualFragment().apply {
                arguments = Bundle().apply {
                    putLong(
                        ARG_DEVICE_ID,
                        deviceId
                    )
                }
            }
        }
    }
}