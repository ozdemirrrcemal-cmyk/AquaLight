package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.health.LivestockObservationSnapshot
import com.aqua.aqualight.databinding.FragmentLivestockHealthFollowUpBinding
import com.aqua.aqualight.databinding.ItemLivestockHealthHistoryBinding
import com.aqua.aqualight.i18n.LocaleFormatter
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

internal fun LivestockObservationSnapshot.toHistoryEntries(fragment: Fragment): List<FollowUpHistoryEntry> {
    fun timeLabel(millis: Long): String =
        LocaleFormatter.formatDate(fragment.requireContext(), millis) + " · " +
            LocaleFormatter.formatTime(fragment.requireContext(), millis)

    val checkEntries = checks.sortedByDescending { it.checkedAtMillis }.map { check ->
        FollowUpHistoryEntry(
            timeLabel = timeLabel(check.checkedAtMillis),
            observationLabel = fragment.getString(LivestockHealthUiText.symptomLabelRes(symptomKeys.first())),
            status = check.status,
            statusLabel = fragment.getString(statusLabelRes(check.status)),
            affectedCount = check.affectedCount,
            totalCount = totalCount,
            photoUri = check.photoUri,
            note = check.note
        )
    }
    return checkEntries + FollowUpHistoryEntry(
        timeLabel = timeLabel(createdAtMillis),
        observationLabel = fragment.getString(LivestockHealthUiText.symptomLabelRes(symptomKeys.first())),
        status = LivestockHealthCheckBottomSheet.STATUS_SAME,
        statusLabel = fragment.getString(R.string.livestock_health_first_observation),
        affectedCount = affectedCount,
        totalCount = totalCount,
        photoUri = photoUris.firstOrNull(),
        note = note
    )
}

private fun statusLabelRes(status: String): Int = when (status) {
    LivestockHealthCheckBottomSheet.STATUS_INCREASED -> R.string.livestock_health_status_increased
    LivestockHealthCheckBottomSheet.STATUS_DECREASED -> R.string.livestock_health_status_decreased
    LivestockHealthCheckBottomSheet.STATUS_RECOVERED -> R.string.livestock_health_status_recovered
    else -> R.string.livestock_health_status_same
}

internal class LivestockHealthFollowUpRenderer(
    private val fragment: Fragment,
    private val binding: FragmentLivestockHealthFollowUpBinding,
    private val symptomKey: String,
    private val readOnly: Boolean,
    private val closeReason: String
) {
    fun renderLivestock(livestock: AquariumLivestock, affectedCount: Int) {
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
            val recovered =
                closeReason == LivestockHealthUiSessionState.CLOSE_REASON_RECOVERED
            binding.tvFollowupBadge.setText(
                if (recovered) {
                    R.string.livestock_health_status_recovered
                } else {
                    R.string.livestock_health_followup_closed_manual
                }
            )
            binding.tvFollowupBadge.setTextColor(
                ContextCompat.getColor(
                    fragment.requireContext(),
                    if (recovered) {
                        R.color.aqua_status_success
                    } else {
                        R.color.aqua_card_text_secondary
                    }
                )
            )
            binding.cardStatusIncreased.isClickable = false
            binding.cardStatusSame.isClickable = false
            binding.cardStatusDecreased.isClickable = false
            binding.cardStatusRecovered.isClickable = false
        } else {
            binding.tvFollowupBadge.setText(R.string.livestock_health_tracking_badge)
            binding.tvFollowupBadge.setTextColor(
                ContextCompat.getColor(
                    fragment.requireContext(),
                    R.color.aqua_status_success
                )
            )
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
