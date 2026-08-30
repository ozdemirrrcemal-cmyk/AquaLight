package com.aqua.aqualight.application.devices.dosing

/**
 * Presentation-independent parsing for one-time manual dose input.
 *
 * Firmware/channel limits are intentionally not duplicated here. The central channel operations
 * boundary validates the parsed amount against the latest authoritative scheduling policy before
 * any runtime command is sent.
 */
object DeviceDosingManualDoseDraftPolicy {
    fun parseMicroliters(rawValue: String): Long? = rawValue
        .trim()
        .replace(',', '.')
        .takeIf(String::isNotBlank)
        ?.toBigDecimalOrNull()
        ?.let(DeviceDosingAmountDraftPolicy::exactMicroliters)
}
