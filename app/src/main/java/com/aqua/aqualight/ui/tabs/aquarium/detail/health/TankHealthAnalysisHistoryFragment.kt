package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.databinding.FragmentTankHealthAnalysisHistoryBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

class TankHealthAnalysisHistoryFragment :
    Fragment(R.layout.fragment_tank_health_analysis_history) {

    private val args: TankHealthAnalysisHistoryFragmentArgs by navArgs()
    private val waterAnalysisViewModel: WaterAnalysisViewModel by viewModels()

    private var _binding: FragmentTankHealthAnalysisHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var historyAdapter: TankHealthAnalysisHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "TankHealthAnalysisHistoryFragment requires a positive tankId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankHealthAnalysisHistoryBinding.bind(view)

        setupHeader()
        setupHistoryList()
        setupNewAnalysisAction()
        observeHistory()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_tank_health_analysis_history),
                onBackClick = { findNavController().navigateUp() }
            )
        )
    }

    private fun setupHistoryList() {
        historyAdapter = TankHealthAnalysisHistoryAdapter(
            onRecordClick = { record -> openRecordDetail(record.analysisId) }
        )
        binding.historyList.layoutManager = LinearLayoutManager(requireContext())
        binding.historyList.adapter = historyAdapter
        binding.historyList.itemAnimator = null
    }

    private fun setupNewAnalysisAction() {
        binding.btnNewAnalysis.setOnClickListener {
            findNavController().navigateSafelyFrom(
                sourceDestinationId = R.id.tankHealthAnalysisHistoryFragment,
                directions = TankHealthAnalysisHistoryFragmentDirections
                    .actionTankHealthAnalysisHistoryFragmentToTankHealthAnalysisAddFragment(
                        args.tankId
                    )
            )
        }
    }

    private fun observeHistory() {
        waterAnalysisViewModel.analysesStateForTank(args.tankId)
            .observe(viewLifecycleOwner) { state ->
                if (state !is WaterAnalysisLoadState.Content) {
                    renderLoadState(state)
                    return@observe
                }
                val analyses = state.value
                historyAdapter.submitItems(analyses.map(::toHistoryRecord))
                binding.tvHistorySummary.text = resources.getQuantityString(
                    R.plurals.tank_health_analysis_history_count,
                    analyses.size,
                    analyses.size
                )
                binding.tvEmptyHistory.isVisible = analyses.isEmpty()
                binding.tvEmptyHistory.setText(R.string.tank_health_analysis_history_empty)
                binding.historyList.isVisible = analyses.isNotEmpty()
            }
    }

    private fun renderLoadState(state: WaterAnalysisLoadState<*>) {
        historyAdapter.submitItems(emptyList())
        binding.historyList.isVisible = false
        binding.tvEmptyHistory.isVisible = true
        val message = if (state is WaterAnalysisLoadState.Error) {
            R.string.water_analysis_read_failed
        } else R.string.water_analysis_loading
        binding.tvEmptyHistory.setText(message)
        binding.tvHistorySummary.setText(message)
    }

    private fun toHistoryRecord(snapshot: WaterAnalysisSnapshot): TankHealthAnalysisHistoryRecord {
        val context = requireContext()
        val metrics = WaterAnalysisHistoryPreview.from(snapshot).map { preview ->
            when (preview) {
                is WaterAnalysisHistoryPreviewValue.Measurement -> {
                    val measurement = preview.source
                    val name = getString(
                        WaterAnalysisPresentation.parameterNameRes(measurement.parameter)
                    )
                    val symbol = WaterAnalysisPresentation.measurementSymbolRes(measurement)
                        ?.let { symbolRes -> getString(symbolRes) }
                    TankHealthHistoryMetric(
                        labelText = symbol?.let { "$name ($it)" } ?: name,
                        valueText = WaterAnalysisPresentation.measurementValueText(
                            context,
                            measurement
                        )
                    )
                }

                is WaterAnalysisHistoryPreviewValue.Temperature -> TankHealthHistoryMetric(
                    labelText = getString(R.string.tank_health_metric_temperature),
                    valueText = WaterAnalysisPresentation.temperatureValueText(
                        context,
                        preview.celsius
                    )
                )
            }
        }
        return TankHealthAnalysisHistoryRecord(
            analysisId = snapshot.id,
            dateText = LocaleFormatter.formatDate(context, snapshot.measuredAtMillis),
            timeText = LocaleFormatter.formatTime(context, snapshot.measuredAtMillis),
            metrics = metrics
        )
    }

    private fun openRecordDetail(analysisId: Long) {
        findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.tankHealthAnalysisHistoryFragment,
            directions = TankHealthAnalysisHistoryFragmentDirections
                .actionTankHealthAnalysisHistoryFragmentToTankHealthAnalysisDetailFragment(
                    args.tankId,
                    analysisId
                )
        )
    }

    override fun onDestroyView() {
        binding.historyList.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
