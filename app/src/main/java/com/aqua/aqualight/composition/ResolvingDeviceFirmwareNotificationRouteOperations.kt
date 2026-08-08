package com.aqua.aqualight.composition

import android.util.Log
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationDestinationPolicy
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteDecision
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteOperations
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteRequest
import com.aqua.aqualight.application.notifications.DeviceFirmwareNotificationKind
import com.aqua.aqualight.data.devices.DeviceFirmwareNotificationActionabilityPolicy
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid

/** Resolves notification routes only through the committed authenticated-owner graph. */
internal class ResolvingDeviceFirmwareNotificationRouteOperations(
    private val ownerGraphResolver: OwnerDependencyGraphResolver
) : DeviceFirmwareNotificationRouteOperations {

    override fun evaluate(
        request: DeviceFirmwareNotificationRouteRequest
    ): DeviceFirmwareNotificationRouteDecision {
        val normalizedDeviceUid = request.deviceUid.trim()
        return if (normalizedDeviceUid.isBlank()) {
            DeviceFirmwareNotificationRouteDecision.REJECT
        } else {
            evaluateActiveGraph(request.copy(deviceUid = normalizedDeviceUid))
        }
    }

    override suspend fun dismissOpenedAvailability(ownerUid: String, deviceUid: String) {
        val normalizedOwnerUid = ownerUid.trim()
        val normalizedDeviceUid = deviceUid.trim()
        val identifiersValid = normalizedOwnerUid.isNotBlank() &&
            normalizedDeviceUid.isNotBlank()
        if (identifiersValid) {
            val graph = activeGraphOrNull()
            if (graph?.ownerUid == normalizedOwnerUid) {
                graph.deviceFirmwareNotifications.dismissAvailability(
                    ownerUid = normalizedOwnerUid,
                    deviceUid = normalizedDeviceUid
                )
            }
        }
    }

    private fun evaluateActiveGraph(
        request: DeviceFirmwareNotificationRouteRequest
    ): DeviceFirmwareNotificationRouteDecision {
        return activeGraphOrNull()?.let { activeGraph ->
            val repository = activeGraph.devicesRepository
            val snapshot = repository.currentDevice(DeviceUid(request.deviceUid))
            DeviceFirmwareNotificationDestinationPolicy.evaluate(
                repositoryReady = repository.ready.value,
                deviceExists = snapshot != null,
                otaSupported = snapshot?.capabilities?.ota == true,
                actionable = snapshot?.let { current ->
                    request.isActionable(activeGraph, current)
                } == true
            )
        } ?: DeviceFirmwareNotificationRouteDecision.DEFER
    }

    private fun DeviceFirmwareNotificationRouteRequest.isActionable(
        activeGraph: OwnerDependencyGraph,
        snapshot: DeviceSnapshot
    ): Boolean = when (kind) {
        DeviceFirmwareNotificationKind.AVAILABILITY ->
            DeviceFirmwareNotificationActionabilityPolicy.availability(
                currentVersion = snapshot.firmwareVersion,
                targetVersion = targetVersion
            )
        DeviceFirmwareNotificationKind.OPERATION ->
            DeviceFirmwareNotificationActionabilityPolicy.operation(
                state = activeGraph.firmwareUpdateOperations.observe(deviceUid).value,
                expectedTargetVersion = targetVersion
            )
    }

    private fun activeGraphOrNull(): OwnerDependencyGraph? {
        return try {
            ownerGraphResolver.requireActive()
        } catch (error: IllegalStateException) {
            Log.d(TAG, "Firmware notification route is waiting for owner graph.", error)
            null
        }
    }

    private companion object {
        const val TAG = "FirmwareNotifyRoute"
    }
}
