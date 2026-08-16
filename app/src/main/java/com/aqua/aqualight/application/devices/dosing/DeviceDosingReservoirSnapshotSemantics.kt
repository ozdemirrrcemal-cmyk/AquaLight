package com.aqua.aqualight.application.devices.dosing

/**
 * Stable application semantic for reservoir attention state.
 *
 * The underlying firmware signal remains owned by the application/data boundary so UI code never
 * depends on wire-oriented low-level status naming.
 */
internal val DeviceDosingReservoirSnapshot.requiresLowReservoirAttention: Boolean
    get() = lowLevelActive
