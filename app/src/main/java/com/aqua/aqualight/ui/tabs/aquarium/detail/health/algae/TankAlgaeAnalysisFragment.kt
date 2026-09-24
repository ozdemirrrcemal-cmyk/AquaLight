package com.aqua.aqualight.ui.tabs.aquarium.detail.health.algae

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumMaterialCategoryKeys
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeAnalysisEngine
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeAnalysisPriority
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeDensity
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeFactorStrength
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeObservationInput
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeObservationLocation
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeTankContext
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeTrend
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeTypeId
import com.aqua.aqualight.databinding.FragmentTankAlgaeAnalysisBinding
import com.aqua.aqualight.databinding.ItemAlgaeAnalysisRowBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.TankHealthFragment
import com.google.android.material.card.MaterialCardView
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class TankAlgaeAnalysisFragment : Fragment(R.layout.fragment_tank_algae_analysis) {

    private val args: TankAlgaeAnalysisFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var _binding: FragmentTankAlgaeAnalysisBinding? = null
    private val binding get() = _binding!!

    private lateinit var algaeType: AlgaeTypeId
    private lateinit var locations: Set<AlgaeObservationLocation>
    private lateinit var density: AlgaeDensity
    private lateinit var trend: AlgaeTrend

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        require(args.tankId > 0L) {
            "TankAlgaeAnalysisFragment requires a positive tankId."
        }

        algaeType = requireEnum(args.algaeType, "algaeType")
        density = requireEnum(args.density, "density")
        trend = requireEnum(args.trend, "trend")
        locations = args.locations
            .split(',')
            .filter(String::isNotBlank)
            .map { value ->
                requireEnum<AlgaeObservationLocation>(
                    value = value,
                    field = "locations"
                )
            }
            .toSet()

        require(locations.isNotEmpty()) {
            "TankAlgaeAnalysisFragment requires at least one location."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankAlgaeAnalysisBinding.bind(view)

        setupHeader()
        setupActions()
        renderObservationHeader()
        observeTankAndAnalyze()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_algae_analysis),
                onBackClick = ::returnToOverview
            )
        )
    }

    private fun setupActions() {
        binding.btnNewObservation.setOnClickListener {
            findNavController()
                .previousBackStackEntry
                ?.savedStateHandle
                ?.set(TankHealthFragment.KEY_OPEN_NEW_ALGAE_OBSERVATION, true)

            findNavController().navigateUp()
        }

        binding.btnBack.setOnClickListener {
            returnToOverview()
        }
    }

    private fun returnToOverview() {
        findNavController()
            .previousBackStackEntry
            ?.savedStateHandle
            ?.set(TankHealthFragment.KEY_RETURN_ALGAE_OVERVIEW, true)

        findNavController().navigateUp()
    }

    private fun renderObservationHeader() {
        val definition = AlgaeUiCatalog.requireDefinition(algaeType)

        binding.ivAlgaeImage.setImageResource(definition.imageRes)
        binding.tvAlgaeName.setText(definition.nameRes)
        binding.tvObservationMeta.text = buildString {
            append(getString(AlgaePresentationText.density(density)))
            append(" • ")
            append(getString(AlgaePresentationText.trend(trend)))
        }
        binding.tvLocationMeta.text = locations.joinToString(separator = " • ") { location ->
            getString(AlgaePresentationText.location(location))
        }
    }

    private fun observeTankAndAnalyze() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val tank = tanks.firstOrNull { candidate ->
                candidate.id == args.tankId
            } ?: return@observe

            renderAnalysis(tank)
        }
    }

    private fun renderAnalysis(tank: AquariumTankSnapshot) {
        val result = AlgaeAnalysisEngine.analyze(
            observation = AlgaeObservationInput(
                algaeType = algaeType,
                locations = locations,
                density = density,
                trend = trend
            ),
            context = buildAvailableContext(tank)
        )

        renderAssessment(result.priority)

        binding.factorsContainer.removeAllViews()
        binding.tvNoFactors.isVisible = result.factors.isEmpty()
        result.factors.forEach { factor ->
            addRow(
                parent = binding.factorsContainer,
                title = getString(AlgaePresentationText.factor(factor.factor)),
                subtitle = getString(
                    AlgaePresentationText.factorStrength(factor.strength)
                ),
                subtitleColor = factorStrengthColor(factor.strength)
            )
        }

        binding.actionsContainer.removeAllViews()
        result.actions.forEachIndexed { index, recommendation ->
            addRow(
                parent = binding.actionsContainer,
                title = (index + 1).toString() + ". " +
                    getString(AlgaePresentationText.action(recommendation.action)),
                subtitle = ""
            )
        }

        binding.missingSection.isVisible = result.missingData.isNotEmpty()
        binding.missingContainer.removeAllViews()
        result.missingData.forEach { missing ->
            addRow(
                parent = binding.missingContainer,
                title = getString(AlgaePresentationText.missing(missing)),
                subtitle = ""
            )
        }
    }

    private fun buildAvailableContext(
        tank: AquariumTankSnapshot
    ): AlgaeTankContext {
        val tankAgeDays = tank.setupDateEpochDay?.let { setupEpochDay ->
            ChronoUnit.DAYS
                .between(
                    LocalDate.ofEpochDay(setupEpochDay),
                    LocalDate.now()
                )
                .coerceAtLeast(0L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }

        val hasCo2 = tank.materials.any { material ->
            material.categoryKey == AquariumMaterialCategoryKeys.CO2
        }

        return AlgaeTankContext(
            tankAgeDays = tankAgeDays,
            hasCo2 = hasCo2,
            co2ScheduleKnown = false,
            plantCount = tank.plants.size
        )
    }

    private fun renderAssessment(priority: AlgaeAnalysisPriority) {
        binding.tvAssessmentTitle.setText(
            AlgaePresentationText.priorityTitle(priority)
        )
        binding.tvAssessmentBody.setText(
            AlgaePresentationText.priorityBody(priority)
        )

        applyAssessmentColors(
            card = binding.cardAssessment,
            priority = priority
        )
    }

    private fun applyAssessmentColors(
        card: MaterialCardView,
        priority: AlgaeAnalysisPriority
    ) {
        val context = requireContext()

        val background = when (priority) {
            AlgaeAnalysisPriority.ACTION_RECOMMENDED ->
                R.color.aqua_bg_maintenance_profile_percent_warning_fill
            AlgaeAnalysisPriority.REVIEW ->
                R.color.aqua_surface_action
            AlgaeAnalysisPriority.MONITOR ->
                R.color.aqua_surface_positive
        }

        val stroke = when (priority) {
            AlgaeAnalysisPriority.ACTION_RECOMMENDED ->
                R.color.aqua_content_warning
            AlgaeAnalysisPriority.REVIEW ->
                R.color.aqua_accent_primary
            AlgaeAnalysisPriority.MONITOR ->
                R.color.aqua_outline_positive
        }

        val title = when (priority) {
            AlgaeAnalysisPriority.ACTION_RECOMMENDED ->
                R.color.aqua_content_warning
            AlgaeAnalysisPriority.REVIEW ->
                R.color.aqua_accent_primary
            AlgaeAnalysisPriority.MONITOR ->
                R.color.aqua_accent_positive
        }

        card.setCardBackgroundColor(
            ContextCompat.getColor(context, background)
        )
        card.strokeColor = ContextCompat.getColor(context, stroke)
        binding.tvAssessmentTitle.setTextColor(
            ContextCompat.getColor(context, title)
        )
    }

    private fun addRow(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        subtitleColor: Int? = null
    ) {
        val row = ItemAlgaeAnalysisRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        row.tvTitle.text = title
        row.tvSubtitle.text = subtitle
        row.tvSubtitle.isVisible = subtitle.isNotBlank()

        if (subtitleColor != null) {
            row.tvSubtitle.setTextColor(subtitleColor)
        }

        parent.addView(row.root)
    }

    private fun factorStrengthColor(
        strength: AlgaeFactorStrength
    ): Int {
        val colorRes = when (strength) {
            AlgaeFactorStrength.HIGH -> R.color.aqua_content_warning
            AlgaeFactorStrength.MEDIUM -> R.color.aqua_accent_primary
            AlgaeFactorStrength.LOW -> R.color.aqua_accent_positive
        }

        return ContextCompat.getColor(requireContext(), colorRes)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private inline fun <reified T : Enum<T>> requireEnum(
        value: String,
        field: String
    ): T {
        return enumValues<T>().firstOrNull { entry ->
            entry.name == value
        } ?: error("Invalid $field value: $value")
    }
}
