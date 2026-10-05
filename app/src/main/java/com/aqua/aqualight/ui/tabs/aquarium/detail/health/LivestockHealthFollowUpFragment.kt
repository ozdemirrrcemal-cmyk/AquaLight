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
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentLivestockHealthFollowUpBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderPillTextAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LivestockHealthFollowUpFragment : Fragment(R.layout.fragment_livestock_health_follow_up) {
    private val args: LivestockHealthFollowUpFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()
    private val healthViewModel: LivestockHealthViewModel by activityViewModels()

    private var _binding: FragmentLivestockHealthFollowUpBinding? = null
    private val binding get() = _binding!!
    private var currentLivestock: AquariumLivestock? = null
    private var currentRecord: LivestockObservationSnapshot? = null
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
            fragment = this, binding = binding, symptomKey = args.symptomKey,
            readOnly = args.readOnly,
            closeReason = args.closeReason
        )
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.livestock_health_followup_title),
                onBackClick = { findNavController().navigateUp() },
                pillTextAction = if (args.readOnly) null else AquaHeaderPillTextAction(
                    text = getString(R.string.livestock_health_end_followup),
                    backgroundRes = R.drawable.bg_aqua_toolbar_pill_action_primary,
                    onClick = { endFollowup() }
                )
            )
        )
        parentFragmentManager.setFragmentResultListener(
            LivestockHealthCheckBottomSheet.REQUEST_KEY, viewLifecycleOwner
        ) { _, bundle ->
            if (bundle.getString(LivestockHealthCheckBottomSheet.RESULT_STATUS) ==
                LivestockHealthCheckBottomSheet.STATUS_RECOVERED
            ) findNavController().popBackStack(R.id.livestockHealthFragment, false)
        }
        binding.btnNewCheck.isVisible = !args.readOnly
        binding.btnNewCheck.setOnClickListener {
            val livestock = currentLivestock ?: return@setOnClickListener
            val record = currentRecord ?: return@setOnClickListener
            if (record.closedAtMillis != null || args.readOnly) return@setOnClickListener
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
                    totalCount = record.totalCount,
                    photoUri = livestock.photoUri
                )
            )
        }
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
    }

    private fun render() {
        val record = currentRecord ?: return
        val livestock = currentLivestock ?: return
        renderer?.renderLivestock(
            livestock,
            record.checks.maxByOrNull { it.checkedAtMillis }?.affectedCount
                ?: record.affectedCount
        )
        renderer?.renderStatus(record.checks.maxByOrNull { it.checkedAtMillis }?.status
            ?: LivestockHealthCheckBottomSheet.STATUS_SAME)
        renderer?.renderHistory(livestock, record.toHistoryEntries(this))
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
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                (activity as? BaseActivity)?.showSnackBar(
                    getString(R.string.livestock_health_save_failed), BaseActivity.SnackType.ERROR
                )
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
