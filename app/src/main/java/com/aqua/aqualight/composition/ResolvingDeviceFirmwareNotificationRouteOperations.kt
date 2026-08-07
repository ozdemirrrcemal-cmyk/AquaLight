package com.aqua.aqualight.composition

import android.util.Log
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationDestinationPolicy
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteDecision
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteOperations
import com.aqua.aqualight.data.devices.model.DeviceUid

/** Resolves notification routes only through the committed authenticated-owner graph. */
internal class ResolvingDeviceFirmwareNotificationRouteOperations(
    private val ownerGraphResolver: OwnerDependencyGraphResolver
) : DeviceFirmwareNotificationRouteOperations {

    override fun evaluate(
        deviceUid: String
    ): DeviceFirmwareNotificationRouteDecision {
        val normalizedDeviceUid = deviceUid.trim()
        if (normalizedDeviceUid.isBlank()) {
            return DeviceFirmwareNotificationRouteDecision.REJECT
        }
        val graph = try {
            ownerGraphResolver.requireActive()
        } catch (error: IllegalStateException) {
            Log.d(TAG, "Firmware notification route is waiting for owner graph.", error)
            return DeviceFirmwareNotificationRouteDecision.DEFER
        }
        val repository = graph.devicesRepository
        val snapshot = repository.currentDevice(DeviceUid(normalizedDeviceUid))
        return DeviceFirmwareNotificationDestinationPolicy.evaluate(
            repositoryReady = repository.ready.value,
            deviceExists = snapshot != null,
            otaSupported = snapshot?.capabilities?.ota == true
        )
    }

    private companion object {
        const val TAG = "FirmwareNotifyRoute"
    }
}
