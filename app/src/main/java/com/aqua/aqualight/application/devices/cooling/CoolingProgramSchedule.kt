package com.aqua.aqualight.application.devices.cooling

private const val DEFAULT_NEW_SLOT_DURATION_MINUTES = 60
private const val NEW_SLOT_SCAN_STEP_MINUTES = 30

enum class CoolingProgramEditRejection {
    SLOT_NOT_FOUND,
    DUPLICATE_SLOT_ID,
    MAXIMUM_SLOT_COUNT_REACHED,
    MINIMUM_SLOT_COUNT_REQUIRED,
    NO_FREE_WINDOW,
    INVALID_TIME_RANGE,
    OVERLAP
}

sealed interface CoolingProgramEditResult {
    data class Updated(val slots: List<CoolingProgramSlot>) : CoolingProgramEditResult
    data class Rejected(val reason: CoolingProgramEditRejection) : CoolingProgramEditResult
}

/** Pure product rules for same-day fan program editing. */
object CoolingProgramSchedule {

    fun addSlot(
        slots: List<CoolingProgramSlot>,
        capabilities: CoolingProgramCapabilities,
        newSlotId: String
    ): CoolingProgramEditResult {
        if (newSlotId.isBlank() || slots.any { slot -> slot.id == newSlotId }) {
            return CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.DUPLICATE_SLOT_ID)
        }
        if (slots.size >= capabilities.maximumSlotCount) {
            return CoolingProgramEditResult.Rejected(
                CoolingProgramEditRejection.MAXIMUM_SLOT_COUNT_REACHED
            )
        }
        val window = findNextFreeWindow(slots, capabilities)
            ?: return CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.NO_FREE_WINDOW)
        val newSlot = CoolingProgramSlot(
            id = newSlotId,
            startMinutes = window.first,
            endMinutes = window.second,
            fanLimitPercent = capabilities.maximumFanLimitPercent
        )
        return validatedUpdate(slots + newSlot, capabilities)
    }

    fun deleteSlot(
        slots: List<CoolingProgramSlot>,
        capabilities: CoolingProgramCapabilities,
        slotId: String
    ): CoolingProgramEditResult {
        if (slots.none { slot -> slot.id == slotId }) {
            return CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.SLOT_NOT_FOUND)
        }
        if (slots.size <= capabilities.minimumSlotCount) {
            return CoolingProgramEditResult.Rejected(
                CoolingProgramEditRejection.MINIMUM_SLOT_COUNT_REQUIRED
            )
        }
        return CoolingProgramEditResult.Updated(
            slots.filterNot { slot -> slot.id == slotId }
                .sortedBy(CoolingProgramSlot::startMinutes)
        )
    }

    fun updateStartTime(
        slots: List<CoolingProgramSlot>,
        capabilities: CoolingProgramCapabilities,
        slotId: String,
        startMinutes: Int
    ): CoolingProgramEditResult {
        val slot = slots.firstOrNull { candidate -> candidate.id == slotId }
            ?: return CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.SLOT_NOT_FOUND)
        if (startMinutes !in 0 until slot.endMinutes ||
            slot.endMinutes - startMinutes < capabilities.minimumSlotDurationMinutes
        ) {
            return CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.INVALID_TIME_RANGE)
        }
        return replaceSlot(
            slots = slots,
            capabilities = capabilities,
            replacement = slot.copy(startMinutes = startMinutes)
        )
    }

    fun updateEndTime(
        slots: List<CoolingProgramSlot>,
        capabilities: CoolingProgramCapabilities,
        slotId: String,
        endMinutes: Int
    ): CoolingProgramEditResult {
        val slot = slots.firstOrNull { candidate -> candidate.id == slotId }
            ?: return CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.SLOT_NOT_FOUND)
        if (endMinutes !in 1 until COOLING_PROGRAM_MINUTES_PER_DAY ||
            endMinutes <= slot.startMinutes ||
            endMinutes - slot.startMinutes < capabilities.minimumSlotDurationMinutes
        ) {
            return CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.INVALID_TIME_RANGE)
        }
        return replaceSlot(
            slots = slots,
            capabilities = capabilities,
            replacement = slot.copy(endMinutes = endMinutes)
        )
    }

    fun updateFanLimit(
        slots: List<CoolingProgramSlot>,
        capabilities: CoolingProgramCapabilities,
        slotId: String,
        percent: Int
    ): CoolingProgramEditResult {
        val slot = slots.firstOrNull { candidate -> candidate.id == slotId }
            ?: return CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.SLOT_NOT_FOUND)
        return replaceSlot(
            slots = slots,
            capabilities = capabilities,
            replacement = slot.copy(fanLimitPercent = snapFanLimit(percent, capabilities))
        )
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
        capabilities: CoolingProgramCapabilities
    ): Boolean {
        if (slots.size !in capabilities.minimumSlotCount..capabilities.maximumSlotCount) {
            return false
        }
        if (slots.map(CoolingProgramSlot::id).toSet().size != slots.size) {
            return false
        }
        if (slots.any { slot -> !isValidSlot(slot, capabilities) }) {
            return false
        }
        return !hasOverlap(slots)
    }

    private fun replaceSlot(
        slots: List<CoolingProgramSlot>,
        capabilities: CoolingProgramCapabilities,
        replacement: CoolingProgramSlot
    ): CoolingProgramEditResult {
        val updated = slots.map { slot ->
            if (slot.id == replacement.id) replacement else slot
        }
        return validatedUpdate(updated, capabilities)
    }

    private fun validatedUpdate(
        slots: List<CoolingProgramSlot>,
        capabilities: CoolingProgramCapabilities
    ): CoolingProgramEditResult {
        if (slots.map(CoolingProgramSlot::id).toSet().size != slots.size) {
            return CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.DUPLICATE_SLOT_ID)
        }
        if (slots.any { slot -> !isValidSlot(slot, capabilities) }) {
            return CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.INVALID_TIME_RANGE)
        }
        if (hasOverlap(slots)) {
            return CoolingProgramEditResult.Rejected(CoolingProgramEditRejection.OVERLAP)
        }
        return CoolingProgramEditResult.Updated(slots.sortedBy(CoolingProgramSlot::startMinutes))
    }

    private fun isValidSlot(
        slot: CoolingProgramSlot,
        capabilities: CoolingProgramCapabilities
    ): Boolean {
        val fanOffset = slot.fanLimitPercent - capabilities.minimumFanLimitPercent
        return slot.endMinutes - slot.startMinutes >= capabilities.minimumSlotDurationMinutes &&
            slot.fanLimitPercent in
            capabilities.minimumFanLimitPercent..capabilities.maximumFanLimitPercent &&
            fanOffset % capabilities.fanLimitStepPercent == 0
    }

    private fun hasOverlap(slots: List<CoolingProgramSlot>): Boolean {
        val ordered = slots.sortedBy(CoolingProgramSlot::startMinutes)
        return ordered.zipWithNext().any { (left, right) -> left.endMinutes > right.startMinutes }
    }

    private fun snapFanLimit(
        percent: Int,
        capabilities: CoolingProgramCapabilities
    ): Int {
        val bounded = percent.coerceIn(
            capabilities.minimumFanLimitPercent,
            capabilities.maximumFanLimitPercent
        )
        val offset = bounded - capabilities.minimumFanLimitPercent
        val step = capabilities.fanLimitStepPercent
        val roundedSteps = (offset + step / 2) / step
        return capabilities.minimumFanLimitPercent + roundedSteps * step
    }

    private fun findNextFreeWindow(
        slots: List<CoolingProgramSlot>,
        capabilities: CoolingProgramCapabilities
    ): Pair<Int, Int>? {
        val duration = maxOf(
            DEFAULT_NEW_SLOT_DURATION_MINUTES,
            capabilities.minimumSlotDurationMinutes
        )
        val latestStart = COOLING_PROGRAM_MINUTES_PER_DAY - duration - 1
        if (latestStart < 0) return null

        val occupied = BooleanArray(COOLING_PROGRAM_MINUTES_PER_DAY)
        slots.forEach { slot ->
            for (minute in slot.startMinutes until slot.endMinutes) {
                occupied[minute] = true
            }
        }

        for (start in 0..latestStart step NEW_SLOT_SCAN_STEP_MINUTES) {
            val end = start + duration
            if ((start until end).all { minute -> !occupied[minute] }) {
                return start to end
            }
        }
        return null
    }
}
