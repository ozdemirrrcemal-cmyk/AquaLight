package com.aqua.aqualight.application.devices

/**
 * Screen-level application facade for the shared device Settings destination.
 *
 * The facade composes common identity, owner-editable name and Light protection contracts without
 * exposing repository or runtime-module types to presentation.
 */
interface DeviceFamilySettingsOperations :
    DeviceRootOperations,
    DeviceSettingsOperations,
    DeviceLightProtectionOperations
