package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.health.LivestockObservationSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.databinding.FragmentLivestockHealthFollowUpBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderPillTextAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException

class LivestockHealthFollowUpFragment : Fragment(R.layout.fragment_livestock_health_follow_up) {
    private val args: LivestockHealthFollowUpFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
    private val healthViewModel: LivestockHealthViewModel by activityViewModels()
    private val waterAnalysisViewModel: WaterAnalysisViewModel by activityViewModels()

    private var _binding: FragmentLivestockHealthFollowUpBinding? = null
    private val binding get() = _binding!!
    private var currentLivestock: AquariumLivestock? = null
    private var currentRecord: LivestockObservationSnapshot? = null
    private var latestWaterAnalysis: WaterAnalysisSnapshot? = null
    private var renderer: LivestockHealthFollowUpRenderer? = null
    private var isEnding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L && args.observationId > 0L)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLivestockHealthFollowUpBinding.bind(view)
        renderer = LivestockHealthFollowUpRenderer(
            fragment = this,
            binding = binding,
            readOnly = args.readOnly,
            closeReason = args.closeReason
        )
        bindHeader()
        bindCheckActions()
        observeFollowUpState()
    }

    private fun bindHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.livestock_health_followup_title),
                onBackClick = { findNavController().navigateUp() },
                pillTextAction = if (args.readOnly) {
                    null
                } else {
                    AquaHeaderPillTextAction(
                        text = getString(R.string.livestock_health_end_followup),
                        backgroundRes = R.drawable.bg_aqua_toolbar_pill_action_primary,
                        onClick = { endFollowup() }
                    )
                }
            )
        )
    }

    private fun bindCheckActions() {
        parentFragmentManager.setFragmentResultListener(
            LivestockHealthCheckBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            if (
                bundle.getString(LivestockHealthCheckBottomSheet.RESULT_STATUS) ==
                LivestockHealthCheckBottomSheet.STATUS_RECOVERED
            ) {
                findNavController().popBackStack(R.id.livestockHealthFragment, false)
            }
        }

        binding.btnNewCheck.isVisible = !args.readOnly
        binding.btnNewCheck.setOnClickListener {
            val livestock = currentLivestock
            val record = currentRecord
            if (livestock != null && record != null && record.closedAtMillis == null) {
                showNewCheck(livestock, record)
            }
        }
    }

    private fun showNewCheck(
        livestock: AquariumLivestock,
        record: LivestockObservationSnapshot
    ) {
        LivestockHealthCheckBottomSheet.show(
            parentFragmentManager,
            LivestockHealthCheckSheetRequest(
                tankId = args.tankId,
                observationId = record.id,
                livestockId = livestock.id,
                livestockName = livestock.name.ifBlank {
                    getString(R.string.aquarium_unnamed_livestock)
                },
                category = livestock.category,
                issueLabel = binding.tvFollowupIssue.text.toString(),
                affectedCount = record.currentAffectedCount,
                totalCount = record.totalCount,
                livestockPhotoUri = livestock.photoUri
            )
        )
    }

    private fun observeFollowUpState() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            currentLivestock = tanks.firstOrNull { it.id == args.tankId }
                ?.livestock?.firstOrNull { it.id == args.livestockId }
            render()
        }
        healthViewModel.observationsForTank(args.tankId).observe(viewLifecycleOwner) { records ->
            currentRecord = records.firstOrNull { it.id == args.observationId &&
                it.livestockId == args.livestockId }
            render()
        }
        waterAnalysisViewModel.analysesForTank(args.tankId).observe(viewLifecycleOwner) { analyses ->
            latestWaterAnalysis = analyses.firstOrNull()
            render()
        }
    }

    private fun render() {
        val record = currentRecord ?: return
        val livestock = currentLivestock ?: return
        renderer?.renderLivestock(
            livestock = livestock,
            affectedCount = record.currentAffectedCount,
            observationLabel = LivestockHealthUiText.observationLabel(
                fragment = this,
                symptomKey = record.symptomKeys.first(),
                otherObservation = record.otherObservation
            )
        )
        renderer?.renderStatus(record.checks.maxByOrNull { it.checkedAtMillis }?.status
            ?: LivestockHealthCheckBottomSheet.STATUS_SAME)
        renderer?.renderHistory(record.toHistoryEntries(this))
        binding.renderEvaluationSummary(
            fragment = this,
            record = record,
            latestWaterAnalysis = latestWaterAnalysis
        ) {
            findNavController().navigateSafelyFrom(
                sourceDestinationId = R.id.livestockHealthFollowUpFragment,
                directions = LivestockHealthFollowUpFragmentDirections
                    .actionLivestockHealthFollowUpFragmentToLivestockHealthEvaluationFragment(
                        tankId = args.tankId,
                        livestockId = record.livestockId,
                        symptomKey = record.symptomKeys.first(),
                        affectedCount = record.currentAffectedCount,
                        observationId = record.id
                    )
            )
        }
        _binding?.btnNewCheck?.isEnabled = !args.readOnly && record.closedAtMillis == null
    }

    private fun endFollowup() {
        if (isEnding || args.readOnly || currentRecord?.closedAtMillis != null) return
        isEnding = true
        viewLifecycleOwner.lifecycleScope.launch {
            setFragmentGlobalLoading(true)
            try {
                healthViewModel.close(
                    args.tankId, args.observationId,
                    LivestockHealthUiSessionState.CLOSE_REASON_MANUAL
                )
                findNavController().popBackStack(R.id.livestockHealthFragment, false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                showLivestockHealthSaveFailure()
            } catch (_: IllegalArgumentException) {
                showLivestockHealthSaveFailure()
            } catch (_: IllegalStateException) {
                showLivestockHealthSaveFailure()
            } finally {
                isEnding = false
                setFragmentGlobalLoading(false)
            }
        }
    }

    override fun onDestroyView() {
        renderer = null
        _binding = null
        super.onDestroyView()
    }
}
