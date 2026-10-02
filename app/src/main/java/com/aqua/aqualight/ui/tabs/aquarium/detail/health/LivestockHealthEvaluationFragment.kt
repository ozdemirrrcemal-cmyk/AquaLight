package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentLivestockHealthEvaluationBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
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

        binding.btnMeasureWater.setOnClickListener { openTankHealth() }
        binding.btnViewFollowup.setOnClickListener { openFollowup() }

        observeLivestock()
    }

    override fun onResume() {
        super.onResume()
        isNavigating = false
    }

    private fun observeLivestock() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) {
            tanks ->
            val livestock = tanks
                .firstOrNull { tank -> tank.id == args.tankId }
                ?.livestock
                ?.firstOrNull { item -> item.id == args.livestockId }

            val name = livestock?.name?.ifBlank {
                getString(R.string.aquarium_unnamed_livestock)
            } ?: getString(R.string.aquarium_unnamed_livestock)

            binding.tvEvaluationSummary.text = getString(
                R.string.livestock_health_evaluation_summary_format,
                name,
                getString(LivestockHealthUiText.symptomLabelRes(args.symptomKey)),
                args.affectedCount.coerceAtLeast(1)
            )
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
                    livestockId = args.livestockId
                )
        )
        isNavigating = didNavigate
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
