package com.aqua.aqualight.application.devices.dosing

/**
 * Returns the firmware-projected next pending occurrence for the active program day.
 *
 * The occurrence list is already compiled and ordered by firmware. Android deliberately preserves
 * that order and does not reproduce program-day selection, weekday logic, wrap handling or clock
 * scheduling. A stale or unanchored projection fails closed instead of inventing a next dose.
 */
internal fun DeviceDosingChannelProgress.nextScheduledOccurrence(): DeviceDosingOccurrenceProgress? {
    if (
        scheduleState != DeviceDosingScheduleState.ACTIVE ||
        !executionCurrent ||
        programDayDate == null
    ) {
        return null
    }
    return occurrences.firstOrNull { occurrence ->
        occurrence.state == DeviceDosingOccurrenceState.PENDING
    }
}
