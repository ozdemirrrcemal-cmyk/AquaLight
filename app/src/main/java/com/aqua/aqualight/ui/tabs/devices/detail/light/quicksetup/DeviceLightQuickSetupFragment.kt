package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightQuickSetupBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.DeviceLightQuickSetupEvent
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupRecommendation
import com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model.QuickSetupUiState
import kotlinx.coroutines.launch

class DeviceLightQuickSetupFragment :
    Fragment(R.layout.fragment_device_light_quick_setup) {

    private var _binding: FragmentDeviceLightQuickSetupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeviceLightQuickSetupViewModel by viewModels()

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID, 0L)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightQuickSetupBinding.bind(view)

        setupHeader()
        setupClicks()
        observeUiState()
        observeEvents()

        viewModel.initialize(deviceId)
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            AquaHeaderConfig(
                title = "Smart Quick Setup",
                showBackButton = true,
                onBackClick = {
                    findNavController().popBackStack()
                }
            )
        )
    }

    private fun setupClicks() {
        binding.btnSaveProgram.setOnClickListener {
            viewModel.saveProgram()
        }

        binding.btnLoadToDevice.setOnClickListener {
            viewModel.loadToDevice()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUiState(state)
                }
            }
        }
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is DeviceLightQuickSetupEvent.ShowMessage -> {
                            Toast.makeText(
                                requireContext(),
                                event.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        is DeviceLightQuickSetupEvent.ShowError -> {
                            Toast.makeText(
                                requireContext(),
                                event.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        DeviceLightQuickSetupEvent.NavigateBack -> {
                            findNavController().popBackStack()
                        }
                    }
                }
            }
        }
    }

    private fun renderUiState(
        state: QuickSetupUiState
    ) {
        val recommendation = state.recommendation

        if (recommendation == null) {
            renderNoLinkedTank(
                message = state.errorMessage
                    ?: "Link this light device to a tank to generate a smart lighting setup."
            )
            return
        }

        binding.noLinkedTankContainer.visibility = View.GONE
        binding.recommendationContainer.visibility = View.VISIBLE
        binding.bottomActionsBar.visibility = View.VISIBLE

        renderRecommendation(recommendation)
    }

    private fun renderNoLinkedTank(
        message: String
    ) {
        binding.noLinkedTankContainer.visibility = View.VISIBLE
        binding.recommendationContainer.visibility = View.GONE
        binding.bottomActionsBar.visibility = View.GONE

        binding.tvNoLinkedTankMessage.text = message
    }

    private fun renderRecommendation(
        recommendation: QuickSetupRecommendation
    ) {
        binding.tvRecommendationTitle.text = recommendation.title
        binding.tvRecommendationSubtitle.text =
            "${recommendation.profileLabel} · ${recommendation.goalLabel}"

        binding.tvDurationValue.text = recommendation.durationLabel
        binding.tvIntensityValue.text = recommendation.intensityLabel

        renderTextList(
            container = binding.tankSummaryContainer,
            items = recommendation.tankSummary
        )

        binding.tvProgramGoal.text =
            "${recommendation.goalLabel} · ${recommendation.confidenceLabel}"

        binding.tvStartTime.text = recommendation.start.label
        binding.tvPeakStartTime.text = recommendation.peakStart.label
        binding.tvPeakEndTime.text = recommendation.peakEnd.label
        binding.tvEndTime.text = recommendation.end.label

        binding.tvRedValue.text = "R${recommendation.channelValues.red}"
        binding.tvGreenValue.text = "G${recommendation.channelValues.green}"
        binding.tvBlueValue.text = "B${recommendation.channelValues.blue}"
        binding.tvWhiteValue.text = "W${recommendation.channelValues.white}"

        renderTextList(
            container = binding.reasoningContainer,
            items = recommendation.reasoningNotes
        )

        if (recommendation.warnings.isEmpty()) {
            binding.cardWarnings.visibility = View.GONE
        } else {
            binding.cardWarnings.visibility = View.VISIBLE

            renderTextList(
                container = binding.warningsContainer,
                items = recommendation.warnings
            )
        }
    }

    private fun renderTextList(
        container: LinearLayout,
        items: List<String>
    ) {
        container.removeAllViews()

        items.forEach { text ->
            val textView = TextView(requireContext()).apply {
                setTextAppearance(R.style.TextAppearance_Aqua_Light_Body)
                this.text = "• $text"

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8.dp()
                }
            }

            container.addView(textView)
        }
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_DEVICE_ID = "deviceId"
    }
}