package com.aqua.aqualight.ui.tabs.devices.detail.light.manual

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightManualBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightScene
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.model.ManualLightUiState
import com.aqua.aqualight.ui.tabs.devices.detail.light.manual.sheet.SaveLightPresetBottomSheet
import kotlinx.coroutines.launch
import android.graphics.Color

class DeviceLightManualFragment :
Fragment(R.layout.fragment_device_light_manual) {

    private var _binding: FragmentDeviceLightManualBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeviceLightManualViewModel by viewModels()

    private var isRendering = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightManualBinding.bind(view)

        setupHeader()
        setupClicks()
        setupSliders()
        observeUiState()
        observeEvents()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            AquaHeaderConfig(
                title = "Manual Control",
                showBackButton = true,
                onBackClick = {
                    findNavController().popBackStack()
                }
            )
        )
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect {
                    state ->
                    renderUiState(state)
                }
            }
        }
    }

    private fun observeEvents() {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect {
                event ->
                when (event) {
                    is ManualLightEvent.ShowMessage -> {
                        Toast.makeText(
                            requireContext(),
                            event.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    is ManualLightEvent.ShowError -> {
                        Toast.makeText(
                            requireContext(),
                            event.message,
                            Toast.LENGTH_SHORT
                        ).show()
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
        isRendering = true

        binding.switchManualPower.isChecked = state.isPowerOn

        binding.tvManualModeSubtitle.text = if (state.isManualMode) {
            "Live RGBW control. Automatic schedule paused."
        } else {
            "Automatic schedule is running."
        }

        binding.masterOutputProgress.progress = state.masterOutputPercent
        binding.masterOutputProgress.setIndicatorColor(
            Color.rgb(
                state.previewRed,
                state.previewGreen,
                state.previewBlue
            )
        )

        binding.tvEstimatedPower.text = if (state.hasPowerCalibration) {
            "%.1fW".format(state.estimatedPowerWatts)
        } else {
            "-- W"
        }
        binding.sliderRed.value = state.red.toFloat()
        binding.sliderGreen.value = state.green.toFloat()
        binding.sliderBlue.value = state.blue.toFloat()
        binding.sliderWhite.value = state.white.toFloat()

        binding.tvRedValue.text = "${state.red}%"
        binding.tvGreenValue.text = "${state.green}%"
        binding.tvBlueValue.text = "${state.blue}%"
        binding.tvWhiteValue.text = "${state.white}%"

        binding.manualContentContainer.alpha = if (state.isPowerOn) {
            1f
        } else {
            0.72f
        }

        isRendering = false
    }

    private fun setupClicks() {
        binding.switchManualPower.setOnCheckedChangeListener {
            _, isChecked ->
            if (isRendering) return@setOnCheckedChangeListener
            viewModel.setPowerOn(isChecked)
        }

        binding.scenePlantGrowth.setOnClickListener {
            viewModel.applyScene(ManualLightScene.PLANT_GROWTH)
        }

        binding.sceneFishDisplay.setOnClickListener {
            viewModel.applyScene(ManualLightScene.FISH_DISPLAY)
        }

        binding.sceneShrimpSafe.setOnClickListener {
            viewModel.applyScene(ManualLightScene.SHRIMP_SAFE)
        }

        binding.sceneBlueAccent.setOnClickListener {
            viewModel.applyScene(ManualLightScene.BLUE_ACCENT)
        }

        binding.sceneRed.setOnClickListener {
            viewModel.applyScene(ManualLightScene.RED_ACCENT)
        }

        binding.sceneFullSpectrum.setOnClickListener {
            viewModel.applyScene(ManualLightScene.FULL_SPECTRUM)
        }

        binding.btnResumeAuto.setOnClickListener {
            viewModel.resumeAuto()
        }

        binding.btnSavePreset.setOnClickListener {
            viewModel.saveAs()
        }
    }

    private fun setupSliders() {
        binding.sliderRed.addOnChangeListener {
            _, value, _ ->
            if (isRendering) return@addOnChangeListener
            viewModel.updateRed(value.toInt())
        }

        binding.sliderGreen.addOnChangeListener {
            _, value, _ ->
            if (isRendering) return@addOnChangeListener
            viewModel.updateGreen(value.toInt())
        }

        binding.sliderBlue.addOnChangeListener {
            _, value, _ ->
            if (isRendering) return@addOnChangeListener
            viewModel.updateBlue(value.toInt())
        }

        binding.sliderWhite.addOnChangeListener {
            _, value, _ ->
            if (isRendering) return@addOnChangeListener
            viewModel.updateWhite(value.toInt())
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}