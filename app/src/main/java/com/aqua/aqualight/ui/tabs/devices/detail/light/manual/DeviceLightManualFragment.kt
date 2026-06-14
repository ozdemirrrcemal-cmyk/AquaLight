package com.aqua.aqualight.ui.tabs.devices.detail.light.manual

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightManualBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.common.feedback.DeviceFeedbackType
import com.aqua.aqualight.ui.tabs.devices.common.feedback.showDeviceLoading
import com.aqua.aqualight.ui.tabs.devices.common.feedback.showDeviceSnack
import com.aqua.aqualight.data.devices.light.math.LightRgbwPreviewColorMath
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightControlMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightScene
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.sheet.SaveLightPresetBottomSheet
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch

class DeviceLightManualFragment :
    Fragment(R.layout.fragment_device_light_manual) {

    private val args: DeviceLightManualFragmentArgs by navArgs()

    private var _binding: FragmentDeviceLightManualBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeviceLightManualViewModel by viewModels()

    private var isRendering = false

    private val userControlledSliderIds =
        mutableSetOf<Int>()

    private val deviceId: Long
        get() = args.deviceId

    private val sceneButtons: List<View>
        get() = listOf(
            binding.scenePlantGrowth,
            binding.sceneFishDisplay,
            binding.sceneShrimpSafe,
            binding.sceneBlueAccent,
            binding.sceneRed,
            binding.sceneFullSpectrum
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
            FragmentDeviceLightManualBinding.bind(view)

        setupHeader()
        setupClicks()
        setupSliders()
        observeUiState()
        observeEvents()

        viewModel.initialize(
            deviceId
        )
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = "Manual Control"
            )
        )
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.uiState.collect { state ->
                    renderUiState(
                        state
                    )
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.events.collect { event ->
                    when (event) {
                        is ManualLightEvent.ShowMessage -> {
                            showDeviceSnack(
                                message = event.message,
                                type = DeviceFeedbackType.SUCCESS
                            )
                        }

                        is ManualLightEvent.ShowError -> {
                            showDeviceSnack(
                                message = event.message,
                                type = DeviceFeedbackType.ERROR
                            )
                        }

                        is ManualLightEvent.SetLoading -> {
                            showDeviceLoading(
                                event.isLoading
                            )
                        }

                        ManualLightEvent.ShowSavePresetSheet -> {
                            SaveLightPresetBottomSheet.show(
                                fragment = this@DeviceLightManualFragment,
                                onSaveClick = viewModel::savePreset
                            )
                        }
                    }
                }
            }
        }
    }

    private fun renderUiState(
        state: ManualLightUiState
    ) {
        isRendering =
            true

        binding.tvManualModeTitle.text =
            when (state.controlMode) {
                ManualLightControlMode.AUTO -> "Auto Schedule"
                ManualLightControlMode.MANUAL_OVERRIDE -> "Manual Control"
                ManualLightControlMode.SCENE_OVERRIDE -> "Scene Applied"
            }

        binding.tvManualModeSubtitle.text =
            state.connectionStatusText

        binding.tvManualModeChip.text =
            when (state.controlMode) {
                ManualLightControlMode.AUTO -> "AUTO"
                ManualLightControlMode.MANUAL_OVERRIDE -> "MANUAL"
                ManualLightControlMode.SCENE_OVERRIDE -> "SCENE"
            }

        binding.tvManualModeChip.setBackgroundResource(
            when (state.controlMode) {
                ManualLightControlMode.AUTO -> R.drawable.bg_light_mode_chip_auto
                ManualLightControlMode.MANUAL_OVERRIDE -> R.drawable.bg_light_mode_chip_manual
                ManualLightControlMode.SCENE_OVERRIDE -> R.drawable.bg_light_mode_chip_scene
            }
        )

        binding.tvManualModeChip.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                when (state.controlMode) {
                    ManualLightControlMode.AUTO -> R.color.light_accent
                    ManualLightControlMode.MANUAL_OVERRIDE -> R.color.light_gold
                    ManualLightControlMode.SCENE_OVERRIDE -> R.color.light_gold
                }
            )
        )

        binding.tvOutputModeHint.text =
            state.outputHintText

        binding.powerProgress.progress =
            state.powerLoadPercent

        binding.powerProgress.setIndicatorColor(
            LightRgbwPreviewColorMath.previewColor(
                red = state.red,
                green = state.green,
                blue = state.blue,
                white = state.white
            )
        )

        binding.tvCurrentPower.text =
            state.powerText

        setSliderValueIfNeeded(
            slider = binding.sliderRed,
            value = state.red
        )

        setSliderValueIfNeeded(
            slider = binding.sliderGreen,
            value = state.green
        )

        setSliderValueIfNeeded(
            slider = binding.sliderBlue,
            value = state.blue
        )

        setSliderValueIfNeeded(
            slider = binding.sliderWhite,
            value = state.white
        )

        binding.tvRedValue.text =
            "${state.red}%"

        binding.tvGreenValue.text =
            "${state.green}%"

        binding.tvBlueValue.text =
            "${state.blue}%"

        binding.tvWhiteValue.text =
            "${state.white}%"

        renderSceneSelection(
            state
        )

        renderControlAvailability(
            state
        )

        isRendering =
            false
    }

    private fun renderControlAvailability(
        state: ManualLightUiState
    ) {
        val controlsEnabled = state.controlsEnabled
        val isAutoMode = state.controlMode == ManualLightControlMode.AUTO

        val outputAlpha = when {
            !controlsEnabled -> 0.48f
            isAutoMode -> 0.76f
            else -> 1f
        }

        binding.cardManualPowerControl.alpha =
            outputAlpha

        binding.cardQuickScenes.alpha =
            if (controlsEnabled) 1f else 0.48f

        binding.sliderRed.isEnabled = controlsEnabled
        binding.sliderGreen.isEnabled = controlsEnabled
        binding.sliderBlue.isEnabled = controlsEnabled
        binding.sliderWhite.isEnabled = controlsEnabled

        sceneButtons.forEach { button ->
            button.isEnabled = controlsEnabled
        }

        binding.btnResumeAuto.isEnabled =
            controlsEnabled && state.isManualOverrideActive

        binding.btnResumeAuto.text =
            if (state.isManualOverrideActive) {
                "Resume Auto"
            } else {
                "Auto Active"
            }

        binding.btnSavePreset.isEnabled =
            controlsEnabled
    }

    private fun setSliderValueIfNeeded(
        slider: Slider,
        value: Int
    ) {
        if (userControlledSliderIds.contains(slider.id)) {
            return
        }

        val safeValue =
            value
                .coerceIn(0, 100)
                .toFloat()

        if (slider.value != safeValue) {
            slider.value =
                safeValue
        }
    }

    private fun setupClicks() {
        binding.scenePlantGrowth.setOnClickListener {
            viewModel.applyScene(
                ManualLightScene.PLANT_GROWTH
            )
        }

        binding.sceneFishDisplay.setOnClickListener {
            viewModel.applyScene(
                ManualLightScene.FISH_DISPLAY
            )
        }

        binding.sceneShrimpSafe.setOnClickListener {
            viewModel.applyScene(
                ManualLightScene.SHRIMP_SAFE
            )
        }

        binding.sceneBlueAccent.setOnClickListener {
            viewModel.applyScene(
                ManualLightScene.BLUE_ACCENT
            )
        }

        binding.sceneRed.setOnClickListener {
            viewModel.applyScene(
                ManualLightScene.RED_ACCENT
            )
        }

        binding.sceneFullSpectrum.setOnClickListener {
            viewModel.applyScene(
                ManualLightScene.FULL_SPECTRUM
            )
        }

        binding.btnResumeAuto.setOnClickListener {
            viewModel.resumeAuto()
        }

        binding.btnSavePreset.setOnClickListener {
            viewModel.saveAs()
        }
    }

    private fun setupSliders() {
        val touchListener =
            object : Slider.OnSliderTouchListener {

                override fun onStartTrackingTouch(
                    slider: Slider
                ) {
                    userControlledSliderIds.add(slider.id)
                    viewModel.beginSliderInteraction()
                }

                override fun onStopTrackingTouch(
                    slider: Slider
                ) {
                    userControlledSliderIds.remove(slider.id)
                    viewModel.endSliderInteraction()
                }
            }

        binding.sliderRed.addOnSliderTouchListener(
            touchListener
        )

        binding.sliderGreen.addOnSliderTouchListener(
            touchListener
        )

        binding.sliderBlue.addOnSliderTouchListener(
            touchListener
        )

        binding.sliderWhite.addOnSliderTouchListener(
            touchListener
        )

        binding.sliderRed.addOnChangeListener { _, value, fromUser ->
            if (isRendering || !fromUser) return@addOnChangeListener

            clearSelectedSceneButton()

            viewModel.previewRed(
                value.toInt()
            )
        }

        binding.sliderGreen.addOnChangeListener { _, value, fromUser ->
            if (isRendering || !fromUser) return@addOnChangeListener

            clearSelectedSceneButton()

            viewModel.previewGreen(
                value.toInt()
            )
        }

        binding.sliderBlue.addOnChangeListener { _, value, fromUser ->
            if (isRendering || !fromUser) return@addOnChangeListener

            clearSelectedSceneButton()

            viewModel.previewBlue(
                value.toInt()
            )
        }

        binding.sliderWhite.addOnChangeListener { _, value, fromUser ->
            if (isRendering || !fromUser) return@addOnChangeListener

            clearSelectedSceneButton()

            viewModel.previewWhite(
                value.toInt()
            )
        }
    }

    private fun renderSceneSelection(
        state: ManualLightUiState
    ) {
        clearSelectedSceneButton()

        if (!state.isManualScene) {
            return
        }

        val sceneName =
            state.activeSceneName
                .orEmpty()
                .lowercase()

        val selectedButton =
            when {
                sceneName.contains("plant") -> binding.scenePlantGrowth
                sceneName.contains("fish") -> binding.sceneFishDisplay
                sceneName.contains("shrimp") -> binding.sceneShrimpSafe
                sceneName.contains("blue") -> binding.sceneBlueAccent
                sceneName.contains("red") -> binding.sceneRed
                sceneName.contains("full") -> binding.sceneFullSpectrum
                else -> null
            }

        selectedButton?.isSelected =
            true
    }

    private fun clearSelectedSceneButton() {
        sceneButtons.forEach { button ->
            button.isSelected =
                false
        }
    }

    override fun onDestroyView() {
        userControlledSliderIds.clear()

        _binding =
            null

        super.onDestroyView()
    }

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
    }
}
