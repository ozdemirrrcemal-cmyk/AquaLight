package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.databinding.FragmentLivestockHealthFollowUpBinding
import com.aqua.aqualight.databinding.ItemLivestockHealthHistoryBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.header.AquaHeaderPillTextAction
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.google.android.material.card.MaterialCardView

internal data class LivestockHealthCheckResultUi(
    val status: String,
    val affectedCount: Int,
    val checkTimeMillis: Long,
    val photoUri: String?,
    val note: String
)

internal data class FollowUpHistoryEntry(
    val timeLabel: String,
    val observationLabel: String,
    val status: String,
    val statusLabel: String,
    val affectedCount: Int,
    val totalCount: Int,
    val photoUri: String?,
    val note: String
)

internal class LivestockHealthFollowUpHistoryState {
    val entries = mutableListOf<FollowUpHistoryEntry>()

    fun restore(savedInstanceState: Bundle?) {
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
        val photoUris = savedInstanceState?.getStringArrayList(STATE_HISTORY_PHOTOS).orEmpty()
        val notes = savedInstanceState?.getStringArrayList(STATE_HISTORY_NOTES).orEmpty()

        val size = listOf(
            times.size,
            observations.size,
            statuses.size,
            statusLabels.size,
            affected.size,
            totals.size,
            photoUris.size,
            notes.size
        ).minOrNull() ?: 0

        repeat(size) { index ->
            entries += FollowUpHistoryEntry(
                timeLabel = times[index],
                observationLabel = observations[index],
                status = statuses[index],
                statusLabel = statusLabels[index],
                affectedCount = affected[index],
                totalCount = totals[index],
                photoUri = photoUris[index].takeIf(String::isNotBlank),
                note = notes[index]
            )
        }
    }

    fun ensureInitial(
        fragment: Fragment,
        symptomKey: String,
        totalCount: Int
    ) {
        if (entries.isEmpty()) {
            entries += FollowUpHistoryEntry(
                timeLabel = fragment.getString(
                    R.string.livestock_health_history_time_current_preview
                ),
                observationLabel = fragment.getString(
                    LivestockHealthUiText.symptomLabelRes(symptomKey)
                ),
                status = LivestockHealthCheckBottomSheet.STATUS_SAME,
                statusLabel = fragment.getString(R.string.livestock_health_status_same),
                affectedCount = 1,
                totalCount = totalCount,
                photoUri = null,
                note = ""
            )
            entries += FollowUpHistoryEntry(
                timeLabel = fragment.getString(
                    R.string.livestock_health_history_time_initial_preview
                ),
                observationLabel = fragment.getString(
                    R.string.livestock_health_history_issue_initial
                ),
                status = LivestockHealthCheckBottomSheet.STATUS_INCREASED,
                statusLabel = fragment.getString(
                    R.string.livestock_health_status_was_increasing
                ),
                affectedCount = 1,
                totalCount = totalCount,
                photoUri = null,
                note = ""
            )
        }
    }

    fun append(
        fragment: Fragment,
        symptomKey: String,
        livestock: AquariumLivestock,
        result: LivestockHealthCheckResultUi
    ) {
        val time = LocaleFormatter.formatTime(
            fragment.requireContext(),
            result.checkTimeMillis
        )
        entries.add(
            0,
            FollowUpHistoryEntry(
                timeLabel = fragment.getString(
                    R.string.livestock_health_history_time_today_format,
                    time
                ),
                observationLabel = fragment.getString(
                    LivestockHealthUiText.symptomLabelRes(symptomKey)
                ),
                status = result.status,
                statusLabel = fragment.getString(statusLabelRes(result.status)),
                affectedCount = result.affectedCount.coerceIn(
                    1,
                    livestock.quantity.coerceAtLeast(1)
                ),
                totalCount = livestock.quantity.coerceAtLeast(1),
                photoUri = result.photoUri,
                note = result.note
            )
        )
    }

    fun save(outState: Bundle) {
        outState.putStringArrayList(
            STATE_HISTORY_TIMES,
            ArrayList(entries.map(FollowUpHistoryEntry::timeLabel))
        )
        outState.putStringArrayList(
            STATE_HISTORY_OBSERVATIONS,
            ArrayList(entries.map(FollowUpHistoryEntry::observationLabel))
        )
        outState.putStringArrayList(
            STATE_HISTORY_STATUSES,
            ArrayList(entries.map(FollowUpHistoryEntry::status))
        )
        outState.putStringArrayList(
            STATE_HISTORY_STATUS_LABELS,
            ArrayList(entries.map(FollowUpHistoryEntry::statusLabel))
        )
        outState.putIntegerArrayList(
            STATE_HISTORY_AFFECTED,
            ArrayList(entries.map(FollowUpHistoryEntry::affectedCount))
        )
        outState.putIntegerArrayList(
            STATE_HISTORY_TOTALS,
            ArrayList(entries.map(FollowUpHistoryEntry::totalCount))
        )
        outState.putStringArrayList(
            STATE_HISTORY_PHOTOS,
            ArrayList(entries.map { it.photoUri.orEmpty() })
        )
        outState.putStringArrayList(
            STATE_HISTORY_NOTES,
            ArrayList(entries.map(FollowUpHistoryEntry::note))
        )
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

    private companion object {
        const val STATE_HISTORY_TIMES = "livestock_followup_history_times"
        const val STATE_HISTORY_OBSERVATIONS = "livestock_followup_history_observations"
        const val STATE_HISTORY_STATUSES = "livestock_followup_history_statuses"
        const val STATE_HISTORY_STATUS_LABELS = "livestock_followup_history_status_labels"
        const val STATE_HISTORY_AFFECTED = "livestock_followup_history_affected"
        const val STATE_HISTORY_TOTALS = "livestock_followup_history_totals"
        const val STATE_HISTORY_PHOTOS = "livestock_followup_history_photos"
        const val STATE_HISTORY_NOTES = "livestock_followup_history_notes"
    }
}

internal class LivestockHealthFollowUpRenderer(
    private val fragment: Fragment,
    private val binding: FragmentLivestockHealthFollowUpBinding,
    private val symptomKey: String,
    private val affectedCount: Int,
    private val readOnly: Boolean,
    private val closeReason: String
) {
    fun renderLivestock(livestock: AquariumLivestock) {
        val quantity = livestock.quantity.coerceAtLeast(1)
        binding.ivFollowupLivestock.bindRecordPhoto(
            livestock.photoUri,
            LivestockCategories.iconRes(livestock.category)
        )
        binding.tvFollowupLivestockName.text = livestock.name.ifBlank {
            fragment.getString(R.string.aquarium_unnamed_livestock)
        }
        binding.tvFollowupIssue.setText(LivestockHealthUiText.symptomLabelRes(symptomKey))
        binding.tvFollowupAffected.text = fragment.resources.getQuantityString(
            R.plurals.livestock_health_affected_format,
            quantity,
            affectedCount.coerceIn(1, quantity),
            quantity
        )

        if (readOnly) {
            binding.cardStatusIncreased.isClickable = false
            binding.cardStatusSame.isClickable = false
            binding.cardStatusDecreased.isClickable = false
            binding.cardStatusRecovered.isClickable = false
        }
    }

    fun renderHistory(
        livestock: AquariumLivestock,
        entries: List<FollowUpHistoryEntry>
    ) {
        val inflater = LayoutInflater.from(fragment.requireContext())
        binding.historyContainer.removeAllViews()

        entries.forEachIndexed { index, entry ->
            val item = ItemLivestockHealthHistoryBinding.inflate(
                inflater,
                binding.historyContainer,
                false
            )
            val isNewest = index == 0
            val isLast = index == entries.lastIndex
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
                ContextCompat.getColor(
                    fragment.requireContext(),
                    statusColorRes(entry.status)
                )
            )
            item.tvHistoryAffected.text = fragment.resources.getQuantityString(
                R.plurals.livestock_health_affected_format,
                entry.totalCount,
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

    fun renderStatus(currentStatus: String) {
        val cards = linkedMapOf(
            LivestockHealthCheckBottomSheet.STATUS_INCREASED to binding.cardStatusIncreased,
            LivestockHealthCheckBottomSheet.STATUS_SAME to binding.cardStatusSame,
            LivestockHealthCheckBottomSheet.STATUS_DECREASED to binding.cardStatusDecreased,
            LivestockHealthCheckBottomSheet.STATUS_RECOVERED to binding.cardStatusRecovered
        )
        val selectedStroke = ContextCompat.getColor(
            fragment.requireContext(),
            R.color.aqua_button_blue
        )
        val normalStroke = ContextCompat.getColor(
            fragment.requireContext(),
            R.color.aqua_card_outline
        )
        cards.forEach { (status, card) ->
            styleStatusCard(card, status == currentStatus, selectedStroke, normalStroke)
        }
    }

    fun closedStatusAction(): AquaHeaderPillTextAction {
        val recovered = closeReason == LivestockHealthUiSessionState.CLOSE_REASON_RECOVERED
        val textRes = if (recovered) {
            R.string.livestock_health_status_recovered
        } else {
            R.string.livestock_health_followup_closed_manual
        }
        val colorRes = if (recovered) {
            R.color.aqua_status_success
        } else {
            R.color.aqua_card_text_secondary
        }
        return AquaHeaderPillTextAction(
            text = fragment.getString(textRes),
            backgroundRes = R.drawable.bg_aqua_toolbar_pill_action_primary,
            textColor = ContextCompat.getColor(fragment.requireContext(), colorRes),
            contentDescription = fragment.getString(textRes),
            onClick = {}
        )
    }

    private fun styleStatusCard(
        card: MaterialCardView,
        selected: Boolean,
        selectedStroke: Int,
        normalStroke: Int
    ) {
        card.strokeWidth = fragment.resources.getDimensionPixelSize(
            if (selected) R.dimen.aqua_size_2 else R.dimen.aqua_size_1
        )
        card.setStrokeColor(if (selected) selectedStroke else normalStroke)
    }

    private fun statusColorRes(status: String): Int = when (status) {
        LivestockHealthCheckBottomSheet.STATUS_INCREASED -> R.color.aqua_status_danger
        LivestockHealthCheckBottomSheet.STATUS_DECREASED -> R.color.aqua_status_success
        LivestockHealthCheckBottomSheet.STATUS_RECOVERED -> R.color.aqua_content_secondary
        else -> R.color.dialog_icon_warning
    }
}
