package com.aqua.aqualight.application.devices.cooling.program

import kotlin.math.round

enum class CoolingProgramEditRejection {
    SLOT_NOT_FOUND,
    MAXIMUM_SLOT_COUNT_REACHED,
    NO_FREE_WINDOW,
    INVALID_TIME_RANGE,
    INVALID_FAN_ON_TEMPERATURE,
    OVERLAP,
    INVALID_PROGRAM
}

sealed interface CoolingProgramEditResult {
    data class Updated(
        val slots: List<CoolingProgramSlot>,
        val selectedSlotIndex: Int? = null
    ) : CoolingProgramEditResult

    data class Rejected(
        val reason: CoolingProgramEditRejection
    ) : CoolingProgramEditResult
}

/** Pure same-day schedule transformations. No Android, persistence or firmware transport concerns. */
object CoolingProgramSchedule {
    fun addSlot(
        slots: List<CoolingProgramSlot>,
        policy: CoolingProgramPolicy
    ): CoolingProgramEditResult {
        val window = if (slots.size < policy.maximumSlotCount) {
            findNextFreeWindow(slots, policy.minimumSlotDurationMinutes)
        } else {
            null
        }
        return when {
            slots.size >= policy.maximumSlotCount -> CoolingProgramEditResult.Rejected(
                CoolingProgramEditRejection.MAXIMUM_SLOT_COUNT_REACHED
            )
            window == null -> CoolingProgramEditResult.Rejected(
                CoolingProgramEditRejection.NO_FREE_WINDOW
            )
            else -> {
                val newSlot = CoolingProgramSlot(
                    startMinutes = window.first,
                    endMinutes = window.second,
                    fanOnTemperatureC = policy.fanOnTemperature.defaultC,
                    targetFanPercent = policy.minimumFanPercent
                )
                validatedUpdate(
                    slots = slots + newSlot,
                    policy = policy,
                    selectedSlot = newSlot
                )
            }
        }
    }

    fun deleteSlot(
        slots: List<CoolingProgramSlot>,
        policy: CoolingProgramPolicy,
        slotIndex: Int
    ): CoolingProgramEditResult = if (slotIndex in slots.indices) {
        validatedUpdate(
            slots = slots.filterIndexed { index, _ -> index != slotIndex },
            policy = policy,
            selectedSlot = null
        )
    } else {
        CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.SLOT_NOT_FOUND)
    }

    fun updateStartTime(
        slots: List<CoolingProgramSlot>,
        policy: CoolingProgramPolicy,
        slotIndex: Int,
        startMinutes: Int
    ): CoolingProgramEditResult {
        val slot = slots.getOrNull(slotIndex)
        return when {
            slot == null -> CoolingProgramEditResult.Rejected(
                CoolingProgramEditRejection.SLOT_NOT_FOUND
            )
            startMinutes !in 0 until slot.endMinutes ||
                startMinutes % policy.timeStepMinutes != 0 ||
                slot.endMinutes - startMinutes < policy.minimumSlotDurationMinutes ->
                CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.INVALID_TIME_RANGE)
            else -> replaceSlot(
                slots = slots,
                policy = policy,
                slotIndex = slotIndex,
                replacement = slot.copy(startMinutes = startMinutes)
            )
        }
    }

    fun updateEndTime(
        slots: List<CoolingProgramSlot>,
        policy: CoolingProgramPolicy,
        slotIndex: Int,
        endMinutes: Int
    ): CoolingProgramEditResult {
        val slot = slots.getOrNull(slotIndex)
        return when {
            slot == null -> CoolingProgramEditResult.Rejected(
                CoolingProgramEditRejection.SLOT_NOT_FOUND
            )
            endMinutes !in 1..COOLING_PROGRAM_MINUTES_PER_DAY ||
                endMinutes % policy.timeStepMinutes != 0 ||
                endMinutes <= slot.startMinutes ||
                endMinutes - slot.startMinutes < policy.minimumSlotDurationMinutes ->
                CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.INVALID_TIME_RANGE)
            else -> replaceSlot(
                slots = slots,
                policy = policy,
                slotIndex = slotIndex,
                replacement = slot.copy(endMinutes = endMinutes)
            )
        }
    }

    fun updateTargetFanPercent(
        slots: List<CoolingProgramSlot>,
        policy: CoolingProgramPolicy,
        slotIndex: Int,
        percent: Int
    ): CoolingProgramEditResult {
        val slot = slots.getOrNull(slotIndex)
        return if (slot == null) {
            CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.SLOT_NOT_FOUND)
        } else {
            replaceSlot(
                slots = slots,
                policy = policy,
                slotIndex = slotIndex,
                replacement = slot.copy(targetFanPercent = snapFanPercent(percent, policy))
            )
        }
    }

    fun updateFanOnTemperature(
        slots: List<CoolingProgramSlot>,
        policy: CoolingProgramPolicy,
        slotIndex: Int,
        temperatureC: Double
    ): CoolingProgramEditResult {
        val slot = slots.getOrNull(slotIndex)
        return when {
            slot == null -> CoolingProgramEditResult.Rejected(
                CoolingProgramEditRejection.SLOT_NOT_FOUND
            )
            !temperatureC.isFinite() -> CoolingProgramEditResult.Rejected(
                CoolingProgramEditRejection.INVALID_FAN_ON_TEMPERATURE
            )
            else -> replaceSlot(
                slots = slots,
                policy = policy,
                slotIndex = slotIndex,
                replacement = slot.copy(
                    fanOnTemperatureC = snapFanOnTemperature(temperatureC, policy)
                )
            )
        }
    }

    fun activeSlotAt(
        slots: List<CoolingProgramSlot>,
        minutesOfDay: Int
    ): CoolingProgramSlot? {
        val minute = minutesOfDay.coerceIn(0, COOLING_PROGRAM_MINUTES_PER_DAY - 1)
        return slots.firstOrNull { slot -> minute in slot.startMinutes until slot.endMinutes }
    }

    fun isValidProgram(
        slots: List<CoolingProgramSlot>,
        policy: CoolingProgramPolicy
    ): Boolean = CoolingProgramValidation.validate(slots, policy) == CoolingProgramValidationResult.Valid
}

private fun replaceSlot(
    slots: List<CoolingProgramSlot>,
    policy: CoolingProgramPolicy,
    slotIndex: Int,
    replacement: CoolingProgramSlot
): CoolingProgramEditResult {
    val updated = slots.mapIndexed { index, slot ->
        if (index == slotIndex) replacement else slot
    }
    return validatedUpdate(updated, policy, selectedSlot = replacement)
}

private fun validatedUpdate(
    slots: List<CoolingProgramSlot>,
    policy: CoolingProgramPolicy,
    selectedSlot: CoolingProgramSlot?
): CoolingProgramEditResult = when (val validation = CoolingProgramValidation.validate(slots, policy)) {
    CoolingProgramValidationResult.Valid -> {
        val ordered = slots.sortedBy(CoolingProgramSlot::startMinutes)
        CoolingProgramEditResult.Updated(
            slots = ordered,
            selectedSlotIndex = selectedSlot?.let(ordered::indexOf)?.takeIf { it >= 0 }
        )
    }
    is CoolingProgramValidationResult.Invalid -> {
        val reason = if (
            validation.errors.any { error -> error is CoolingProgramValidationError.OverlappingSlots }
        ) {
            CoolingProgramEditRejection.OVERLAP
        } else {
            CoolingProgramEditRejection.INVALID_PROGRAM
        }
        CoolingProgramEditResult.Rejected(reason)
    }
}

private fun snapFanPercent(
    percent: Int,
    policy: CoolingProgramPolicy
): Int {
    val bounded = percent.coerceIn(policy.minimumFanPercent, policy.maximumFanPercent)
    val offset = bounded - policy.minimumFanPercent
    val roundedSteps = (offset + policy.fanPercentStep / 2) / policy.fanPercentStep
    return (policy.minimumFanPercent + roundedSteps * policy.fanPercentStep)
        .coerceAtMost(policy.maximumFanPercent)
}

private fun snapFanOnTemperature(
    temperatureC: Double,
    policy: CoolingProgramPolicy
): Double {
    val temperaturePolicy = policy.fanOnTemperature
    val bounded = temperatureC.coerceIn(temperaturePolicy.minimumC, temperaturePolicy.maximumC)
    val steps = round((bounded - temperaturePolicy.minimumC) / temperaturePolicy.stepC)
    return (temperaturePolicy.minimumC + steps * temperaturePolicy.stepC)
        .coerceIn(temperaturePolicy.minimumC, temperaturePolicy.maximumC)
}

private fun findNextFreeWindow(
    slots: List<CoolingProgramSlot>,
    durationMinutes: Int
): Pair<Int, Int>? {
    val ordered = slots.sortedBy(CoolingProgramSlot::startMinutes)
    var candidateStart = 0

    for (slot in ordered) {
        val candidateEnd = candidateStart + durationMinutes
        if (candidateEnd <= slot.startMinutes) {
            return candidateStart to candidateEnd
        }
        if (slot.endMinutes > candidateStart) {
            candidateStart = slot.endMinutes
        }
    }

    val candidateEnd = candidateStart + durationMinutes
    return if (candidateEnd <= COOLING_PROGRAM_MINUTES_PER_DAY) {
        candidateStart to candidateEnd
    } else {
        null
    }
}
