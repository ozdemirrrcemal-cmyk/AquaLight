package com.aqua.aqualight.data.devices.dosing.v1

import kotlin.math.abs
import kotlin.math.round

internal object DeviceDosingV1AmountMapper {
    fun toMicroliters(value: Double, allowZero: Boolean = false): Long {
        require(value.isFinite()) { "Firmware Dosing amount must be finite." }
        val scaled = value * DeviceDosingV1Contract.Limit.AMOUNT_QUANTA_PER_ML
        val normalized = round(scaled)
        require(abs(scaled - normalized) <= NORMALIZATION_TOLERANCE) {
            "Firmware Dosing amount exceeds the application resolution."
        }
        val minimum = if (allowZero) 0L else 1L
        require(normalized in minimum.toDouble()..Long.MAX_VALUE.toDouble())
        return normalized.toLong()
    }

    fun toExactLong(value: Double): Long {
        require(value.isFinite() && value >= 0.0)
        val normalized = round(value)
        require(abs(value - normalized) <= NORMALIZATION_TOLERANCE)
        require(normalized <= Long.MAX_VALUE.toDouble())
        return normalized.toLong()
    }

    fun toWireAmount(value: Long): DeviceDosingV1Amount =
        DeviceDosingV1Amount.fromMilliliters(
            value.toDouble() / DeviceDosingV1Contract.Limit.AMOUNT_QUANTA_PER_ML
        )

    private const val NORMALIZATION_TOLERANCE = 0.000_001
}
