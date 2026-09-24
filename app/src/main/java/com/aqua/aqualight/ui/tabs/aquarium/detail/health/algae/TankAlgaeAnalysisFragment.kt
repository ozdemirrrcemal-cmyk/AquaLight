package com.aqua.aqualight.ui.tabs.aquarium.detail.health.algae

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeAnalysisEngine
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeAnalysisPriority
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeDensity
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeFactorStrength
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeObservationInput
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeObservationLocation
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeTrend
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeTypeId
import com.aqua.aqualight.databinding.FragmentTankAlgaeAnalysisBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.TankHealthFragment

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
            context = buildAlgaeTankContext(tank)
        )

        renderAssessment(result.priority)

        binding.factorsContainer.removeAllViews()
        binding.tvNoFactors.isVisible = result.factors.isEmpty()
        result.factors.forEach { factor ->
            addAlgaeAnalysisRow(
                parent = binding.factorsContainer,
                title = getString(AlgaePresentationText.factor(factor.factor)),
                subtitle = getString(
                    AlgaePresentationText.factorStrength(factor.strength)
                ),
                subtitleColor = algaeFactorStrengthColor(requireContext(), factor.strength)
            )
        }

        binding.actionsContainer.removeAllViews()
        result.actions.forEachIndexed { index, recommendation ->
            addAlgaeAnalysisRow(
                parent = binding.actionsContainer,
                title = (index + 1).toString() + ". " +
                    getString(AlgaePresentationText.action(recommendation.action)),
                subtitle = ""
            )
        }

        binding.missingSection.isVisible = result.missingData.isNotEmpty()
        binding.missingContainer.removeAllViews()
        result.missingData.forEach { missing ->
            addAlgaeAnalysisRow(
                parent = binding.missingContainer,
                title = getString(AlgaePresentationText.missing(missing)),
                subtitle = ""
            )
        }
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
