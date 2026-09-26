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

    private val uiState = WaterAnalysisDraftUiState()
    private var tankProfile: String? = null

    private var measurementController: WaterAnalysisMeasurementTimeController? = null
    private var sensorController: WaterAnalysisSensorController? = null
    private var parameterController: WaterAnalysisParameterController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "TankHealthAnalysisAddFragment requires a positive tankId."
        }
        uiState.restore(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankHealthAnalysisAddBinding.bind(view)

        measurementController = WaterAnalysisMeasurementTimeController(
            fragment = this,
            binding = binding.measurementTimeSection,
            state = uiState
        )
        sensorController = WaterAnalysisSensorController(
            fragment = this,
            binding = binding.sensorSection,
            state = uiState
        )
        parameterController = WaterAnalysisParameterController(
            fragment = this,
            binding = binding.waterParametersSection,
            state = uiState
        )

        setupHeader()
        measurementController?.bind()
        sensorController?.bind()
        parameterController?.bindPickerResultListener()
        setupNavigation()
        observeTankProfile()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        uiState.save(outState)
        super.onSaveInstanceState(outState)
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_tank_health_analysis_add),
                onBackClick = {
                    findNavController().navigateUp()
                }
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

        // Persistence is intentionally deferred to the data-integration stage.
        binding.btnSaveAnalysis.setOnClickListener { }
    }

    private fun observeTankProfile() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val nextProfile = tanks
                .firstOrNull { tank -> tank.id == args.tankId }
                ?.tankType
                ?.takeIf(AquariumTankTaxonomy::isSupportedTankType)

            if (nextProfile != tankProfile) {
                tankProfile = nextProfile
                val allowedAdditional = nextProfile
                    ?.let(WaterTestProfileUiCatalog::additionalIds)
                    .orEmpty()
                    .toSet()
                uiState.additionalParameters.retainAll(allowedAdditional)
            }

            parameterController?.render(nextProfile)
        }
    }

    override fun onDestroyView() {
        measurementController = null
        sensorController = null
        parameterController = null
        _binding = null
        super.onDestroyView()
    }
}
