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
    val tankId: Long,
    val observationId: Long,
    val livestockId: Long,
    val livestockName: String,
    val category: String,
    val issueLabel: String,
    val affectedCount: Int,
    val totalCount: Int,
    val livestockPhotoUri: String?
)

internal fun ContentSheetLivestockHealthCheckBinding.bindCheckLivestock(
    request: LivestockHealthCheckSheetRequest
) {
    tvCheckLivestockName.text = request.livestockName
    tvCheckLivestockIssue.text = request.issueLabel
    ivCheckLivestock.bindRecordPhoto(
        request.livestockPhotoUri,
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
    val style = CheckStatusStyle(
        selectedStroke = ContextCompat.getColor(
            fragment.requireContext(),
            R.color.aqua_button_blue
        ),
        normalStroke = ContextCompat.getColor(
            fragment.requireContext(),
            R.color.aqua_card_outline
        ),
        selectedSurface = ContextCompat.getColor(
            fragment.requireContext(),
            R.color.aqua_surface_action
        ),
        normalSurface = ContextCompat.getColor(
            fragment.requireContext(),
            R.color.aqua_card_surface
        ),
        selectedWidth = fragment.resources.getDimensionPixelSize(R.dimen.aqua_size_2),
        normalWidth = fragment.resources.getDimensionPixelSize(R.dimen.aqua_size_1)
    )

    fun render(status: String) {
        cards.forEach { (key, card) ->
            card.renderCheckStatus(selected = key == status, style = style)
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

internal fun ContentSheetLivestockHealthCheckBinding.bindCheckPhotoSlots(
    onSlotSelected: (Int) -> Unit
) {
    checkPhotoSlotOne.setOnClickListener { onSlotSelected(0) }
    checkPhotoSlotTwo.setOnClickListener { onSlotSelected(1) }
    checkPhotoSlotThree.setOnClickListener { onSlotSelected(2) }
}

internal fun ContentSheetLivestockHealthCheckBinding.renderCheckPhotoSlots(
    photoUris: List<String?>
) {
    renderCheckPhotoSlot(
        photoUris.getOrNull(0),
        ivCheckPhotoPreviewOne,
        tvCheckPhotoSlotPlusOne
    )
    renderCheckPhotoSlot(
        photoUris.getOrNull(1),
        ivCheckPhotoPreviewTwo,
        tvCheckPhotoSlotPlusTwo
    )
    renderCheckPhotoSlot(
        photoUris.getOrNull(2),
        ivCheckPhotoPreviewThree,
        tvCheckPhotoSlotPlusThree
    )
}

private fun renderCheckPhotoSlot(
    photoUri: String?,
    preview: android.widget.ImageView,
    plus: android.widget.TextView
) {
    val hasPhoto = !photoUri.isNullOrBlank()
    preview.isVisible = hasPhoto
    plus.isVisible = !hasPhoto
    if (hasPhoto) {
        preview.bindRecordPhoto(photoUri)
    } else {
        preview.setImageDrawable(null)
    }
}

private data class CheckStatusStyle(
    val selectedStroke: Int,
    val normalStroke: Int,
    val selectedSurface: Int,
    val normalSurface: Int,
    val selectedWidth: Int,
    val normalWidth: Int
)

private fun MaterialCardView.renderCheckStatus(
    selected: Boolean,
    style: CheckStatusStyle
) {
    strokeWidth = if (selected) style.selectedWidth else style.normalWidth
    setStrokeColor(if (selected) style.selectedStroke else style.normalStroke)
    setCardBackgroundColor(
        if (selected) style.selectedSurface else style.normalSurface
    )
}

internal fun Bundle.toLivestockHealthCheckRequest(): LivestockHealthCheckSheetRequest {
    val totalCount = getInt(ARG_CHECK_TOTAL_COUNT)
    val affectedCount = getInt(ARG_CHECK_AFFECTED_COUNT)
    require(totalCount > 0) { "Livestock check total count must be positive." }
    require(affectedCount in 1..totalCount) {
        "Livestock check affected count must be within the tracked population."
    }
    return LivestockHealthCheckSheetRequest(
        tankId = getLong(ARG_CHECK_TANK_ID),
        observationId = getLong(ARG_CHECK_OBSERVATION_ID),
        livestockId = getLong(ARG_CHECK_LIVESTOCK_ID),
        livestockName = getString(ARG_CHECK_LIVESTOCK_NAME).orEmpty(),
        category = getString(ARG_CHECK_CATEGORY).orEmpty(),
        issueLabel = getString(ARG_CHECK_ISSUE_LABEL).orEmpty(),
        affectedCount = affectedCount,
        totalCount = totalCount,
        livestockPhotoUri = getString(ARG_CHECK_LIVESTOCK_PHOTO_URI)
    )
}

internal const val ARG_CHECK_LIVESTOCK_NAME = "livestock_name"
internal const val ARG_CHECK_ISSUE_LABEL = "issue_label"
internal const val ARG_CHECK_CATEGORY = "livestock_category"
internal const val ARG_CHECK_LIVESTOCK_ID = "livestock_id"
internal const val ARG_CHECK_TANK_ID = "tank_id"
internal const val ARG_CHECK_OBSERVATION_ID = "observation_id"
internal const val ARG_CHECK_AFFECTED_COUNT = "affected_count"
internal const val ARG_CHECK_TOTAL_COUNT = "total_count"
internal const val ARG_CHECK_LIVESTOCK_PHOTO_URI = "livestock_photo_uri"
