package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentTankHealthAnalysisAddBinding
import com.aqua.aqualight.ui.common.dialog.ConfirmDialogFragment
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import com.aqua.aqualight.utils.DialogType

class TankHealthAnalysisAddFragment :
    Fragment(R.layout.fragment_tank_health_analysis_add) {

    private val args: TankHealthAnalysisAddFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
    private val waterAnalysisViewModel: WaterAnalysisViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }

    private var _binding: FragmentTankHealthAnalysisAddBinding? = null
    private val binding get() = _binding!!

    private var tankProfile: String? = null
    private val parameterState = WaterAnalysisParameterState()
    private var requestId: String = ""

    private var measurementTimeController: WaterAnalysisMeasurementTimeController? = null
    private var temperatureUiController: WaterAnalysisTemperatureUiController? = null
    private var parameterRenderer: WaterAnalysisParameterRenderer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "TankHealthAnalysisAddFragment requires a positive tankId."
        }
        val restored = waterAnalysisViewModel.draft ?: savedInstanceState
        restoreWaterTestState(restored)
        requestId = restored?.getString(STATE_REQUEST_ID) ?: waterAnalysisViewModel.requestId
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankHealthAnalysisAddBinding.bind(view)

        measurementTimeController = WaterAnalysisMeasurementTimeController(
            fragment = this,
            binding = binding.measurementTimeSection,
            savedInstanceState = waterAnalysisViewModel.draft ?: savedInstanceState
        ).also { controller -> controller.bind() }

        temperatureUiController = WaterAnalysisTemperatureUiController(
            fragment = this,
            binding = binding.sensorSection,
            savedInstanceState = waterAnalysisViewModel.draft ?: savedInstanceState
        ).also { controller -> controller.bind() }

        parameterRenderer = WaterAnalysisParameterRenderer(
            fragment = this,
            binding = binding.waterParametersSection,
            state = parameterState
        )

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_tank_health_analysis_add),
                onBackClick = { findNavController().navigateUp() }
            )
        )
        WaterAnalysisSelectionResultBinder(
            fragment = this,
            state = parameterState,
            tankProfile = { tankProfile },
            renderer = { parameterRenderer }
        ).bind()
        bindHiddenDraftConfirmation()
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
        observeTankProfile()
        bindWaterAnalysisMutation(waterAnalysisViewModel.mutations, R.id.tankHealthAnalysisAddFragment) { state ->
            binding.btnSaveAnalysis.isEnabled = state == WaterAnalysisMutationState.Idle
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureDraft(outState)
        waterAnalysisViewModel.draft = Bundle(outState)
        super.onSaveInstanceState(outState)
    }

    private fun captureDraft(outState: Bundle) {
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
        outState.putString(STATE_REQUEST_ID, requestId)
        outState.putString(
            STATE_ACTIVE_MEASUREMENT_PARAMETER_ID,
            parameterState.activeMeasurementParameterId?.name
        )
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

    private fun saveAnalysis(hiddenDraftReviewed: Boolean = false) {
        val profile = tankProfile ?: run {
            (activity as? BaseActivity)?.showSnackBar(
                message = getString(R.string.tank_health_analysis_profile_missing),
                type = BaseActivity.SnackType.WARNING
            )
            return
        }
        val timeController = requireNotNull(measurementTimeController)
        val temperatureController = requireNotNull(temperatureUiController)
        val visibleParameterIds = (
            WaterTestProfileUiCatalog.recommendedIds(profile) +
                WaterTestProfileUiCatalog.additionalIds(profile)
                    .filter(parameterState.additionalParameters::contains)
        ).toSet()
        val hiddenValues = parameterState.parameterValues.filter { (id, value) ->
            id !in visibleParameterIds && value.isNotBlank()
        }
        if (hiddenValues.isNotEmpty() && !hiddenDraftReviewed) {
            ConfirmDialogFragment.show(
                fragmentManager = childFragmentManager,
                request = ConfirmDialogFragment.Request(
                    title = getString(R.string.water_analysis_hidden_draft_title),
                    message = getString(R.string.water_analysis_hidden_draft_message),
                    confirmText = getString(R.string.confirm),
                    cancelText = getString(R.string.cancel),
                    presentation = ConfirmDialogFragment.Presentation(DialogType.WARNING),
                    resultTarget = ConfirmDialogFragment.ResultTarget(
                        requestKey = HIDDEN_DRAFT_REQUEST_KEY,
                        actionId = args.tankId.toString()
                    )
                )
            )
            return
        }
        val buildResult = WaterAnalysisInputBuilder.build(
            WaterAnalysisInputBuildRequest(
                tankId = args.tankId,
                measuredAtMillis = timeController.measurementTimeMillis(),
                temperatureText = temperatureController.currentValueText(),
                temperatureSource = temperatureController.currentDomainSource(),
                visibleParameterIds = visibleParameterIds,
                parameterState = parameterState,
                requestId = requestId
            )
        )

        when (buildResult) {
            is WaterAnalysisInputBuildResult.Failure -> showInputError(buildResult)
            is WaterAnalysisInputBuildResult.Success -> {
                waterAnalysisViewModel.draft = Bundle().also(::captureDraft)
                waterAnalysisViewModel.saveAnalysis(buildResult.input)
            }
        }
    }

    private fun bindHiddenDraftConfirmation() {
        childFragmentManager.setFragmentResultListener(
            HIDDEN_DRAFT_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(ConfirmDialogFragment.RESULT_KEY) ==
                ConfirmDialogFragment.RESULT_CONFIRM &&
                result.getString(ConfirmDialogFragment.RESULT_ACTION_ID) == args.tankId.toString()
            ) {
                saveAnalysis(hiddenDraftReviewed = true)
            }
        }
    }

    private fun showInputError(failure: WaterAnalysisInputBuildResult.Failure) {
        parameterState.invalidParameterId =
            (failure as? WaterAnalysisInputBuildResult.Failure.InvalidParameterValue)?.parameterId
        parameterRenderer?.render(tankProfile)
        binding.sensorSection.inputTemperature.error =
            if (failure == WaterAnalysisInputBuildResult.Failure.InvalidTemperature) {
                getString(R.string.tank_health_analysis_invalid_temperature)
            } else null
        val message = when (failure) {
            WaterAnalysisInputBuildResult.Failure.MeasurementRequired ->
                getString(R.string.tank_health_analysis_measurement_required)
            WaterAnalysisInputBuildResult.Failure.InvalidMeasurementSelection ->
                getString(R.string.tank_health_analysis_invalid_measurement_selection)
            WaterAnalysisInputBuildResult.Failure.InvalidTemperature ->
                getString(R.string.tank_health_analysis_invalid_temperature)
            is WaterAnalysisInputBuildResult.Failure.InvalidParameterValue -> {
                val name = tankProfile?.let {
                    WaterTestProfileUiCatalog.model(
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
            val visibleParameterIds = linkedSetOf<WaterTestParameterId>()
            if (nextProfile != null) {
                visibleParameterIds.addAll(
                    WaterTestProfileUiCatalog.recommendedIds(nextProfile)
                )
            }
            visibleParameterIds.addAll(
                nextProfile
                    ?.let(WaterTestProfileUiCatalog::additionalIds)
                    .orEmpty()
                    .filter(parameterState.additionalParameters::contains)
            )
            // Keep hidden draft input when the tank type changes; only visible
            // fields enter the event assembled above.

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
        if (_binding != null) waterAnalysisViewModel.draft = Bundle().also(::captureDraft)
        measurementTimeController = null
        temperatureUiController = null
        parameterRenderer = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val STATE_REQUEST_ID = "water_analysis_request_id"
        const val HIDDEN_DRAFT_REQUEST_KEY = "water_analysis_hidden_draft_confirmation"
        const val STATE_ADDITIONAL_PARAMETER_IDS = "additional_parameter_ids"
        const val STATE_PARAMETER_VALUE_IDS = "parameter_value_ids"
        const val STATE_PARAMETER_VALUES = "parameter_values"
        const val STATE_ACTIVE_MEASUREMENT_PARAMETER_ID =
            "active_measurement_parameter_id"
    }
}
