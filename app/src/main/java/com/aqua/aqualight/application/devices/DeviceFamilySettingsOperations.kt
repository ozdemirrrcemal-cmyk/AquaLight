package com.aqua.aqualight.application.devices

/**
 * Screen-level application facade for the shared device Settings destination.
 *
 * The facade composes common identity and owner-editable name contracts without exposing
 * repository types to presentation. Product runtime settings belong to their product screens.
 */
interface DeviceFamilySettingsOperations :
    DeviceRootOperations,
    DeviceSettingsOperations
