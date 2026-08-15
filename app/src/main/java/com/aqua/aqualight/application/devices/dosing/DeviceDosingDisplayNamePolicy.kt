package com.aqua.aqualight.application.devices.dosing

/**
 * Application-owned display-name policy aligned with the persisted Dosing firmware contract.
 *
 * The byte budget is deliberately private: callers consume semantic acceptance/rejection and
 * never need to know how the firmware stores the name.
 */
object DeviceDosingDisplayNamePolicy {
    fun validateRequired(rawValue: String): DeviceDosingDisplayNameValidation {
        val normalizedValue = rawValue.trimFirmwareWhitespace()
        val rejection = when {
            normalizedValue.isEmpty() -> DeviceDosingDisplayNameRejection.REQUIRED
            normalizedValue.any { character -> character.isFirmwareRejectedControl() } ->
                DeviceDosingDisplayNameRejection.CONTROL_CHARACTER
            normalizedValue.toByteArray(Charsets.UTF_8).size > MAX_UTF8_BYTES ->
                DeviceDosingDisplayNameRejection.TOO_LONG
            else -> null
        }
        return rejection?.let(DeviceDosingDisplayNameValidation::Rejected)
            ?: DeviceDosingDisplayNameValidation.Accepted(normalizedValue)
    }

    private fun String.trimFirmwareWhitespace(): String = trim { character ->
        character == ' ' || character in '\t'..'\r'
    }

    private fun Char.isFirmwareRejectedControl(): Boolean = code < ASCII_SPACE || code == ASCII_DEL

    private const val MAX_UTF8_BYTES = 32
    private const val ASCII_SPACE = 0x20
    private const val ASCII_DEL = 0x7F
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
