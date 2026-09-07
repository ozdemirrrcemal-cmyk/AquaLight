package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily

/** Shared presentation projection of the central device-root availability contract. */
internal fun DeviceRootSnapshot?.isCoolingContentAvailable(): Boolean =
    this?.family == OwnerDeviceFamily.COOLING &&
        availability == OwnerDeviceAvailability.REACHABLE &&
        catalogState == DeviceRootCatalogState.VALID
