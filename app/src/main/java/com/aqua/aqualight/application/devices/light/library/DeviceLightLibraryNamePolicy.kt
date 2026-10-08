package com.aqua.aqualight.application.devices.light.library

import java.text.Normalizer
import java.util.Locale

/** One canonical name policy shared by create, rename, persistence and presentation. */
object DeviceLightLibraryNamePolicy {
    const val MAX_LENGTH = 40

    sealed interface Validation {
        data class Valid(val name: CanonicalName) : Validation
        data class Invalid(val reason: InvalidReason) : Validation
    }

    data class CanonicalName(
        val display: String,
        val normalized: String
    )

    enum class InvalidReason {
        BLANK,
        TOO_LONG,
        CONTROL_CHARACTER
    }

    fun validate(rawName: String): Validation {
        val display = canonicalDisplay(rawName)
        return when {
            display.isBlank() -> Validation.Invalid(InvalidReason.BLANK)
            display.length > MAX_LENGTH -> Validation.Invalid(InvalidReason.TOO_LONG)
            display.any(Char::isISOControl) ->
                Validation.Invalid(InvalidReason.CONTROL_CHARACTER)
            else -> Validation.Valid(
                CanonicalName(
                    display = display,
                    normalized = normalize(display)
                )
            )
        }
    }

    fun canonicalDisplay(rawName: String): String = Normalizer
        .normalize(rawName, Normalizer.Form.NFKC)
        .trim()
        .replace(WHITESPACE, " ")

    fun normalize(canonicalDisplayName: String): String = Normalizer
        .normalize(canonicalDisplayName, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)

    private val WHITESPACE = Regex("\\s+")
}
