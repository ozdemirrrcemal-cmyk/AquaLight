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
        if (rawValue.isBlank()) {
            return rejected(DeviceDosingReservoirCapacityRejection.REQUIRED)
        }
        val canonical = canonicalDecimalOrNull(rawValue, locale)
            ?: return rejected(DeviceDosingReservoirCapacityRejection.INVALID_NUMBER)
        val amount = canonical.toBigDecimalOrNull()
            ?: return rejected(DeviceDosingReservoirCapacityRejection.INVALID_NUMBER)
        if (amount.signum() <= 0) {
            return rejected(DeviceDosingReservoirCapacityRejection.POSITIVE_REQUIRED)
        }
        if (amount > MAX_CAPACITY_MILLILITERS) {
            return rejected(DeviceDosingReservoirCapacityRejection.OUT_OF_RANGE)
        }
        if (amount.stripTrailingZeros().scale() > MILLILITER_SCALE) {
            return rejected(DeviceDosingReservoirCapacityRejection.UNSUPPORTED_PRECISION)
        }
        val microliters = runCatching {
            amount.movePointRight(MILLILITER_SCALE).longValueExact()
        }.getOrNull() ?: return rejected(DeviceDosingReservoirCapacityRejection.OUT_OF_RANGE)
        return DeviceDosingReservoirCapacityValidation.Accepted(microliters)
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

    private fun canonicalDecimalOrNull(rawValue: String, locale: Locale): String? {
        val value = rawValue.trim()
        val symbols = DecimalFormatSymbols.getInstance(locale)
        val decimalSeparators = setOf(symbols.decimalSeparator, '.', ',')
        val output = StringBuilder(value.length)
        var hasDigit = false
        var hasDecimalSeparator = false

        value.forEachIndexed { index, character ->
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
                else -> return null
            }
        }
        if (!hasDigit) return null
        if (output.lastOrNull() == '.') output.append('0')
        return output.toString()
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
