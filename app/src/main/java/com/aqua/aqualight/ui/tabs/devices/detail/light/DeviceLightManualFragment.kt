package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightManualBinding
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

class DeviceLightManualFragment : Fragment(R.layout.fragment_device_light_manual) {

    private var _binding: FragmentDeviceLightManualBinding? = null
    private val binding get() = _binding!!

    private var lastSyncedManualState = ManualLightState(
        master = DEFAULT_MASTER,
        red = DEFAULT_RED,
        green = DEFAULT_GREEN,
        blue = DEFAULT_BLUE,
        white = DEFAULT_WHITE
    )

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightManualBinding.bind(view)

        setupHeader()
        configureSliders()
        renderPreviewState()
        setupSliders()
        setupClicks()
    }

    private fun setupHeader() = with(binding.deviceHeader) {
        tvTitle.text = getString(R.string.light_manual_title)

        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        headerActionsContainer.visibility = View.VISIBLE

        btnActionOne.visibility = View.VISIBLE
        btnActionOne.setImageResource(R.drawable.ic_light_sync_24)
        btnActionOne.contentDescription = getString(R.string.light_cd_sync)
        btnActionOne.setOnClickListener {
            onHeaderSyncClick()
        }

        btnActionTwo.visibility = View.GONE
        btnActionThree.visibility = View.GONE
    }

    private fun onHeaderSyncClick() {
        if (_binding == null) {
            return
        }

        // Veri bağlanınca burada ESP32/manual output refresh çağrısı yapılacak.
        showMessage(
            message = "Syncing manual light data"
        )
    }

    private fun configureSliders() = with(binding) {
        listOf(
            sliderMasterBrightness,
            sliderRed,
            sliderGreen,
            sliderBlue,
            sliderWhite
        ).forEach { slider ->
            slider.valueFrom = 0f
            slider.valueTo = MAX_PERCENT.toFloat()
            slider.stepSize = 1f
        }
    }

    private fun renderPreviewState() = with(binding) {
        applyManualValues(
            state = lastSyncedManualState
        )

        btnApplyToProgram.isEnabled = false
        btnApplyToProgram.alpha = DISABLED_ALPHA
    }

    private fun setupSliders() = with(binding) {
        bindMasterSlider(
            slider = sliderMasterBrightness
        )

        bindChannelSlider(
            slider = sliderRed,
            valueView = tvRedValue
        )

        bindChannelSlider(
            slider = sliderGreen,
            valueView = tvGreenValue
        )

        bindChannelSlider(
            slider = sliderBlue,
            valueView = tvBlueValue
        )

        bindChannelSlider(
            slider = sliderWhite,
            valueView = tvWhiteValue
        )
    }

    private fun bindMasterSlider(
        slider: Slider
    ) {
        slider.addOnChangeListener { _, _, _ ->
            refreshManualUi()
        }
    }

    private fun bindChannelSlider(
        slider: Slider,
        valueView: TextView
    ) {
        slider.addOnChangeListener { _, value, _ ->
            valueView.text = formatPercent(
                value = value.roundToInt()
            )

            refreshManualUi()
        }
    }

    private fun setupClicks() = with(binding) {
        btnAll100.setOnClickListener {
            applyAllChannels(
                value = 100
            )
        }

        btnAll50.setOnClickListener {
            applyAllChannels(
                value = 50
            )
        }

        btnAllOff.setOnClickListener {
            applyAllChannels(
                value = 0
            )
        }

        btnResetChannels.setOnClickListener {
            applyManualValues(
                state = lastSyncedManualState
            )
        }

        btnApplyTemporary.setOnClickListener {
            val state = currentManualState()

            // Veri bağlanınca burada ESP32 temporary manual output komutu gönderilecek.
            // state.master, state.red, state.green, state.blue, state.white kullanılacak.
            showMessage(
                message = "Temporary manual output command will be sent"
            )
        }

        btnSaveAsPreset.setOnClickListener {
            val state = currentManualState()

            // Veri bağlanınca burada Custom Preset create flow açılacak.
            // Manual > Save as Preset ile Presets & Scenes > Custom Presets aynı yapıyı kullanmalı.
            showMessage(
                message = "Save as preset will be added"
            )
        }

        btnApplyToProgram.setOnClickListener {
            if (!btnApplyToProgram.isEnabled) {
                return@setOnClickListener
            }

            showMessage(
                message = "Apply to program will be added"
            )
        }
    }

    private fun applyAllChannels(
        value: Int
    ) = with(binding) {
        val safeValue = value.coerceIn(
            minimumValue = 0,
            maximumValue = MAX_PERCENT
        )

        if (safeValue == 0) {
            sliderMasterBrightness.value = 0f
        } else if (sliderMasterBrightness.value.roundToInt() == 0) {
            sliderMasterBrightness.value = safeValue.toFloat()
        }

        sliderRed.value = safeValue.toFloat()
        sliderGreen.value = safeValue.toFloat()
        sliderBlue.value = safeValue.toFloat()
        sliderWhite.value = safeValue.toFloat()

        refreshManualUi()
    }

    private fun applyManualValues(
        state: ManualLightState
    ) = with(binding) {
        sliderMasterBrightness.value = state.master
            .coerceIn(0, MAX_PERCENT)
            .toFloat()

        sliderRed.value = state.red
            .coerceIn(0, MAX_PERCENT)
            .toFloat()

        sliderGreen.value = state.green
            .coerceIn(0, MAX_PERCENT)
            .toFloat()

        sliderBlue.value = state.blue
            .coerceIn(0, MAX_PERCENT)
            .toFloat()

        sliderWhite.value = state.white
            .coerceIn(0, MAX_PERCENT)
            .toFloat()

        refreshManualUi()
    }

    private fun refreshManualUi() {
        updateMasterValue()
        updateChannelValues()
        updatePowerState()
        updatePreviewText()
    }

    private fun updateMasterValue() = with(binding) {
        tvManualOutputValue.text = formatPercent(
            value = sliderMasterBrightness.value.roundToInt()
        )
    }

    private fun updateChannelValues() = with(binding) {
        val state = currentManualState()

        tvRedValue.text = formatPercent(
            value = state.red
        )

        tvGreenValue.text = formatPercent(
            value = state.green
        )

        tvBlueValue.text = formatPercent(
            value = state.blue
        )

        tvWhiteValue.text = formatPercent(
            value = state.white
        )
    }

    private fun updatePowerState() = with(binding) {
        val state = currentManualState()

        val allChannelsOff =
            state.red == 0 &&
                state.green == 0 &&
                state.blue == 0 &&
                state.white == 0

        val isOff =
            state.master <= 0 ||
                allChannelsOff

        tvManualPowerState.text =
            if (isOff) {
                "OFF"
            } else {
                "LIVE"
            }
    }

    private fun updatePreviewText() = with(binding) {
        val state = currentManualState()

        val channelAverage =
            (state.red + state.green + state.blue + state.white) / 4f

        val effectiveAverage =
            channelAverage * (state.master / 100f)

        val description =
            when {
                state.master <= 0 || effectiveAverage <= 5f -> {
                    "Estimated appearance: Lights off"
                }

                state.blue > state.red + 20 -> {
                    "Estimated appearance: Cool blue display"
                }

                state.red > state.blue + 20 -> {
                    "Estimated appearance: Warm evening tone"
                }

                state.white >= 70 && effectiveAverage >= 70f -> {
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

        updatePreviewGradient(
            state = state
        )
    }

    private fun updatePreviewGradient(
        state: ManualLightState
    ) = with(binding) {
        val masterRatio = state.master.coerceIn(0, MAX_PERCENT) / 100f

        val mixedRed = ((state.red + state.white) / 2f * masterRatio)
            .roundToInt()
            .coerceIn(0, MAX_PERCENT)

        val mixedGreen = ((state.green + state.white) / 2f * masterRatio)
            .roundToInt()
            .coerceIn(0, MAX_PERCENT)

        val mixedBlue = ((state.blue + state.white) / 2f * masterRatio)
            .roundToInt()
            .coerceIn(0, MAX_PERCENT)

        val startColor = Color.rgb(
            percentToColorChannel(mixedRed),
            percentToColorChannel((mixedGreen * 1.15f).roundToInt()),
            percentToColorChannel((mixedBlue * 0.85f).roundToInt())
        )

        val centerColor = Color.rgb(
            percentToColorChannel(mixedRed),
            percentToColorChannel(mixedGreen),
            percentToColorChannel(mixedBlue)
        )

        val endColor = Color.rgb(
            percentToColorChannel((mixedRed * 0.85f).roundToInt()),
            percentToColorChannel(mixedGreen),
            percentToColorChannel((mixedBlue * 1.25f).roundToInt())
        )

        val drawable = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                startColor,
                centerColor,
                endColor
            )
        ).apply {
            cornerRadius = resources.getDimension(
                R.dimen.light_card_corner_radius_large
            )
        }

        viewLivePreviewGradient.background = drawable
    }

    private fun percentToColorChannel(
        value: Int
    ): Int {
        return (value.coerceIn(0, MAX_PERCENT) * COLOR_CHANNEL_MULTIPLIER)
            .roundToInt()
            .coerceIn(0, RGB_MAX)
    }

    private fun currentManualState(): ManualLightState = with(binding) {
        return ManualLightState(
            master = sliderMasterBrightness.value.roundToInt()
                .coerceIn(0, MAX_PERCENT),
            red = sliderRed.value.roundToInt()
                .coerceIn(0, MAX_PERCENT),
            green = sliderGreen.value.roundToInt()
                .coerceIn(0, MAX_PERCENT),
            blue = sliderBlue.value.roundToInt()
                .coerceIn(0, MAX_PERCENT),
            white = sliderWhite.value.roundToInt()
                .coerceIn(0, MAX_PERCENT)
        )
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

    private data class ManualLightState(
        val master: Int,
        val red: Int,
        val green: Int,
        val blue: Int,
        val white: Int
    )

    companion object {
        private const val MAX_PERCENT = 100
        private const val RGB_MAX = 255
        private const val COLOR_CHANNEL_MULTIPLIER = 2.55f
        private const val DISABLED_ALPHA = 0.45f

        private const val DEFAULT_MASTER = 78
        private const val DEFAULT_RED = 80
        private const val DEFAULT_GREEN = 84
        private const val DEFAULT_BLUE = 79
        private const val DEFAULT_WHITE = 65
    }
}