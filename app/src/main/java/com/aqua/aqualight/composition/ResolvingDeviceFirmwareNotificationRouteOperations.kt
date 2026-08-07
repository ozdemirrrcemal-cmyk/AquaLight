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
        return if (normalizedDeviceUid.isBlank()) {
            DeviceFirmwareNotificationRouteDecision.REJECT
        } else {
            evaluateActiveGraph(normalizedDeviceUid)
        }
    }

    override suspend fun dismissOpenedAvailability(ownerUid: String, deviceUid: String) {
        val normalizedOwnerUid = ownerUid.trim()
        val normalizedDeviceUid = deviceUid.trim()
        if (normalizedOwnerUid.isBlank() || normalizedDeviceUid.isBlank()) return

        val graph = activeGraphOrNull() ?: return
        if (graph.ownerUid != normalizedOwnerUid) return

        graph.deviceFirmwareNotifications.dismissAvailability(
            ownerUid = normalizedOwnerUid,
            deviceUid = normalizedDeviceUid
        )
    }

    private fun evaluateActiveGraph(
        deviceUid: String
    ): DeviceFirmwareNotificationRouteDecision {
        return activeGraphOrNull()?.let { activeGraph ->
            val repository = activeGraph.devicesRepository
            val snapshot = repository.currentDevice(DeviceUid(deviceUid))
            DeviceFirmwareNotificationDestinationPolicy.evaluate(
                repositoryReady = repository.ready.value,
                deviceExists = snapshot != null,
                otaSupported = snapshot?.capabilities?.ota == true
            )
        } ?: DeviceFirmwareNotificationRouteDecision.DEFER
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
