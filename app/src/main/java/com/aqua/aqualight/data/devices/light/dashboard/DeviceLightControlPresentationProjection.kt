package com.aqua.aqualight.data.devices.light.dashboard

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlFailure
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlSnapshot
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemReadResult
import com.aqua.aqualight.data.devices.light.supportsLightSystemPresentation
import com.aqua.aqualight.data.devices.light.system.projectLightSystemPresentationSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightDashboardReadAuthority
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightLibraryReadAuthority
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightSystemReadAuthority
import com.aqua.aqualight.data.devices.runtime.modules.light.currentDashboard
import com.aqua.aqualight.data.devices.runtime.modules.light.currentLibrary
import com.aqua.aqualight.data.devices.runtime.modules.light.currentSystem

/**
 * Central presentation projection over the single Light runtime state owner.
 *
 * Presentation mirrors Dosing semantics: reconnect may revoke write authority, but it does not
 * erase the last fully validated frame. Authoritative current/refresh/mutation paths remain in
 * [DefaultDeviceLightControlOperations] and continue to require current DeviceRoot authority.
 */
internal fun projectLightControlPresentationRead(
    deviceUid: DeviceUid,
    runtime: DeviceLightRuntimeRepository
): DeviceLightControlResult {
    val dashboard = runtime.currentDashboard(
        deviceUid,
        DeviceLightDashboardReadAuthority.PRESENTATION
    ) ?: return DeviceLightControlResult.Failed(DeviceLightControlFailure.UNAVAILABLE)
    val status = dashboard.status
    val library = runtime.currentLibrary(
        deviceUid,
        DeviceLightLibraryReadAuthority.PRESENTATION
    )
    val systemSupported = status.supportsLightSystemPresentation()
    val systemSnapshot = retainedSystemSnapshot(deviceUid, runtime, systemSupported)
    return status.toControlSnapshot(
        deviceUid = deviceUid,
        graph = dashboard.graph,
        customDocument = library?.custom,
        systemSupported = systemSupported,
        systemSnapshot = systemSnapshot
    )
        .takeIf { snapshot -> snapshot.matchesRetainedLightRuntimeSurface(deviceUid, status) }
        ?.let(DeviceLightControlResult::Available)
        ?: DeviceLightControlResult.Failed(DeviceLightControlFailure.INVALID_DATA)
}

private fun retainedSystemSnapshot(
    deviceUid: DeviceUid,
    runtime: DeviceLightRuntimeRepository,
    supported: Boolean
) = if (!supported) {
    null
} else {
    val frame = runtime.currentSystem(
        deviceUid,
        DeviceLightSystemReadAuthority.PRESENTATION
    )
    val authoritative = runtime.currentSystem(
        deviceUid,
        DeviceLightSystemReadAuthority.AUTHORITATIVE
    )
    (
        projectLightSystemPresentationSnapshot(
            deviceUid = deviceUid,
            thermal = frame?.thermal,
            protection = frame?.protection,
            firmwareWriteAuthoritative = frame != null && frame == authoritative
        ) as? DeviceLightSystemReadResult.Available
        )?.snapshot
}

private fun DeviceLightControlSnapshot.matchesRetainedLightRuntimeSurface(
    deviceUid: DeviceUid,
    status: DeviceLightStatus
): Boolean {
    val expectedKeys = status.channels
        .sortedBy { channel -> channel.order }
        .map { channel -> channel.key }
    val identityMatches =
        deviceUid.value == this.deviceUid &&
            productKey == status.product.wireValue
    val topologyMatches =
        physicalChannelCount == status.runtime.physicalChannelCount &&
            channelKeys == expectedKeys &&
            channels.map { channel -> channel.key } == channelKeys
    val planMatches = plan.points.none { point ->
        point.channelLevels.size != channels.size
    }
    return identityMatches && topologyMatches && planMatches
}
