package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid

internal fun DeviceLightRuntimeRepository.currentDashboard(
    deviceUid: DeviceUid,
    authority: DeviceLightDashboardReadAuthority
): DeviceLightDashboardRuntimeState? = stateOwner.dashboardProjection.current(
    deviceUid,
    authority
)

internal fun DeviceLightRuntimeRepository.currentLibrary(
    deviceUid: DeviceUid,
    authority: DeviceLightLibraryReadAuthority
): DeviceLightLibraryRuntimeState? = stateOwner.libraryProjection.current(
    deviceUid,
    authority
)

internal fun DeviceLightRuntimeRepository.currentAutomatic(
    deviceUid: DeviceUid,
    authority: DeviceLightAutomaticReadAuthority
): DeviceLightAutomaticRuntimeState? = stateOwner.automaticProjection.current(
    deviceUid,
    authority
)

internal fun DeviceLightRuntimeRepository.currentSystem(
    deviceUid: DeviceUid,
    authority: DeviceLightSystemReadAuthority
): DeviceLightSystemRuntimeState? = stateOwner.systemProjection.current(deviceUid, authority)

internal fun DeviceLightRuntimeRepository.requiresLibraryCustomRefresh(
    deviceUid: DeviceUid
): Boolean = currentStatus(deviceUid) != null &&
    currentLibrary(deviceUid, DeviceLightLibraryReadAuthority.AUTHORITATIVE) == null

internal fun DeviceLightRuntimeRepository.requiresAutomaticProgramsRefresh(
    deviceUid: DeviceUid
): Boolean = currentStatus(deviceUid) != null &&
    stateOwner.automaticProjection.hasPresentation(deviceUid) &&
    currentAutomatic(deviceUid, DeviceLightAutomaticReadAuthority.AUTHORITATIVE) == null
