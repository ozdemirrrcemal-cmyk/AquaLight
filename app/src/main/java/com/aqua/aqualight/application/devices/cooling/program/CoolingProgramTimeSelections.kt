package com.aqua.aqualight.application.devices.cooling.program

/**
 * A firmware-policy-aligned set of values that an editor may present for one program time field.
 * The current value is always part of [selectableMinutesOfDay].
 */
data class CoolingProgramTimeSelection(
    val currentMinutesOfDay: Int,
    val selectableMinutesOfDay: List<Int>
) {
    init {
        require(selectableMinutesOfDay.isNotEmpty())
        require(selectableMinutesOfDay == selectableMinutesOfDay.distinct().sorted())
        require(currentMinutesOfDay in selectableMinutesOfDay)
    }
}

/**
 * Builds picker options by exercising the same application transitions used to apply edits.
 * Presentation therefore cannot offer a time that the program state machine would reject.
 */
object CoolingProgramTimeSelections {
    fun forStartTime(
        slots: List<CoolingProgramSlot>,
        policy: CoolingProgramPolicy,
        slotIndex: Int
    ): CoolingProgramTimeSelection? {
        val slot = slots.getOrNull(slotIndex) ?: return null
        return selection(
            currentMinutesOfDay = slot.startMinutes,
            candidates = 0 until COOLING_PROGRAM_MINUTES_PER_DAY step policy.timeStepMinutes
        ) { candidate ->
            CoolingProgramSchedule.updateStartTime(
                slots = slots,
                policy = policy,
                slotIndex = slotIndex,
                startMinutes = candidate
            )
        }
    }

    fun forEndTime(
        slots: List<CoolingProgramSlot>,
        policy: CoolingProgramPolicy,
        slotIndex: Int
    ): CoolingProgramTimeSelection? {
        val slot = slots.getOrNull(slotIndex) ?: return null
        return selection(
            currentMinutesOfDay = slot.endMinutes,
            candidates = (policy.timeStepMinutes..COOLING_PROGRAM_MINUTES_PER_DAY)
                .step(policy.timeStepMinutes)
        ) { candidate ->
            CoolingProgramSchedule.updateEndTime(
                slots = slots,
                policy = policy,
                slotIndex = slotIndex,
                endMinutes = candidate
            )
        }
    }
}

private inline fun selection(
    currentMinutesOfDay: Int,
    candidates: IntProgression,
    edit: (Int) -> CoolingProgramEditResult
): CoolingProgramTimeSelection {
    val selectable = candidates.filter { candidate ->
        edit(candidate) is CoolingProgramEditResult.Updated
    }
    return CoolingProgramTimeSelection(
        currentMinutesOfDay = currentMinutesOfDay,
        selectableMinutesOfDay = selectable
    )
}
