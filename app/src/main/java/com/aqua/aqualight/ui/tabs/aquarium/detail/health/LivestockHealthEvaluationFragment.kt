package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.databinding.FragmentLivestockHealthEvaluationBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

class LivestockHealthEvaluationFragment :
    Fragment(R.layout.fragment_livestock_health_evaluation) {

    private val args: LivestockHealthEvaluationFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var _binding: FragmentLivestockHealthEvaluationBinding? = null
    private val binding get() = _binding!!

    private var isNavigating: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "LivestockHealthEvaluationFragment requires a positive tankId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLivestockHealthEvaluationBinding.bind(view)

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.livestock_health_evaluation_title),
                onBackClick = { findNavController().navigateUp() },
                actions = listOf(
                    AquaHeaderAction(
                        iconRes = R.drawable.ic_info,
                        contentDescription = getString(R.string.livestock_health_info_description),
                        onClick = {}
                    )
                )
            )
        )

        binding.rowLastWaterMeasurement.setOnClickListener { openTankHealth() }
        binding.rowLastWaterChange.setOnClickListener { openTankHealth() }
        binding.btnViewFollowup.setOnClickListener { openFollowup() }

        observeLivestock()
    }

    override fun onResume() {
        super.onResume()
        isNavigating = false
    }

    private fun observeLivestock() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val livestock = tanks
                .firstOrNull { tank -> tank.id == args.tankId }
                ?.livestock
                ?.firstOrNull { item -> item.id == args.livestockId }

            renderEvaluation(livestock)
        }
    }

    private fun renderEvaluation(livestock: AquariumLivestock?) {
        val name = livestock?.name?.ifBlank {
            getString(R.string.aquarium_unnamed_livestock)
        } ?: getString(R.string.aquarium_unnamed_livestock)
        val totalCount = livestock?.quantity?.coerceAtLeast(1)
            ?: args.affectedCount.coerceAtLeast(1)
        val affectedCount = args.affectedCount.coerceIn(1, totalCount)
        val symptomLabel = getString(
            LivestockHealthUiText.symptomLabelRes(args.symptomKey)
        )

        binding.ivEvaluationLivestockIcon.setImageResource(
            LivestockCategories.iconRes(livestock?.category.orEmpty())
        )
        binding.tvEvaluationSummary.text = getString(
            R.string.livestock_health_evaluation_summary_format,
            name,
            affectedCount,
            totalCount,
            symptomLabel
        )

        val checks = LivestockHealthEvaluationCatalog.checksFor(
            category = livestock?.category,
            symptomKey = args.symptomKey
        )
        binding.bindEvaluationChecks(checks)
    }

    private fun FragmentLivestockHealthEvaluationBinding.bindEvaluationChecks(
        checks: List<LivestockHealthEvaluationCheck>
    ) {
        require(checks.size == CHECK_COUNT) {
            "Livestock health evaluation requires exactly $CHECK_COUNT checks."
        }

        val icons = listOf(ivCheckOne, ivCheckTwo, ivCheckThree, ivCheckFour)
        val titles = listOf(tvCheckOneTitle, tvCheckTwoTitle, tvCheckThreeTitle, tvCheckFourTitle)
        val bodies = listOf(tvCheckOneBody, tvCheckTwoBody, tvCheckThreeBody, tvCheckFourBody)

        checks.forEachIndexed { index, check ->
            icons[index].setImageResource(check.iconRes)
            titles[index].setText(check.titleRes)
            bodies[index].setText(check.bodyRes)
        }
    }

    private fun openTankHealth() {
        if (isNavigating) {
            return
        }
        val didNavigate = findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.livestockHealthEvaluationFragment,
            directions = LivestockHealthEvaluationFragmentDirections
                .actionLivestockHealthEvaluationFragmentToTankHealthFragment(args.tankId)
        )
        isNavigating = didNavigate
    }

    private fun openFollowup() {
        if (isNavigating) {
            return
        }
        val didNavigate = findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.livestockHealthEvaluationFragment,
            directions = LivestockHealthEvaluationFragmentDirections
                .actionLivestockHealthEvaluationFragmentToLivestockHealthFollowUpFragment(
                    tankId = args.tankId,
                    livestockId = args.livestockId,
                    symptomKey = args.symptomKey,
                    affectedCount = args.affectedCount,
                    readOnly = false
                )
        )
        isNavigating = didNavigate
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val CHECK_COUNT = 4
    }
}
