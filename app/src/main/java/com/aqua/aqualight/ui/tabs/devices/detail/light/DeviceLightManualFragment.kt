package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightManualBinding
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.LightManualUiState
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

class DeviceLightManualFragment : Fragment(R.layout.fragment_device_light_manual) {

    private var _binding: FragmentDeviceLightManualBinding? = null
    private val binding get() = _binding!!

    private var currentState: LightManualUiState = LightManualUiState.preview()
    private var isProgrammaticSliderChange = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentDeviceLightManualBinding.bind(view)

        setupHeader()
        configureSliders()
        setupSliders()
        setupClicks()

        renderManualState(
            state = currentState
        )
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
            slider.valueTo = LightManualUiState.MAX_PERCENT.toFloat()
        }
    }

    private fun setupSliders() = with(binding) {
        bindSlider(
            slider = sliderMasterBrightness
        )

        bindSlider(
            slider = sliderRed
        )

        bindSlider(
            slider = sliderGreen
        )

        bindSlider(
            slider = sliderBlue
        )

        bindSlider(
            slider = sliderWhite
        )
    }

    private fun bindSlider(
        slider: Slider
    ) {
        slider.addOnChangeListener { _, _, _ ->
            if (isProgrammaticSliderChange) {
                return@addOnChangeListener
            }

            updateStateFromSliders()
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
            currentState = LightManualUiState.preview()

            renderManualState(
                state = currentState
            )
        }

        btnApplyTemporary.setOnClickListener {
            showMessage(
                message = "Temporary manual output command will be sent"
            )
        }

        btnSaveAsPreset.setOnClickListener {
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
    ) {
        val safeValue =
            value.coerceIn(
                minimumValue = 0,
                maximumValue = LightManualUiState.MAX_PERCENT
            )

        currentState =
            currentState.copy(
                redPercent = safeValue,
                greenPercent = safeValue,
                bluePercent = safeValue,
                whitePercent = safeValue
            )

        renderManualState(
            state = currentState
        )
    }

    private fun updateStateFromSliders() = with(binding) {
        currentState =
            currentState.copy(
                masterPercent = sliderMasterBrightness.value.roundToInt(),
                redPercent = sliderRed.value.roundToInt(),
                greenPercent = sliderGreen.value.roundToInt(),
                bluePercent = sliderBlue.value.roundToInt(),
                whitePercent = sliderWhite.value.roundToInt()
            )

        renderManualLabels(
            state = currentState
        )
    }

    private fun renderManualState(
        state: LightManualUiState
    ) = with(binding) {
        isProgrammaticSliderChange = true

        sliderMasterBrightness.value = state.safeMasterPercent.toFloat()
        sliderRed.value = state.safeRedPercent.toFloat()
        sliderGreen.value = state.safeGreenPercent.toFloat()
        sliderBlue.value = state.safeBluePercent.toFloat()
        sliderWhite.value = state.safeWhitePercent.toFloat()

        isProgrammaticSliderChange = false

        renderManualLabels(
            state = state
        )

        btnApplyToProgram.isEnabled = state.isApplyToProgramEnabled
        btnApplyToProgram.alpha =
            if (state.isApplyToProgramEnabled) {
                ENABLED_ALPHA
            } else {
                DISABLED_ALPHA
            }
    }

    private fun renderManualLabels(
        state: LightManualUiState
    ) = with(binding) {
        tvManualPowerState.text = state.powerStateLabel

        tvManualOutputValue.text = state.masterLabel

        tvRedValue.text = state.redLabel
        tvGreenValue.text = state.greenLabel
        tvBlueValue.text = state.blueLabel
        tvWhiteValue.text = state.whiteLabel

        tvPreviewAppearance.text = state.previewAppearanceLabel
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
        private const val ENABLED_ALPHA = 1f
        private const val DISABLED_ALPHA = 0.45f
    }
}