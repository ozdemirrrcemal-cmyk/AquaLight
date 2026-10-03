package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.databinding.ItemLivestockHealthActiveFollowupBinding
import com.aqua.aqualight.databinding.ItemLivestockHealthPastFollowupBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import java.util.concurrent.TimeUnit

internal fun Fragment.bindActiveFollowupCard(
    item: ItemLivestockHealthActiveFollowupBinding,
    livestock: AquariumLivestock,
    entry: ActiveLivestockFollowupUi
) {
    item.ivLivestock.bindRecordPhoto(
        livestock.photoUri,
        LivestockCategories.iconRes(livestock.category)
    )
    item.tvName.text = livestock.healthDisplayName(this)
    item.tvIssue.setText(LivestockHealthUiText.symptomLabelRes(entry.symptomKey))
    item.tvAffected.text = getString(
        R.string.livestock_health_affected_format,
        entry.affectedCount.coerceIn(1, livestock.quantity.coerceAtLeast(1)),
        livestock.quantity.coerceAtLeast(1)
    )
    item.tvLastCheck.text = getString(
        R.string.livestock_health_active_last_check_format,
        healthLastCheckLabel(entry.lastCheckAtMillis)
    )
}

internal fun Fragment.bindPastFollowupCard(
    item: ItemLivestockHealthPastFollowupBinding,
    livestock: AquariumLivestock,
    entry: ClosedLivestockFollowupUi
) {
    item.ivLivestock.bindRecordPhoto(
        livestock.photoUri,
        LivestockCategories.iconRes(livestock.category)
    )
    item.tvName.text = livestock.healthDisplayName(this)
    item.tvIssue.setText(LivestockHealthUiText.symptomLabelRes(entry.symptomKey))
    item.tvPeriod.text = getString(
        R.string.livestock_health_past_period_format,
        LocaleFormatter.formatDate(requireContext(), entry.startedAtMillis),
        LocaleFormatter.formatDate(requireContext(), entry.closedAtMillis),
        healthFollowupDurationDays(entry)
    )
    val recovered =
        entry.closeReason == LivestockHealthUiSessionState.CLOSE_REASON_RECOVERED
    item.tvCloseReason.text = getString(
        if (recovered) {
            R.string.livestock_health_past_recovered_format
        } else {
            R.string.livestock_health_past_manual_format
        },
        entry.checkCount
    )
    item.tvCloseReason.setTextColor(
        ContextCompat.getColor(
            requireContext(),
            if (recovered) {
                R.color.aqua_status_success
            } else {
                R.color.aqua_card_text_secondary
            }
        )
    )
}

private fun AquariumLivestock.healthDisplayName(fragment: Fragment): String = name.ifBlank {
    fragment.getString(R.string.aquarium_unnamed_livestock)
}

private fun Fragment.healthLastCheckLabel(millis: Long): String =
    if (millis > 0L) {
        getString(
            R.string.livestock_health_today_time_format,
            LocaleFormatter.formatTime(requireContext(), millis)
        )
    } else {
        getString(R.string.livestock_health_no_check_yet)
    }

private fun healthFollowupDurationDays(entry: ClosedLivestockFollowupUi): Long =
    TimeUnit.MILLISECONDS.toDays(
        (entry.closedAtMillis - entry.startedAtMillis).coerceAtLeast(0L)
    ).coerceAtLeast(1L)
