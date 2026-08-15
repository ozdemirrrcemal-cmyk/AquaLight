package com.aqua.aqualight.application.devices.dosing

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Exact application policy for the firmware-backed reservoir-capacity intent. */
object DeviceDosingReservoirCapacityPolicy {
    const val DEFAULT_CAPACITY_MICROLITERS = 450_000L

    fun validate(
        rawValue: String,
        locale: Locale
    ): DeviceDosingReservoirCapacityValidation {
        val canonical = rawValue
            .takeUnless(String::isBlank)
            ?.let { value -> canonicalDecimalOrNull(value, locale) }
        return when {
            rawValue.isBlank() -> rejected(DeviceDosingReservoirCapacityRejection.REQUIRED)
            canonical == null ->
                rejected(DeviceDosingReservoirCapacityRejection.INVALID_NUMBER)
            else -> validateCanonicalDecimal(canonical)
        }
    }

    fun format(capacityMicroliters: Long, locale: Locale): String {
        val exactCapacity = normalizePersistedMicroliters(capacityMicroliters)
        return DecimalFormat(
            "0.###",
            DecimalFormatSymbols.getInstance(locale)
        ).apply {
            isGroupingUsed = false
            roundingMode = RoundingMode.UNNECESSARY
        }.format(BigDecimal.valueOf(exactCapacity, MILLILITER_SCALE))
    }

    fun normalizePersistedMicroliters(capacityMicroliters: Long?): Long =
        capacityMicroliters
            ?.takeIf(::isSupportedMicroliters)
            ?: DEFAULT_CAPACITY_MICROLITERS

    internal fun isSupportedMicroliters(capacityMicroliters: Long): Boolean =
        capacityMicroliters in 1L..MAX_CAPACITY_MICROLITERS

    private fun validateCanonicalDecimal(
        canonical: String
    ): DeviceDosingReservoirCapacityValidation {
        val amount = canonical.toBigDecimalOrNull()
            ?: return rejected(DeviceDosingReservoirCapacityRejection.INVALID_NUMBER)
        val rejection = when {
            amount.signum() <= 0 -> DeviceDosingReservoirCapacityRejection.POSITIVE_REQUIRED
            amount > MAX_CAPACITY_MILLILITERS ->
                DeviceDosingReservoirCapacityRejection.OUT_OF_RANGE
            amount.stripTrailingZeros().scale() > MILLILITER_SCALE ->
                DeviceDosingReservoirCapacityRejection.UNSUPPORTED_PRECISION
            else -> null
        }
        return rejection?.let(::rejected)
            ?: DeviceDosingReservoirCapacityValidation.Accepted(
                amount.movePointRight(MILLILITER_SCALE).longValueExact()
            )
    }

    private fun canonicalDecimalOrNull(rawValue: String, locale: Locale): String? {
        val value = rawValue.trim()
        val symbols = DecimalFormatSymbols.getInstance(locale)
        val decimalSeparators = setOf(symbols.decimalSeparator, '.', ',')
        val output = StringBuilder(value.length)
        var hasDigit = false
        var hasDecimalSeparator = false

        var invalidCharacter = false
        for (index in value.indices) {
            val character = value[index]
            val digit = Character.digit(character, DECIMAL_RADIX)
            when {
                digit >= 0 -> {
                    output.append(digit)
                    hasDigit = true
                }
                character in decimalSeparators && !hasDecimalSeparator -> {
                    output.append('.')
                    hasDecimalSeparator = true
                }
                index == 0 && character == '+' -> output.append(character)
                index == 0 && (character == '-' || character == symbols.minusSign) ->
                    output.append('-')
                else -> invalidCharacter = true
            }
            if (invalidCharacter) break
        }
        if (output.lastOrNull() == '.') output.append('0')
        return output
            .takeIf { !invalidCharacter && hasDigit }
            ?.toString()
    }

    private fun rejected(
        reason: DeviceDosingReservoirCapacityRejection
    ) = DeviceDosingReservoirCapacityValidation.Rejected(reason)

    private val MAX_CAPACITY_MILLILITERS = BigDecimal("4294967.295")
    private const val MAX_CAPACITY_MICROLITERS = 4_294_967_295L
    private const val MILLILITER_SCALE = 3
    private const val DECIMAL_RADIX = 10
}

sealed interface DeviceDosingReservoirCapacityValidation {
    data class Accepted(
        val capacityMicroliters: Long
    ) : DeviceDosingReservoirCapacityValidation

    data class Rejected(
        val reason: DeviceDosingReservoirCapacityRejection
    ) : DeviceDosingReservoirCapacityValidation
}

enum class DeviceDosingReservoirCapacityRejection {
    REQUIRED,
    INVALID_NUMBER,
    POSITIVE_REQUIRED,
    UNSUPPORTED_PRECISION,
    OUT_OF_RANGE
}
