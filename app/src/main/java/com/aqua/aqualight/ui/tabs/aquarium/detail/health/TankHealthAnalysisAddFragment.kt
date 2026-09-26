package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentTankHealthAnalysisAddBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import kotlinx.coroutines.launch

class TankHealthAnalysisAddFragment :
    Fragment(R.layout.fragment_tank_health_analysis_add) {

    private val args: TankHealthAnalysisAddFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
    private val waterAnalysisViewModel: WaterAnalysisViewModel by activityViewModels()

    private var _binding: FragmentTankHealthAnalysisAddBinding? = null
    private val binding get() = _binding!!

    private var tankProfile: String? = null
    private val parameterState = WaterAnalysisParameterState()

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
            state = parameterState
        )

        setupHeader()
        WaterAnalysisSelectionResultBinder(
            fragment = this,
            state = parameterState,
            tankProfile = { tankProfile },
            renderer = { parameterRenderer }
        ).bind()
        setupNavigation()
        observeTankProfile()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        measurementTimeController?.saveState(outState)
        temperatureUiController?.saveState(outState)
        outState.putStringArrayList(
            STATE_ADDITIONAL_PARAMETER_IDS,
            ArrayList(
                parameterState.additionalParameters.map { parameterId -> parameterId.name }
            )
        )
        outState.putStringArrayList(
            STATE_PARAMETER_VALUE_IDS,
            ArrayList(parameterState.parameterValues.keys.map { parameterId -> parameterId.name })
        )
        outState.putStringArrayList(
            STATE_PARAMETER_VALUES,
            ArrayList(parameterState.parameterValues.values)
        )
        WaterMeasurementUiStateCodec.save(outState, parameterState.measurementSelections)
        outState.putString(
            STATE_ACTIVE_MEASUREMENT_PARAMETER_ID,
            parameterState.activeMeasurementParameterId?.name
        )
        super.onSaveInstanceState(outState)
    }

    private fun restoreWaterTestState(savedInstanceState: Bundle?) {
        savedInstanceState?.getStringArrayList(STATE_ADDITIONAL_PARAMETER_IDS)
            .orEmpty()
            .mapNotNull { rawId ->
                runCatching { WaterTestParameterId.valueOf(rawId) }.getOrNull()
            }
            .forEach(parameterState.additionalParameters::add)

        val valueIds = savedInstanceState
            ?.getStringArrayList(STATE_PARAMETER_VALUE_IDS)
            .orEmpty()
        val values = savedInstanceState
            ?.getStringArrayList(STATE_PARAMETER_VALUES)
            .orEmpty()
        valueIds.zip(values).forEach { (rawId, value) ->
            runCatching { WaterTestParameterId.valueOf(rawId) }
                .getOrNull()
                ?.let { parameterId ->
                    parameterState.parameterValues[parameterId] = value
                }
        }

        WaterMeasurementUiStateCodec.restore(
            savedInstanceState,
            parameterState.measurementSelections
        )
        parameterState.activeMeasurementParameterId = savedInstanceState
            ?.getString(STATE_ACTIVE_MEASUREMENT_PARAMETER_ID)
            ?.let { rawId ->
                runCatching { WaterTestParameterId.valueOf(rawId) }.getOrNull()
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
        binding.btnSaveAnalysis.setOnClickListener { saveAnalysis() }
    }

    private fun saveAnalysis() {
        val timeController = requireNotNull(measurementTimeController)
        val temperatureController = requireNotNull(temperatureUiController)
        val buildResult = WaterAnalysisInputBuilder.build(
            WaterAnalysisInputBuildRequest(
                tankId = args.tankId,
                measuredAtMillis = timeController.measurementTimeMillis(),
                temperatureText = temperatureController.currentValueText(),
                temperatureSource = temperatureController.currentDomainSource(),
                parameterState = parameterState
            )
        )

        when (buildResult) {
            is WaterAnalysisInputBuildResult.Failure -> showInputError(buildResult)
            is WaterAnalysisInputBuildResult.Success -> {
                binding.btnSaveAnalysis.isEnabled = false
                viewLifecycleOwner.lifecycleScope.launch {
                    runCatching {
                        waterAnalysisViewModel.saveAnalysis(buildResult.input)
                    }.onSuccess {
                        if (_binding != null) {
                            findNavController().navigateUp()
                        }
                    }.onFailure {
                        _binding?.btnSaveAnalysis?.isEnabled = true
                        (activity as? BaseActivity)?.showSnackBar(
                            message = getString(R.string.tank_health_analysis_save_failed),
                            type = BaseActivity.SnackType.ERROR
                        )
                    }
                }
            }
        }
    }

    private fun showInputError(failure: WaterAnalysisInputBuildResult.Failure) {
        val message = when (failure) {
            WaterAnalysisInputBuildResult.Failure.MeasurementRequired ->
                getString(R.string.tank_health_analysis_measurement_required)
            WaterAnalysisInputBuildResult.Failure.InvalidMeasurementSelection ->
                getString(R.string.tank_health_analysis_invalid_measurement_selection)
            WaterAnalysisInputBuildResult.Failure.InvalidTemperature ->
                getString(R.string.tank_health_analysis_invalid_temperature)
            is WaterAnalysisInputBuildResult.Failure.InvalidParameterValue -> {
                val name = tankProfile?.let { profile ->
                    WaterTestProfileUiCatalog.model(
                        tankProfile = profile,
                        id = failure.parameterId,
                        importance = WaterTestImportance.RECOMMENDED,
                        value = ""
                    ).nameRes
                }?.let(::getString) ?: failure.parameterId.name
                getString(R.string.tank_health_analysis_invalid_parameter_value, name)
            }
        }

        (activity as? BaseActivity)?.showSnackBar(
            message = message,
            type = BaseActivity.SnackType.WARNING
        )
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
            parameterState.additionalParameters.retainAll(allowedAdditional)

            val visibleParameterIds = linkedSetOf<WaterTestParameterId>()
            if (nextProfile != null) {
                visibleParameterIds.addAll(
                    WaterTestProfileUiCatalog.recommendedIds(nextProfile)
                )
            }
            visibleParameterIds.addAll(parameterState.additionalParameters)
            parameterState.parameterValues.keys.retainAll(visibleParameterIds)
            parameterState.measurementSelections.keys.retainAll(visibleParameterIds)

            parameterState.activeMeasurementParameterId
                ?.takeIf { activeId -> activeId !in visibleParameterIds }
                ?.let { activeId ->
                    if (parameterRenderer?.clearActiveMeasurementParameter(activeId) != true) {
                        parameterState.activeMeasurementParameterId = null
                    }
                }

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
        const val STATE_ACTIVE_MEASUREMENT_PARAMETER_ID =
            "active_measurement_parameter_id"
    }
}
