package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSupplyProjectionPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSupplySeverity

internal fun DeviceDosingChannelSnapshot.toReservoirUiState(): DosingReservoirUiState? {
    val projection = DeviceDosingSupplyProjectionPolicy.evaluate(this) ?: return null
    val remainingAvailable = reservoir.remainingAvailable &&
        reservoir.accountingCertain &&
        deliveryAccountingCertain
    return DosingReservoirUiState(
        remainingMl = reservoir.remainingMicroliters.toMilliliters(),
        fillFraction = reservoir.fillFraction(),
        estimatedRemainingDays = projection.estimatedRemainingDays,
        tone = projection.supplySeverity.toUiTone(),
        remainingAvailable = remainingAvailable,
        fillAvailable = remainingAvailable && reservoir.capacityMicroliters > 0L
    )
}

private fun DeviceDosingReservoirSnapshot.fillFraction(): Float =
    capacityMicroliters.takeIf { capacity -> capacity > 0L }
        ?.let { capacity ->
            (remainingMicroliters.toDouble() / capacity).coerceIn(0.0, 1.0).toFloat()
        }
        ?: 0f

private fun DeviceDosingSupplySeverity.toUiTone(): DosingReservoirTone = when (this) {
    DeviceDosingSupplySeverity.NORMAL -> DosingReservoirTone.NORMAL
    DeviceDosingSupplySeverity.WARNING -> DosingReservoirTone.WARNING
    DeviceDosingSupplySeverity.CRITICAL -> DosingReservoirTone.CRITICAL
    DeviceDosingSupplySeverity.UNCERTAIN -> DosingReservoirTone.UNCERTAIN
}
