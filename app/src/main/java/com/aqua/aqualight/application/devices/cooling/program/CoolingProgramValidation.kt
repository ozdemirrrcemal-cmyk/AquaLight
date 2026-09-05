package com.aqua.aqualight.application.devices.cooling.program

sealed interface CoolingProgramValidationError {
    data class TooManySlots(
        val actualCount: Int,
        val maximumCount: Int
    ) : CoolingProgramValidationError

    data class SlotTimeOffStep(
        val slotIndex: Int,
        val timeStepMinutes: Int
    ) : CoolingProgramValidationError

    data class SlotTooShort(
        val slotIndex: Int,
        val durationMinutes: Int,
        val minimumDurationMinutes: Int
    ) : CoolingProgramValidationError

    data class TargetFanPercentOutOfRange(
        val slotIndex: Int,
        val fanPercent: Int
    ) : CoolingProgramValidationError

    data class TargetFanPercentOffStep(
        val slotIndex: Int,
        val fanPercent: Int
    ) : CoolingProgramValidationError

    data class FanOnTemperatureOutOfRange(
        val slotIndex: Int,
        val temperatureC: Double
    ) : CoolingProgramValidationError

    data class FanOnTemperatureOffStep(
        val slotIndex: Int,
        val temperatureC: Double
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
            addProgramSizeError(slots, policy)
            slots.forEachIndexed { index, slot ->
                addSlotErrors(index, slot, policy)
            }
            addOverlapErrors(slots)
        }

        return if (errors.isEmpty()) {
            CoolingProgramValidationResult.Valid
        } else {
            CoolingProgramValidationResult.Invalid(errors)
        }
    }
}

private fun MutableList<CoolingProgramValidationError>.addProgramSizeError(
    slots: List<CoolingProgramSlot>,
    policy: CoolingProgramPolicy
) {
    if (slots.size > policy.maximumSlotCount) {
        add(
            CoolingProgramValidationError.TooManySlots(
                actualCount = slots.size,
                maximumCount = policy.maximumSlotCount
            )
        )
    }
}

private fun MutableList<CoolingProgramValidationError>.addSlotErrors(
    index: Int,
    slot: CoolingProgramSlot,
    policy: CoolingProgramPolicy
) {
    if (
        slot.startMinutes % policy.timeStepMinutes != 0 ||
        slot.endMinutes % policy.timeStepMinutes != 0
    ) {
        add(
            CoolingProgramValidationError.SlotTimeOffStep(
                slotIndex = index,
                timeStepMinutes = policy.timeStepMinutes
            )
        )
    }
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
    addTargetFanErrors(index, slot, policy)
    addFanOnTemperatureErrors(index, slot, policy)
}

private fun MutableList<CoolingProgramValidationError>.addTargetFanErrors(
    index: Int,
    slot: CoolingProgramSlot,
    policy: CoolingProgramPolicy
) {
    if (slot.targetFanPercent !in policy.minimumFanPercent..policy.maximumFanPercent) {
        add(
            CoolingProgramValidationError.TargetFanPercentOutOfRange(
                slotIndex = index,
                fanPercent = slot.targetFanPercent
            )
        )
    } else if (
        (slot.targetFanPercent - policy.minimumFanPercent) % policy.fanPercentStep != 0
    ) {
        add(
            CoolingProgramValidationError.TargetFanPercentOffStep(
                slotIndex = index,
                fanPercent = slot.targetFanPercent
            )
        )
    }
}

private fun MutableList<CoolingProgramValidationError>.addFanOnTemperatureErrors(
    index: Int,
    slot: CoolingProgramSlot,
    policy: CoolingProgramPolicy
) {
    val temperaturePolicy = policy.fanOnTemperature
    if (slot.fanOnTemperatureC !in temperaturePolicy.minimumC..temperaturePolicy.maximumC) {
        add(
            CoolingProgramValidationError.FanOnTemperatureOutOfRange(
                slotIndex = index,
                temperatureC = slot.fanOnTemperatureC
            )
        )
    } else if (
        !isTemperatureAligned(
            value = slot.fanOnTemperatureC,
            origin = temperaturePolicy.minimumC,
            step = temperaturePolicy.stepC
        )
    ) {
        add(
            CoolingProgramValidationError.FanOnTemperatureOffStep(
                slotIndex = index,
                temperatureC = slot.fanOnTemperatureC
            )
        )
    }
}

private fun MutableList<CoolingProgramValidationError>.addOverlapErrors(
    slots: List<CoolingProgramSlot>
) {
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
