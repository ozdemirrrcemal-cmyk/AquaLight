package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ContentSheetLivestockHealthCheckBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.media.bindRecordPhoto
import com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock.LivestockCategories
import com.google.android.material.card.MaterialCardView

internal data class LivestockHealthCheckSheetRequest(
    val livestockId: Long,
    val livestockName: String,
    val category: String,
    val issueLabel: String,
    val totalCount: Int,
    val photoUri: String?
)

internal fun ContentSheetLivestockHealthCheckBinding.bindCheckLivestock(
    request: LivestockHealthCheckSheetRequest
) {
    tvCheckLivestockName.text = request.livestockName
    tvCheckLivestockIssue.text = request.issueLabel
    ivCheckLivestock.bindRecordPhoto(
        request.photoUri,
        LivestockCategories.iconRes(request.category)
    )
}

internal fun ContentSheetLivestockHealthCheckBinding.bindCheckStatusCards(
    fragment: Fragment,
    selectedStatus: String,
    onSelected: (String) -> Unit
) {
    val cards = linkedMapOf(
        LivestockHealthCheckBottomSheet.STATUS_INCREASED to cardCheckIncreased,
        LivestockHealthCheckBottomSheet.STATUS_SAME to cardCheckSame,
        LivestockHealthCheckBottomSheet.STATUS_DECREASED to cardCheckDecreased,
        LivestockHealthCheckBottomSheet.STATUS_RECOVERED to cardCheckRecovered
    )
    val selectedStroke = ContextCompat.getColor(
        fragment.requireContext(),
        R.color.aqua_button_blue
    )
    val normalStroke = ContextCompat.getColor(
        fragment.requireContext(),
        R.color.aqua_card_outline
    )
    val selectedSurface = ContextCompat.getColor(
        fragment.requireContext(),
        R.color.aqua_surface_action
    )
    val normalSurface = ContextCompat.getColor(
        fragment.requireContext(),
        R.color.aqua_card_surface
    )

    fun render(status: String) {
        cards.forEach { (key, card) ->
            card.renderCheckStatus(
                selected = key == status,
                selectedStroke = selectedStroke,
                normalStroke = normalStroke,
                selectedSurface = selectedSurface,
                normalSurface = normalSurface,
                selectedWidth = fragment.resources.getDimensionPixelSize(
                    R.dimen.aqua_size_2
                ),
                normalWidth = fragment.resources.getDimensionPixelSize(
                    R.dimen.aqua_size_1
                )
            )
        }
    }

    cards.forEach { (status, card) ->
        card.setOnClickListener {
            onSelected(status)
            render(status)
        }
    }
    render(selectedStatus)
}

internal fun ContentSheetLivestockHealthCheckBinding.bindAffectedCounter(
    fragment: Fragment,
    currentCount: () -> Int,
    totalCount: () -> Int,
    onCountChanged: (Int) -> Unit
) {
    fun render(count: Int) {
        val total = totalCount().coerceAtLeast(1)
        val normalized = count.coerceIn(1, total)
        onCountChanged(normalized)
        tvCheckAffectedCount.text = fragment.getString(
            R.string.livestock_health_affected_counter_format,
            normalized,
            total
        )
        btnCheckMinus.isEnabled = normalized > 1
        btnCheckPlus.isEnabled = normalized < total
    }

    btnCheckMinus.setOnClickListener { render(currentCount() - 1) }
    btnCheckPlus.setOnClickListener { render(currentCount() + 1) }
    render(currentCount())
}

internal fun ContentSheetLivestockHealthCheckBinding.renderCheckTime(
    fragment: Fragment,
    selectedTimeMillis: Long
) {
    tvCheckTimeValue.text = fragment.getString(
        R.string.livestock_health_check_time_today_format,
        LocaleFormatter.formatTime(fragment.requireContext(), selectedTimeMillis)
    )
}

internal fun ContentSheetLivestockHealthCheckBinding.renderCheckPhoto(photoUri: String?) {
    val hasPhoto = !photoUri.isNullOrBlank()
    ivCheckPhotoPreview.isVisible = hasPhoto
    ivCheckPhotoPlaceholder.isVisible = !hasPhoto
    btnRemoveCheckPhoto.isVisible = hasPhoto
    if (hasPhoto) {
        ivCheckPhotoPreview.bindRecordPhoto(photoUri)
    } else {
        ivCheckPhotoPreview.setImageDrawable(null)
    }
}

private fun MaterialCardView.renderCheckStatus(
    selected: Boolean,
    selectedStroke: Int,
    normalStroke: Int,
    selectedSurface: Int,
    normalSurface: Int,
    selectedWidth: Int,
    normalWidth: Int
) {
    strokeWidth = if (selected) selectedWidth else normalWidth
    setStrokeColor(if (selected) selectedStroke else normalStroke)
    setCardBackgroundColor(if (selected) selectedSurface else normalSurface)
}

internal fun Bundle.toLivestockHealthCheckRequest(): LivestockHealthCheckSheetRequest =
    LivestockHealthCheckSheetRequest(
        livestockId = getLong(ARG_CHECK_LIVESTOCK_ID),
        livestockName = getString(ARG_CHECK_LIVESTOCK_NAME).orEmpty(),
        category = getString(ARG_CHECK_CATEGORY).orEmpty(),
        issueLabel = getString(ARG_CHECK_ISSUE_LABEL).orEmpty(),
        totalCount = getInt(ARG_CHECK_TOTAL_COUNT, 1).coerceAtLeast(1),
        photoUri = getString(ARG_CHECK_PHOTO_URI)
    )

internal const val ARG_CHECK_LIVESTOCK_NAME = "livestock_name"
internal const val ARG_CHECK_ISSUE_LABEL = "issue_label"
internal const val ARG_CHECK_CATEGORY = "livestock_category"
internal const val ARG_CHECK_LIVESTOCK_ID = "livestock_id"
internal const val ARG_CHECK_TOTAL_COUNT = "total_count"
internal const val ARG_CHECK_PHOTO_URI = "photo_uri"
