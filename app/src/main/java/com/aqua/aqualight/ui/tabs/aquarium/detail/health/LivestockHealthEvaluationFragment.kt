package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.health.LivestockObservationInput
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentLivestockHealthEvaluationBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LivestockHealthEvaluationFragment :
    Fragment(R.layout.fragment_livestock_health_evaluation) {

    private val args: LivestockHealthEvaluationFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
    private val healthViewModel: LivestockHealthViewModel by activityViewModels()

    private var _binding: FragmentLivestockHealthEvaluationBinding? = null
    private val binding get() = _binding!!

    private var currentLivestock: AquariumLivestock? = null
    private var isNavigating: Boolean = false
    private var isSaving: Boolean = false

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
        if (isSaving || isNavigating || currentLivestock == null) return
        val draft = findNavController().previousBackStackEntry?.savedStateHandle ?: return
        if (draft.get<Long>(LivestockHealthObservationFragment.DRAFT_TANK_ID) != args.tankId ||
            draft.get<Long>(LivestockHealthObservationFragment.DRAFT_LIVESTOCK_ID) != args.livestockId
        ) return
        val symptoms = draft.get<ArrayList<String>>(
            LivestockHealthObservationFragment.DRAFT_SYMPTOMS
        ).orEmpty()
        if (symptoms.isEmpty() || symptoms.first() != args.symptomKey) return
        val input = LivestockObservationInput(
            requestId = draft.get<String>(
                LivestockHealthObservationFragment.DRAFT_REQUEST_ID
            ).orEmpty(),
            tankId = args.tankId,
            livestockId = args.livestockId,
            symptomKeys = symptoms,
            onsetKey = draft.get<String>(LivestockHealthObservationFragment.DRAFT_ONSET).orEmpty(),
            otherObservation = draft.get<String>(LivestockHealthObservationFragment.DRAFT_OTHER).orEmpty(),
            note = draft.get<String>(LivestockHealthObservationFragment.DRAFT_NOTE).orEmpty(),
            photoUris = draft.get<ArrayList<String>>(
                LivestockHealthObservationFragment.DRAFT_PHOTOS
            ).orEmpty(),
            affectedCount = draft.get<Int>(LivestockHealthObservationFragment.DRAFT_AFFECTED)
                ?: args.affectedCount
        )
        isSaving = true
        draft[LivestockHealthObservationFragment.DRAFT_SAVING] = true
        binding.btnViewFollowup.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            setFragmentGlobalLoading(true)
            try {
                healthViewModel.create(input)
                draft[LivestockHealthObservationFragment.DRAFT_COMMITTED] = true
                draft[LivestockHealthObservationFragment.DRAFT_SAVING] = false
                isNavigating = findNavController().popBackStack(
                    R.id.livestockHealthFragment, false
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                draft[LivestockHealthObservationFragment.DRAFT_SAVING] = false
                (activity as? BaseActivity)?.showSnackBar(
                    getString(R.string.livestock_health_save_failed),
                    BaseActivity.SnackType.ERROR
                )
            } finally {
                setFragmentGlobalLoading(false)
                isSaving = false
                _binding?.btnViewFollowup?.isEnabled = true
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
