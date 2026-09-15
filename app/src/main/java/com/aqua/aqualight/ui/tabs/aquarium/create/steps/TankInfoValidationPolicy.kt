package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import com.aqua.aqualight.application.aquarium.AquariumTankDraft
import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumMeasurementPolicy

internal enum class TankInfoValidationError {
    SETUP_DATE_REQUIRED,
    TANK_SIZE_REQUIRED,
    INVALID_TANK_SIZE,
    TANK_TYPE_REQUIRED
}

internal object TankInfoValidationPolicy {

    fun validate(
        draft: AquariumTankDraft,
        isTankSizeConfirmed: Boolean
    ): TankInfoValidationError? {
        val hasValidDimensions = AquariumMeasurementPolicy.areValidDimensions(
            widthCm = draft.widthCm,
            lengthCm = draft.lengthCm,
            heightCm = draft.heightCm
        )
        return when {
            draft.setupDateEpochDay == null -> TankInfoValidationError.SETUP_DATE_REQUIRED
            !isTankSizeConfirmed -> TankInfoValidationError.TANK_SIZE_REQUIRED
            !hasValidDimensions -> TankInfoValidationError.INVALID_TANK_SIZE
            draft.tankType.isBlank() -> TankInfoValidationError.TANK_TYPE_REQUIRED
            else -> null
        }
    }
}
