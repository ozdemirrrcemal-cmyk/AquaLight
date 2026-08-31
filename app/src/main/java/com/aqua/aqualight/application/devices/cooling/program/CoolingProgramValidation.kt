package com.aqua.aqualight.application.devices.cooling.program

sealed interface CoolingProgramValidationError {
    data class TooManySlots(
        val actualCount: Int,
        val maximumCount: Int
    ) : CoolingProgramValidationError

    data class SlotTooShort(
        val slotIndex: Int,
        val durationMinutes: Int,
        val minimumDurationMinutes: Int
    ) : CoolingProgramValidationError

    data class FanPercentOutOfRange(
        val slotIndex: Int,
        val fanPercent: Int
    ) : CoolingProgramValidationError

    data class FanPercentOffStep(
        val slotIndex: Int,
        val fanPercent: Int
    ) : CoolingProgramValidationError

    data class OverlappingSlots(
        val firstSlotIndex: Int,
        val secondSlotIndex: Int
    ) : CoolingProgramValidationError
}

sealed interface CoolingProgramValidationResult {
    data object Valid : CoolingProgramValidationResult
    data class Invalid(
        val errors: List<CoolingProgramValidationError>
    ) : CoolingProgramValidationResult
}

/** Pure validation for a same-day program using authoritative device-reported policy values. */
object CoolingProgramValidation {
    fun validate(
        slots: List<CoolingProgramSlot>,
        policy: CoolingProgramPolicy
    ): CoolingProgramValidationResult {
        val errors = buildList {
            if (slots.size > policy.maximumSlotCount) {
                add(
                    CoolingProgramValidationError.TooManySlots(
                        actualCount = slots.size,
                        maximumCount = policy.maximumSlotCount
                    )
                )
            }

            slots.forEachIndexed { index, slot ->
                val duration = slot.endMinutes - slot.startMinutes
                if (duration < policy.minimumSlotDurationMinutes) {
                    add(
                        CoolingProgramValidationError.SlotTooShort(
                            slotIndex = index,
                            durationMinutes = duration,
                            minimumDurationMinutes = policy.minimumSlotDurationMinutes
                        )
                    )
                }
                if (slot.fanLimitPercent !in policy.minimumFanPercent..policy.maximumFanPercent) {
                    add(
                        CoolingProgramValidationError.FanPercentOutOfRange(
                            slotIndex = index,
                            fanPercent = slot.fanLimitPercent
                        )
                    )
                } else if (
                    (slot.fanLimitPercent - policy.minimumFanPercent) % policy.fanPercentStep != 0
                ) {
                    add(
                        CoolingProgramValidationError.FanPercentOffStep(
                            slotIndex = index,
                            fanPercent = slot.fanLimitPercent
                        )
                    )
                }
            }

            val ordered = slots.withIndex().sortedBy { indexed -> indexed.value.startMinutes }
            ordered.zipWithNext().forEach { (left, right) ->
                if (left.value.endMinutes > right.value.startMinutes) {
                    add(
                        CoolingProgramValidationError.OverlappingSlots(
                            firstSlotIndex = left.index,
                            secondSlotIndex = right.index
                        )
                    )
                }
            }
        }

        return if (errors.isEmpty()) {
            CoolingProgramValidationResult.Valid
        } else {
            CoolingProgramValidationResult.Invalid(errors)
        }
    }
}
