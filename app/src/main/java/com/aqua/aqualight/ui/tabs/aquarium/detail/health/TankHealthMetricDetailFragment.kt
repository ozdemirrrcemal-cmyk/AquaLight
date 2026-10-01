package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentDirection
import com.aqua.aqualight.application.aquarium.health.water.WaterRuleFinding
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentTankHealthMetricDetailBinding
import com.aqua.aqualight.databinding.ItemTankHealthMetricFindingBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class TankHealthMetricDetailFragment :
    Fragment(R.layout.fragment_tank_health_metric_detail) {

    private val args: TankHealthMetricDetailFragmentArgs by navArgs()
    private val waterAnalysisViewModel: WaterAnalysisViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var _binding: FragmentTankHealthMetricDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var metricId: TankHealthWaterMetricId

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "TankHealthMetricDetailFragment requires a positive tankId."
        }
        require(args.analysisId > 0L) {
            "TankHealthMetricDetailFragment requires a positive analysisId."
        }
        metricId = requireNotNull(TankHealthWaterMetricRoute.decode(args.metricKey)) {
            "TankHealthMetricDetailFragment requires a valid metricKey."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankHealthMetricDetailBinding.bind(view)
        setupHeader()
        observeRecord()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_tank_health_metric_detail),
                onBackClick = { findNavController().navigateUp() }
            )
        )
    }

    private fun observeRecord() {
        waterAnalysisViewModel.analysisState(args.tankId, args.analysisId)
            .observe(viewLifecycleOwner) { state ->
                when (state) {
                    WaterAnalysisLoadState.Loading ->
                        renderUnavailable(R.string.water_analysis_loading)
                    is WaterAnalysisLoadState.Error ->
                        renderUnavailable(R.string.water_analysis_read_failed)
                    is WaterAnalysisLoadState.Content -> state.value
                        ?.takeIf { record -> record.tankId == args.tankId }
                        ?.let(::renderRecord)
                        ?: renderUnavailable(R.string.water_analysis_record_missing)
                }
            }
    }

    private fun renderRecord(record: WaterAnalysisSnapshot) {
        val context = requireContext()
        val value = TankHealthWaterMetricDetailPresentation.valueText(
            context,
            record,
            metricId
        ) ?: run {
            renderUnavailable(R.string.water_analysis_record_missing)
            return
        }
        val summary = TankHealthWaterMetricDetailPresentation.summary(record, metricId)
        val findings = TankHealthWaterMetricDetailPresentation.findings(record, metricId)

        binding.tvMetricName.setText(
            TankHealthWaterMetricDetailPresentation.metricNameRes(metricId)
        )
        binding.tvMetricValue.text = value
        binding.tvMetricStatus.setText(
            TankHealthWaterMetricAssessment.statusRes(summary.status)
        )
        binding.tvMetricStatus.setTextColor(
            ContextCompat.getColor(
                context,
                TankHealthWaterMetricAssessment.statusColorRes(summary.status)
            )
        )
        binding.tvComparisonCount.text = resources.getQuantityString(
            R.plurals.tank_health_metric_detail_compared_count,
            summary.comparedCount,
            summary.comparedCount
        )
        binding.tvCountBreakdown.text = countBreakdown(summary)
        binding.tvNoComparison.isVisible = summary.comparedCount == 0

        bindFindingSection(
            binding.groupWithin,
            binding.withinContainer,
            findings.filter { it.direction == WaterAssessmentDirection.WITHIN }
        )
        bindFindingSection(
            binding.groupOutside,
            binding.outsideContainer,
            findings.filter {
                it.direction == WaterAssessmentDirection.ABOVE ||
                    it.direction == WaterAssessmentDirection.BELOW
            }
        )
        bindFindingSection(
            binding.groupUnassessed,
            binding.unassessedContainer,
            findings.filter { it.direction == null }
        )
    }

    private fun countBreakdown(summary: TankHealthWaterCompatibilitySummary): String =
        buildList {
            if (summary.withinCount > 0) {
                add(getString(R.string.tank_health_metric_detail_within_count, summary.withinCount))
            }
            if (summary.aboveCount > 0) {
                add(getString(R.string.tank_health_metric_detail_above_count, summary.aboveCount))
            }
            if (summary.belowCount > 0) {
                add(getString(R.string.tank_health_metric_detail_below_count, summary.belowCount))
            }
            if (summary.unassessedCount > 0) {
                add(
                    getString(
                        R.string.tank_health_metric_detail_unassessed_count,
                        summary.unassessedCount
                    )
                )
            }
        }.joinToString(" · ")

    private fun bindFindingSection(
        group: View,
        container: LinearLayout,
        findings: List<WaterRuleFinding>
    ) {
        group.isVisible = findings.isNotEmpty()
        container.removeAllViews()
        findings.forEach { finding ->
            val item = ItemTankHealthMetricFindingBinding.inflate(
                layoutInflater,
                container,
                false
            )
            bindFinding(item, finding)
            container.addView(item.root)
        }
    }

    private fun bindFinding(
        item: ItemTankHealthMetricFindingBinding,
        finding: WaterRuleFinding
    ) {
        val context = requireContext()
        item.tvEntityType.setText(
            TankHealthWaterMetricDetailPresentation.entityKindRes(finding)
        )
        item.tvEntityName.text =
            TankHealthWaterMetricDetailPresentation.entityName(finding)
        item.tvFindingStatus.setText(
            TankHealthWaterMetricDetailPresentation.findingResultRes(finding)
        )
        item.tvFindingStatus.setTextColor(
            ContextCompat.getColor(
                context,
                TankHealthWaterMetricDetailPresentation.findingColorRes(finding)
            )
        )

        val range = TankHealthWaterMetricDetailPresentation.rangeText(context, finding)
        item.tvCatalogRange.isVisible = range != null
        item.tvCatalogRange.text = range?.let {
            getString(R.string.tank_health_metric_detail_catalog_range, it)
        }

        item.tvFindingReason.isVisible = finding.direction == null
        item.tvFindingReason.text = if (finding.direction == null) {
            getString(
                TankHealthWaterMetricDetailPresentation.gapReasonRes(finding.gap)
            )
        } else {
            null
        }
    }

    private fun renderUnavailable(@StringRes messageRes: Int) {
        binding.tvMetricName.setText(
            TankHealthWaterMetricDetailPresentation.metricNameRes(metricId)
        )
        binding.tvMetricValue.setText(R.string.tank_health_value_not_measured)
        binding.tvMetricStatus.setText(R.string.tank_health_metric_status_unevaluated)
        binding.tvMetricStatus.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.aqua_content_muted)
        )
        binding.tvComparisonCount.setText(messageRes)
        binding.tvCountBreakdown.text = null
        binding.tvNoComparison.isVisible = true
        bindFindingSection(binding.groupWithin, binding.withinContainer, emptyList())
        bindFindingSection(binding.groupOutside, binding.outsideContainer, emptyList())
        bindFindingSection(
            binding.groupUnassessed,
            binding.unassessedContainer,
            emptyList()
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
