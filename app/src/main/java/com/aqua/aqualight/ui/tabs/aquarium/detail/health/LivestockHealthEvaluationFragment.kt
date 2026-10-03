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

    private var currentLivestock: AquariumLivestock? = null
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
                onBackClick = { findNavController().navigateUp() }
            )
        )

        binding.rowLastWaterMeasurement.setOnClickListener { openTankHealth() }
        binding.rowLastWaterChange.setOnClickListener { openTankHealth() }
        binding.btnViewFollowup.setOnClickListener { saveEvaluationAndReturn() }

        observeLivestock()
    }

    override fun onResume() {
        super.onResume()
        isNavigating = false
    }

    private fun observeLivestock() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            currentLivestock = tanks
                .firstOrNull { tank -> tank.id == args.tankId }
                ?.livestock
                ?.firstOrNull { item -> item.id == args.livestockId }

            renderEvaluation(currentLivestock)
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
        binding.tvEvaluationSummary.text = resources.getQuantityString(
            R.plurals.livestock_health_evaluation_summary_format,
            totalCount,
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

    private fun saveEvaluationAndReturn() {
        if (!isNavigating) {
            currentLivestock?.let { livestock ->
                val navController = findNavController()
                val now = System.currentTimeMillis()
                val saved = runCatching {
                    navController.getBackStackEntry(R.id.livestockHealthFragment)
                        .savedStateHandle
                        .apply {
                            set(LivestockHealthFragment.KEY_HAS_OBSERVATION, true)
                            set(LivestockHealthFragment.KEY_LIVESTOCK_ID, livestock.id)
                            set(LivestockHealthFragment.KEY_SYMPTOM_KEY, args.symptomKey)
                            set(LivestockHealthFragment.KEY_AFFECTED_COUNT, args.affectedCount)
                            set(LivestockHealthFragment.KEY_STARTED_AT, now)
                            set(LivestockHealthFragment.KEY_LAST_CHECK_AT, now)
                            LivestockHealthUiSessionState.upsertActiveFollowup(
                                handle = this,
                                entry = ActiveLivestockFollowupUi(
                                    livestockId = livestock.id,
                                    symptomKey = args.symptomKey,
                                    affectedCount = args.affectedCount.coerceIn(
                                        1,
                                        livestock.quantity.coerceAtLeast(1)
                                    ),
                                    totalCount = livestock.quantity.coerceAtLeast(1),
                                    startedAtMillis = now,
                                    lastCheckAtMillis = now
                                )
                            )
                        }
                }.isSuccess

                if (saved) {
                    isNavigating = navController.popBackStack(
                        R.id.livestockHealthFragment,
                        false
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val CHECK_COUNT = 4
    }
}
