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
import com.aqua.aqualight.application.aquarium.health.LivestockHealthContextSnapshot
import com.aqua.aqualight.application.aquarium.health.LivestockObservationSnapshot
import com.aqua.aqualight.databinding.FragmentLivestockHealthEvaluationBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException

class LivestockHealthEvaluationFragment :
    Fragment(R.layout.fragment_livestock_health_evaluation) {

    private val args: LivestockHealthEvaluationFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
    private val healthViewModel: LivestockHealthViewModel by activityViewModels()

    private var _binding: FragmentLivestockHealthEvaluationBinding? = null
    private val binding get() = _binding!!

    private var currentLivestock: AquariumLivestock? = null
    private var currentRecord: LivestockObservationSnapshot? = null
    private var currentContext = LivestockHealthContextSnapshot(
        latestWaterAnalysis = null,
        lastWaterChangeAtMillis = null
    )
    private var isNavigating = false
    private var isSaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L)
        require(args.observationId >= 0L)
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
        binding.btnViewFollowup.setOnClickListener { handlePrimaryAction() }
        observeEvaluationData()
    }

    override fun onResume() {
        super.onResume()
        isNavigating = false
    }

    private fun observeEvaluationData() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            currentLivestock = tanks
                .firstOrNull { tank -> tank.id == args.tankId }
                ?.livestock
                ?.firstOrNull { item -> item.id == args.livestockId }
            renderEvaluation()
        }
        healthViewModel.observationsForTank(args.tankId).observe(viewLifecycleOwner) { records ->
            currentRecord = records.firstOrNull { record ->
                record.id == args.observationId && record.livestockId == args.livestockId
            }
            renderEvaluation()
        }
        healthViewModel.contextForTank(args.tankId).observe(viewLifecycleOwner) { context ->
            currentContext = context
            renderEvaluation()
        }
    }

    private fun renderEvaluation() {
        val existingEvaluation = args.observationId > 0L
        val record = currentRecord
        if (existingEvaluation && record == null) return

        val draftOtherObservation = if (record == null) {
            findNavController().previousBackStackEntry
                ?.savedStateHandle
                ?.get<String>(LivestockHealthObservationFragment.DRAFT_OTHER)
                .orEmpty()
        } else {
            ""
        }
        binding.renderEvaluationScreen(
            fragment = this,
            state = LivestockEvaluationScreenState(
                livestock = currentLivestock,
                record = record,
                fallback = LivestockEvaluationFallbackState(
                    symptomKey = args.symptomKey,
                    affectedCount = args.affectedCount,
                    otherObservation = draftOtherObservation
                ),
                latestWaterAnalysis = currentContext.latestWaterAnalysis,
                lastWaterChangeAtMillis = currentContext.lastWaterChangeAtMillis,
                existingEvaluation = existingEvaluation
            )
        )
    }

    private fun openTankHealth() {
        if (isNavigating) return
        isNavigating = findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.livestockHealthEvaluationFragment,
            directions = LivestockHealthEvaluationFragmentDirections
                .actionLivestockHealthEvaluationFragmentToTankHealthFragment(args.tankId)
        )
    }

    private fun handlePrimaryAction() {
        if (!isSaving && !isNavigating) {
            val record = currentRecord
            val stale = record?.let { snapshot ->
                snapshot.closedAtMillis == null &&
                    snapshot.isEvaluationStale(currentContext.latestWaterAnalysis)
            }
            when {
                args.observationId > 0L && record == null -> Unit
                args.observationId > 0L && stale == false -> {
                    isNavigating = findNavController().navigateUp()
                }
                else -> persistEvaluation()
            }
        }
    }

    private fun persistEvaluation() {
        val existingEvaluation = args.observationId > 0L
        if (existingEvaluation && currentRecord == null) return
        val draft = if (existingEvaluation) {
            null
        } else {
            findNavController().previousBackStackEntry?.savedStateHandle
        }

        isSaving = true
        binding.btnViewFollowup.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            setFragmentGlobalLoading(true)
            try {
                when (
                    val result = saveLivestockEvaluation(
                        healthViewModel = healthViewModel,
                        context = LivestockEvaluationSaveContext(
                            record = currentRecord,
                            draft = draft,
                            route = LivestockEvaluationSaveRoute(
                                tankId = args.tankId,
                                livestockId = args.livestockId,
                                symptomKey = args.symptomKey,
                                affectedCount = args.affectedCount
                            ),
                            latestWaterAnalysis = currentContext.latestWaterAnalysis
                        )
                    )
                ) {
                    null -> Unit
                    LivestockEvaluationSaveResult.Refreshed ->
                        isNavigating = findNavController().navigateUp()
                    is LivestockEvaluationSaveResult.FollowUpStarted ->
                        openFollowUp(result)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                showLivestockHealthSaveFailure()
            } catch (_: IllegalArgumentException) {
                showLivestockHealthSaveFailure()
            } catch (_: IllegalStateException) {
                showLivestockHealthSaveFailure()
            } finally {
                setFragmentGlobalLoading(false)
                isSaving = false
                _binding?.btnViewFollowup?.isEnabled = true
            }
        }
    }

    private fun openFollowUp(result: LivestockEvaluationSaveResult.FollowUpStarted) {
        isNavigating = findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.livestockHealthEvaluationFragment,
            directions = LivestockHealthEvaluationFragmentDirections
                .actionLivestockHealthEvaluationFragmentToLivestockHealthFollowUpFragment(
                    tankId = args.tankId,
                    livestockId = args.livestockId,
                    symptomKey = args.symptomKey,
                    affectedCount = result.affectedCount,
                    readOnly = false,
                    closeReason = "",
                    observationId = result.observationId
                )
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
