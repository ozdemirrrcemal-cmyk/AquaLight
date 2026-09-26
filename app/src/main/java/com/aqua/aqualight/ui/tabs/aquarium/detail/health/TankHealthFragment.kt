package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.databinding.FragmentTankHealthBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

class TankHealthFragment : Fragment(R.layout.fragment_tank_health) {

    private val args: TankHealthFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
    private val waterAnalysisViewModel: WaterAnalysisViewModel by activityViewModels()

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
            currentTankProfile = tanks
                .firstOrNull { tank -> tank.id == args.tankId }
                ?.tankType
                ?.takeIf(AquariumTankTaxonomy::isSupportedTankType)
            renderWaterMetrics()
        }

        waterAnalysisViewModel.analysesForTank(args.tankId)
            .observe(viewLifecycleOwner) { analyses ->
                currentAnalyses = analyses
                renderWaterMetrics()
            }
    }

    private fun renderWaterMetrics() {
        val profile = currentTankProfile ?: run {
            contentAdapter?.submitWaterMetrics(
                metrics = emptyList(),
                measuredAtMillis = WaterAnalysisLatestMeasurements.latestEvent(currentAnalyses)
                    ?.measuredAtMillis
            )
            return
        }

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
                    statusText = getString(R.string.tank_health_analysis_recorded)
                )
            }
        }
        contentAdapter?.submitWaterMetrics(
            metrics = models,
            measuredAtMillis = WaterAnalysisLatestMeasurements.latestEvent(currentAnalyses)
                ?.measuredAtMillis
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
}
