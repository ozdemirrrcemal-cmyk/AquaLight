package com.aqua.aqualight.ui.tabs.aquarium.create

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankDraft
import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumMeasurementPolicy

object CreateTankInfoValidationPolicy {

    enum class Issue(
        @StringRes val messageRes: Int
    ) {
        SETUP_DATE_REQUIRED(R.string.aquarium_validation_setup_date_required),
        SIZE_REQUIRED(R.string.aquarium_validation_tank_size_required),
        TANK_TYPE_REQUIRED(R.string.aquarium_validation_tank_type_required)
    }

    fun firstIssue(draft: AquariumTankDraft): Issue? {
        val hasValidDimensions = AquariumMeasurementPolicy.areValidDimensions(
            widthCm = draft.widthCm,
            lengthCm = draft.lengthCm,
            heightCm = draft.heightCm
        )

        return when {
            draft.setupDateEpochDay == null -> Issue.SETUP_DATE_REQUIRED
            !hasValidDimensions -> Issue.SIZE_REQUIRED
            draft.tankType.isBlank() -> Issue.TANK_TYPE_REQUIRED
            else -> null
        }
    }
}
