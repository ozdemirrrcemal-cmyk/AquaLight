package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.databinding.FragmentLivestockHealthFollowUpBinding
import com.aqua.aqualight.databinding.ItemLivestockHealthHistoryBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderPillTextAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.google.android.material.card.MaterialCardView

class LivestockHealthFollowUpFragment :
    Fragment(R.layout.fragment_livestock_health_follow_up) {

    private val args: LivestockHealthFollowUpFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var _binding: FragmentLivestockHealthFollowUpBinding? = null
    private val binding get() = _binding!!

    private var currentLivestock: AquariumLivestock? = null
    private var currentStatus: String = LivestockHealthCheckBottomSheet.STATUS_SAME
    private val historyEntries = mutableListOf<FollowUpHistoryEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "LivestockHealthFollowUpFragment requires a positive tankId."
        }
        restoreHistory(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLivestockHealthFollowUpBinding.bind(view)

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.livestock_health_followup_title),
                onBackClick = { findNavController().navigateUp() },
                pillTextAction = AquaHeaderPillTextAction(
                    text = getString(R.string.livestock_health_end_followup),
                    backgroundRes = R.drawable.bg_aqua_toolbar_pill_action_primary,
                    onClick = { endSessionFollowup() }
                )
            )
        )

        parentFragmentManager.setFragmentResultListener(
            LivestockHealthCheckBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            currentStatus = bundle.getString(
                LivestockHealthCheckBottomSheet.RESULT_STATUS,
                LivestockHealthCheckBottomSheet.STATUS_SAME
            )
            val affectedCount = bundle.getInt(
                LivestockHealthCheckBottomSheet.RESULT_AFFECTED_COUNT,
                1
            )
            val checkTimeMillis = bundle.getLong(
                LivestockHealthCheckBottomSheet.RESULT_TIME_MILLIS,
                System.currentTimeMillis()
            )
            val checkPhotoUri = bundle.getString(
                LivestockHealthCheckBottomSheet.RESULT_PHOTO_URI
            )
            appendHistoryEntry(
                status = currentStatus,
                affectedCount = affectedCount,
                checkTimeMillis = checkTimeMillis,
                photoUri = checkPhotoUri
            )
            if (currentStatus == LivestockHealthCheckBottomSheet.STATUS_RECOVERED) {
                endSessionFollowup()
                return@setFragmentResultListener
            }
            renderStatus()
            renderHistory()
        }

        binding.btnNewCheck.setOnClickListener {
            val livestock = currentLivestock ?: return@setOnClickListener
            LivestockHealthCheckBottomSheet.show(
                fragmentManager = parentFragmentManager,
                livestockId = livestock.id,
                livestockName = livestock.name.ifBlank {
                    getString(R.string.aquarium_unnamed_livestock)
                },
                category = livestock.category,
                issueLabel = binding.tvFollowupIssue.text.toString(),
                totalCount = livestock.quantity.coerceAtLeast(1),
                photoUri = livestock.photoUri
            )
        }

        observeLivestock()
        renderStatus()
    }

    private fun observeLivestock() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            currentLivestock = tanks
                .firstOrNull { tank -> tank.id == args.tankId }
                ?.livestock
                ?.firstOrNull { item -> item.id == args.livestockId }
                ?: tanks
                    .firstOrNull { tank -> tank.id == args.tankId }
                    ?.livestock
                    ?.firstOrNull()

            currentLivestock?.let(::renderLivestock)
        }
    }

    private fun renderLivestock(livestock: AquariumLivestock) {
        val name = livestock.name.ifBlank {
            getString(R.string.aquarium_unnamed_livestock)
        }
        val quantity = livestock.quantity.coerceAtLeast(1)

        binding.ivFollowupLivestock.bindRecordPhoto(
            livestock.photoUri,
            LivestockCategories.iconRes(livestock.category)
        )
        binding.tvFollowupLivestockName.text = name
        binding.tvFollowupAffected.text = getString(
            R.string.livestock_health_affected_format,
            1,
            quantity
        )

        ensureInitialHistory(quantity)
        renderHistory()
    }

    private fun ensureInitialHistory(totalCount: Int) {
        if (historyEntries.isNotEmpty()) return
        historyEntries += FollowUpHistoryEntry(
            timeLabel = getString(R.string.livestock_health_history_time_current_preview),
            observationLabel = getString(R.string.livestock_health_history_issue_surface),
            status = LivestockHealthCheckBottomSheet.STATUS_SAME,
            statusLabel = getString(R.string.livestock_health_status_same),
            affectedCount = 1,
            totalCount = totalCount,
            photoUri = null
        )
        historyEntries += FollowUpHistoryEntry(
            timeLabel = getString(R.string.livestock_health_history_time_initial_preview),
            observationLabel = getString(R.string.livestock_health_history_issue_initial),
            status = LivestockHealthCheckBottomSheet.STATUS_INCREASED,
            statusLabel = getString(R.string.livestock_health_status_was_increasing),
            affectedCount = 1,
            totalCount = totalCount,
            photoUri = null
        )
    }

    private fun appendHistoryEntry(
        status: String,
        affectedCount: Int,
        checkTimeMillis: Long,
        photoUri: String?
    ) {
        val livestock = currentLivestock ?: return
        val time = com.aqua.aqualight.i18n.LocaleFormatter.formatTime(
            requireContext(),
            checkTimeMillis
        )
        historyEntries.add(
            0,
            FollowUpHistoryEntry(
                timeLabel = getString(R.string.livestock_health_history_time_today_format, time),
                observationLabel = getString(R.string.livestock_health_history_issue_surface),
                status = status,
                statusLabel = getString(statusLabelRes(status)),
                affectedCount = affectedCount.coerceIn(1, livestock.quantity.coerceAtLeast(1)),
                totalCount = livestock.quantity.coerceAtLeast(1),
                photoUri = photoUri
            )
        )
    }

    private fun renderHistory() {
        if (_binding == null) return
        val livestock = currentLivestock ?: return
        val inflater = LayoutInflater.from(requireContext())

        binding.historyContainer.removeAllViews()
        historyEntries.forEachIndexed { index, entry ->
            val item = ItemLivestockHealthHistoryBinding.inflate(
                inflater,
                binding.historyContainer,
                false
            )
            val isNewest = index == 0
            val isLast = index == historyEntries.lastIndex

            item.timelineTop.isVisible = !isNewest
            item.timelineBottom.isVisible = !isLast
            item.timelineNode.setBackgroundResource(
                if (isNewest) {
                    R.drawable.bg_livestock_health_timeline_current
                } else {
                    R.drawable.bg_livestock_health_timeline_previous
                }
            )
            item.tvHistoryTime.text = entry.timeLabel
            item.tvHistoryObservation.text = entry.observationLabel
            item.tvHistoryStatus.text = entry.statusLabel
            item.tvHistoryStatus.setTextColor(
                ContextCompat.getColor(requireContext(), statusColorRes(entry.status))
            )
            item.tvHistoryAffected.text = getString(
                R.string.livestock_health_affected_format,
                entry.affectedCount,
                entry.totalCount
            )
            item.ivHistoryPhoto.bindRecordPhoto(
                entry.photoUri ?: livestock.photoUri,
                LivestockCategories.iconRes(livestock.category)
            )
            binding.historyContainer.addView(item.root)
        }
    }

    private fun renderStatus() {
        val cards = linkedMapOf(
            LivestockHealthCheckBottomSheet.STATUS_INCREASED to binding.cardStatusIncreased,
            LivestockHealthCheckBottomSheet.STATUS_SAME to binding.cardStatusSame,
            LivestockHealthCheckBottomSheet.STATUS_DECREASED to binding.cardStatusDecreased,
            LivestockHealthCheckBottomSheet.STATUS_RECOVERED to binding.cardStatusRecovered
        )
        val selectedStroke = ContextCompat.getColor(requireContext(), R.color.aqua_button_blue)
        val normalStroke = ContextCompat.getColor(requireContext(), R.color.aqua_card_outline)

        cards.forEach { (status, card) ->
            styleStatusCard(card, status == currentStatus, selectedStroke, normalStroke)
        }
    }

    private fun styleStatusCard(
        card: MaterialCardView,
        selected: Boolean,
        selectedStroke: Int,
        normalStroke: Int
    ) {
        card.strokeWidth = resources.getDimensionPixelSize(
            if (selected) R.dimen.aqua_size_2 else R.dimen.aqua_size_1
        )
        card.setStrokeColor(if (selected) selectedStroke else normalStroke)
    }

    private fun statusLabelRes(status: String): Int = when (status) {
        LivestockHealthCheckBottomSheet.STATUS_INCREASED ->
            R.string.livestock_health_status_increased
        LivestockHealthCheckBottomSheet.STATUS_DECREASED ->
            R.string.livestock_health_status_decreased
        LivestockHealthCheckBottomSheet.STATUS_RECOVERED ->
            R.string.livestock_health_status_recovered
        else -> R.string.livestock_health_status_same
    }

    private fun statusColorRes(status: String): Int = when (status) {
        LivestockHealthCheckBottomSheet.STATUS_INCREASED -> R.color.aqua_status_danger
        LivestockHealthCheckBottomSheet.STATUS_DECREASED -> R.color.aqua_status_success
        LivestockHealthCheckBottomSheet.STATUS_RECOVERED -> R.color.aqua_content_secondary
        else -> R.color.dialog_icon_warning
    }

    private fun restoreHistory(savedInstanceState: Bundle?) {
        val times = savedInstanceState?.getStringArrayList(STATE_HISTORY_TIMES).orEmpty()
        val observations = savedInstanceState
            ?.getStringArrayList(STATE_HISTORY_OBSERVATIONS)
            .orEmpty()
        val statuses = savedInstanceState?.getStringArrayList(STATE_HISTORY_STATUSES).orEmpty()
        val statusLabels = savedInstanceState
            ?.getStringArrayList(STATE_HISTORY_STATUS_LABELS)
            .orEmpty()
        val affected = savedInstanceState?.getIntegerArrayList(STATE_HISTORY_AFFECTED).orEmpty()
        val totals = savedInstanceState?.getIntegerArrayList(STATE_HISTORY_TOTALS).orEmpty()
        val photoUris = savedInstanceState
            ?.getStringArrayList(STATE_HISTORY_PHOTOS)
            .orEmpty()

        val size = listOf(
            times.size,
            observations.size,
            statuses.size,
            statusLabels.size,
            affected.size,
            totals.size,
            photoUris.size
        ).minOrNull() ?: 0

        repeat(size) { index ->
            historyEntries += FollowUpHistoryEntry(
                timeLabel = times[index],
                observationLabel = observations[index],
                status = statuses[index],
                statusLabel = statusLabels[index],
                affectedCount = affected[index],
                totalCount = totals[index],
                photoUri = photoUris[index].takeIf(String::isNotBlank)
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(
            STATE_HISTORY_TIMES,
            ArrayList(historyEntries.map(FollowUpHistoryEntry::timeLabel))
        )
        outState.putStringArrayList(
            STATE_HISTORY_OBSERVATIONS,
            ArrayList(historyEntries.map(FollowUpHistoryEntry::observationLabel))
        )
        outState.putStringArrayList(
            STATE_HISTORY_STATUSES,
            ArrayList(historyEntries.map(FollowUpHistoryEntry::status))
        )
        outState.putStringArrayList(
            STATE_HISTORY_STATUS_LABELS,
            ArrayList(historyEntries.map(FollowUpHistoryEntry::statusLabel))
        )
        outState.putIntegerArrayList(
            STATE_HISTORY_AFFECTED,
            ArrayList(historyEntries.map(FollowUpHistoryEntry::affectedCount))
        )
        outState.putIntegerArrayList(
            STATE_HISTORY_TOTALS,
            ArrayList(historyEntries.map(FollowUpHistoryEntry::totalCount))
        )
        outState.putStringArrayList(
            STATE_HISTORY_PHOTOS,
            ArrayList(historyEntries.map { entry -> entry.photoUri.orEmpty() })
        )
        super.onSaveInstanceState(outState)
    }

    private fun endSessionFollowup() {
        val navController = findNavController()
        runCatching {
            navController.getBackStackEntry(R.id.livestockHealthFragment)
                .savedStateHandle
                .set(LivestockHealthFragment.KEY_HAS_OBSERVATION, false)
        }
        navController.popBackStack(R.id.livestockHealthFragment, false)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private data class FollowUpHistoryEntry(
        val timeLabel: String,
        val observationLabel: String,
        val status: String,
        val statusLabel: String,
        val affectedCount: Int,
        val totalCount: Int,
        val photoUri: String?
    )

    private companion object {
        const val STATE_HISTORY_TIMES = "livestock_followup_history_times"
        const val STATE_HISTORY_OBSERVATIONS = "livestock_followup_history_observations"
        const val STATE_HISTORY_STATUSES = "livestock_followup_history_statuses"
        const val STATE_HISTORY_STATUS_LABELS = "livestock_followup_history_status_labels"
        const val STATE_HISTORY_AFFECTED = "livestock_followup_history_affected"
        const val STATE_HISTORY_TOTALS = "livestock_followup_history_totals"
        const val STATE_HISTORY_PHOTOS = "livestock_followup_history_photos"
    }
}
