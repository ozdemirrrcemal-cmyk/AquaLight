package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import com.aqua.aqualight.application.care.CareTaskType
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSemanticStatus
import com.aqua.aqualight.databinding.FragmentTankHealthBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.text.resolve
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import com.aqua.aqualight.ui.tabs.maintenance.MaintenanceViewModel
import com.aqua.aqualight.ui.tabs.maintenance.TankActivityUiState
import kotlinx.coroutines.launch

class TankHealthFragment : Fragment(R.layout.fragment_tank_health) {

    private val args: TankHealthFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
    private val waterAnalysisViewModel: WaterAnalysisViewModel by viewModels()
    private val maintenanceViewModel: MaintenanceViewModel by activityViewModels()

    private var _binding: FragmentTankHealthBinding? = null
    private val binding get() = _binding!!
    private var contentAdapter: TankHealthContentAdapter? = null

    private var currentTankProfile: String? = null
    private var currentAnalyses: List<WaterAnalysisSnapshot> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "TankHealthFragment requires a positive tankId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankHealthBinding.bind(view)

        setupHeader()
        setupContent()
        observeTankHealth()
        observeMaintenance()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_tank_health),
                onBackClick = { findNavController().navigateUp() }
            )
        )
    }

    private fun setupContent() {
        val adapter = TankHealthContentAdapter(onAddAnalysisClick = ::openAddAnalysis)
        contentAdapter = adapter

        binding.healthContent.layoutManager = GridLayoutManager(
            requireContext(),
            TankHealthContentAdapter.GRID_SPAN_COUNT
        ).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    adapter.spanSizeForPosition(position)
            }
        }
        binding.healthContent.adapter = adapter
        binding.healthContent.itemAnimator = null
    }

    private fun observeTankHealth() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            maintenanceViewModel.setTanks(tanks)
            val tank = tanks.firstOrNull { candidate -> candidate.id == args.tankId }
            currentTankProfile = tank
                ?.tankType
                ?.takeIf(AquariumTankTaxonomy::isSupportedTankType)
            contentAdapter?.submitSystem(TankHealthOverviewProjection.system(tank))
            renderWaterMetrics()
        }

        waterAnalysisViewModel.analysesStateForTank(args.tankId)
            .observe(viewLifecycleOwner) { state ->
                currentAnalyses = (state as? WaterAnalysisLoadState.Content)?.value.orEmpty()
                renderWaterMetrics()
                contentAdapter?.waterReadStatus = when (state) {
                    WaterAnalysisLoadState.Loading -> R.string.water_analysis_loading
                    is WaterAnalysisLoadState.Error -> R.string.water_analysis_read_failed
                    is WaterAnalysisLoadState.Content -> null
                }
            }
    }

    private fun observeMaintenance() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                maintenanceViewModel.tankActivityStateFlow(args.tankId).collect { state ->
                    contentAdapter?.submitMaintenance(state.toHealthMaintenanceUi())
                }
            }
        }
    }

    private fun TankActivityUiState.toHealthMaintenanceUi(): TankHealthMaintenanceUi {
        val completedTypes = completedTasks
            .filter { task -> task.completedAtMillis?.let { it > 0L } == true }
            .map { task -> task.type }
            .toSet()
        val context = requireContext()
        return TankHealthMaintenanceUi(
            waterChangeText = lastWaterChangeText.takeIf {
                CareTaskType.WATER_CHANGE in completedTypes
            }?.let(context::resolve)?.toString(),
            pruningText = lastTrimText.takeIf {
                CareTaskType.PLANT_TRIM in completedTypes
            }?.let(context::resolve)?.toString(),
            filterText = lastFilterMaintenanceText.takeIf {
                completedTypes.any { type -> type in FILTER_CARE_TYPES }
            }?.let(context::resolve)?.toString()
        )
    }

    private fun renderWaterMetrics() {
        val profile = currentTankProfile.orEmpty()

        val latestMeasurements = WaterAnalysisLatestMeasurements.from(currentAnalyses)
        val models = TankHealthWaterMetricUiCatalog.models(
            tankProfile = profile,
            measuredParameterIds = latestMeasurements.keys.map(WaterParameter::toUiParameterId)
        ).map { model ->
            val measurement = latestMeasurements[model.id.toDomainParameter()]
            if (measurement == null) {
                model
            } else {
                model.copy(
                    symbolRes = WaterAnalysisPresentation.measurementSymbolRes(measurement),
                    valueText = WaterAnalysisPresentation.measurementValueText(
                        requireContext(),
                        measurement
                    ),
                    statusText = getString(
                        if (measurement.semanticStatus ==
                            WaterMeasurementSemanticStatus.LEGACY_UNASSESSED
                        ) {
                            R.string.water_measurement_legacy_unassessed
                        } else {
                            R.string.tank_health_analysis_recorded
                        }
                    )
                )
            }
        }
        contentAdapter?.submitWaterMetrics(
            metrics = models,
            measuredAtMillis = WaterAnalysisLatestMeasurements.latestEvent(currentAnalyses)
                ?.measuredAtMillis,
            assessmentSummary = WaterAnalysisLatestMeasurements.latestEvent(currentAnalyses)
                ?.let { WaterAssessmentPresentation.summary(requireContext(), it) }
        )
    }

    private fun openAddAnalysis() {
        findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.tankHealthFragment,
            directions = TankHealthFragmentDirections
                .actionTankHealthFragmentToTankHealthAnalysisAddFragment(args.tankId)
        )
    }

    override fun onDestroyView() {
        binding.healthContent.adapter = null
        contentAdapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        val FILTER_CARE_TYPES = setOf(
            CareTaskType.FILTER_MAINTENANCE,
            CareTaskType.FILTER_CHANGE,
            CareTaskType.PRE_FILTER_CLEANING,
            CareTaskType.PIPE_CLEANING,
            CareTaskType.DIFFUSER_CLEANING,
            CareTaskType.HOSE_CLEANING
        )
    }
}
