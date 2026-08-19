package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card

internal fun Long.toMilliliters(): Double = toDouble() / MICROLITERS_PER_MILLILITER

private const val MICROLITERS_PER_MILLILITER = 1_000.0
