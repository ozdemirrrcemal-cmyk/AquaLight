package com.aqua.aqualight.application.devices.dosing

/**
 * Application-owned display-name policy aligned with the persisted Dosing firmware contract.
 *
 * The byte budget is deliberately private: callers consume semantic acceptance/rejection and
 * never need to know how the firmware stores the name.
 */
object DeviceDosingDisplayNamePolicy {
    fun validateRequired(rawValue: String): DeviceDosingDisplayNameValidation {
        val normalizedValue = rawValue.trim()
        val rejection = when {
            normalizedValue.isEmpty() -> DeviceDosingDisplayNameRejection.REQUIRED
            normalizedValue.any(Char::isISOControl) ->
                DeviceDosingDisplayNameRejection.CONTROL_CHARACTER
            normalizedValue.toByteArray(Charsets.UTF_8).size > MAX_UTF8_BYTES ->
                DeviceDosingDisplayNameRejection.TOO_LONG
            else -> null
        }
        return rejection?.let(DeviceDosingDisplayNameValidation::Rejected)
            ?: DeviceDosingDisplayNameValidation.Accepted(normalizedValue)
    }

    private const val MAX_UTF8_BYTES = 32
}

sealed interface DeviceDosingDisplayNameValidation {
    data class Accepted(
        val normalizedValue: String
    ) : DeviceDosingDisplayNameValidation

    data class Rejected(
        val reason: DeviceDosingDisplayNameRejection
    ) : DeviceDosingDisplayNameValidation
}

enum class DeviceDosingDisplayNameRejection {
    REQUIRED,
    CONTROL_CHARACTER,
    TOO_LONG
}
