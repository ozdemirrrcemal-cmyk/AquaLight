package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import com.aqua.aqualight.databinding.FragmentTankHealthAnalysisAddBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

class TankHealthAnalysisAddFragment :
    Fragment(R.layout.fragment_tank_health_analysis_add) {

    private val args: TankHealthAnalysisAddFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var _binding: FragmentTankHealthAnalysisAddBinding? = null
    private val binding get() = _binding!!

    private var tankProfile: String? = null
    private val parameterValues = linkedMapOf<WaterTestParameterId, String>()
    private val additionalParameters = linkedSetOf<WaterTestParameterId>()

    private var measurementTimeController: WaterAnalysisMeasurementTimeController? = null
    private var temperatureUiController: WaterAnalysisTemperatureUiController? = null
    private var parameterRenderer: WaterAnalysisParameterRenderer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "TankHealthAnalysisAddFragment requires a positive tankId."
        }
        restoreWaterTestState(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankHealthAnalysisAddBinding.bind(view)

        measurementTimeController = WaterAnalysisMeasurementTimeController(
            fragment = this,
            binding = binding.measurementTimeSection,
            savedInstanceState = savedInstanceState
        ).also { controller -> controller.bind() }

        temperatureUiController = WaterAnalysisTemperatureUiController(
            fragment = this,
            binding = binding.sensorSection,
            savedInstanceState = savedInstanceState
        ).also { controller -> controller.bind() }

        parameterRenderer = WaterAnalysisParameterRenderer(
            fragment = this,
            binding = binding.waterParametersSection,
            parameterValues = parameterValues,
            additionalParameters = additionalParameters
        )

        setupHeader()
        setupWaterTestPickerResultListener()
        setupNavigation()
        observeTankProfile()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        measurementTimeController?.saveState(outState)
        temperatureUiController?.saveState(outState)
        outState.putStringArrayList(
            STATE_ADDITIONAL_PARAMETER_IDS,
            ArrayList(additionalParameters.map { parameterId -> parameterId.name })
        )
        outState.putStringArrayList(
            STATE_PARAMETER_VALUE_IDS,
            ArrayList(parameterValues.keys.map { parameterId -> parameterId.name })
        )
        outState.putStringArrayList(
            STATE_PARAMETER_VALUES,
            ArrayList(parameterValues.values)
        )
        super.onSaveInstanceState(outState)
    }

    private fun restoreWaterTestState(savedInstanceState: Bundle?) {
        savedInstanceState?.getStringArrayList(STATE_ADDITIONAL_PARAMETER_IDS)
            .orEmpty()
            .mapNotNull { rawId ->
                runCatching { WaterTestParameterId.valueOf(rawId) }.getOrNull()
            }
            .forEach(additionalParameters::add)

        val valueIds = savedInstanceState
            ?.getStringArrayList(STATE_PARAMETER_VALUE_IDS)
            .orEmpty()
        val values = savedInstanceState
            ?.getStringArrayList(STATE_PARAMETER_VALUES)
            .orEmpty()
        valueIds.zip(values).forEach { (rawId, value) ->
            runCatching { WaterTestParameterId.valueOf(rawId) }
                .getOrNull()
                ?.let { parameterId -> parameterValues[parameterId] = value }
        }
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_tank_health_analysis_add),
                onBackClick = { findNavController().navigateUp() }
            )
        )
    }

    private fun setupWaterTestPickerResultListener() {
        childFragmentManager.setFragmentResultListener(
            WaterTestPickerBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            val parameterId = result.getString(WaterTestPickerBottomSheet.RESULT_PARAMETER_ID)
                ?.let { rawId ->
                    runCatching { WaterTestParameterId.valueOf(rawId) }.getOrNull()
                }
                ?: return@setFragmentResultListener
            val profile = tankProfile ?: return@setFragmentResultListener
            if (parameterId !in WaterTestProfileUiCatalog.additionalIds(profile)) {
                return@setFragmentResultListener
            }

            additionalParameters.add(parameterId)
            parameterRenderer?.render(profile)
        }
    }

    private fun setupNavigation() {
        binding.btnHistory.setOnClickListener {
            findNavController().navigateSafelyFrom(
                sourceDestinationId = R.id.tankHealthAnalysisAddFragment,
                directions = TankHealthAnalysisAddFragmentDirections
                    .actionTankHealthAnalysisAddFragmentToTankHealthAnalysisHistoryFragment(
                        args.tankId
                    )
            )
        }

        // Persistence is intentionally deferred to the data-integration stage.
        binding.btnSaveAnalysis.setOnClickListener { }
    }

    private fun observeTankProfile() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val nextProfile = tanks
                .firstOrNull { tank -> tank.id == args.tankId }
                ?.tankType
                ?.takeIf(AquariumTankTaxonomy::isSupportedTankType)

            if (nextProfile == tankProfile) {
                parameterRenderer?.render(nextProfile)
                return@observe
            }

            tankProfile = nextProfile
            val allowedAdditional = nextProfile
                ?.let(WaterTestProfileUiCatalog::additionalIds)
                .orEmpty()
                .toSet()
            additionalParameters.retainAll(allowedAdditional)
            parameterRenderer?.render(nextProfile)
        }
    }

    override fun onDestroyView() {
        measurementTimeController = null
        temperatureUiController = null
        parameterRenderer = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val STATE_ADDITIONAL_PARAMETER_IDS = "additional_parameter_ids"
        const val STATE_PARAMETER_VALUE_IDS = "parameter_value_ids"
        const val STATE_PARAMETER_VALUES = "parameter_values"
    }
}
