package com.aqua.aqualight.composition

import android.util.Log
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationDestinationPolicy
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteDecision
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteOperations
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteRequest
import com.aqua.aqualight.application.devices.DeviceOtaFailureStage
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.notifications.DeviceFirmwareNotificationKind
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareVersionComparator

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
            isAvailabilityActionable(snapshot, targetVersion)
        DeviceFirmwareNotificationKind.OPERATION -> {
            val state = activeGraph.firmwareUpdateOperations
                .observe(deviceUid)
                .value
            isOperationActionable(state, targetVersion)
        }
    }

    private fun isAvailabilityActionable(
        snapshot: DeviceSnapshot,
        targetVersion: String
    ): Boolean {
        val currentVersion = snapshot.firmwareVersion.trim()
        val target = targetVersion.trim()
        if (currentVersion.isBlank() || target.isBlank()) return false
        return runCatching {
            DeviceFirmwareVersionComparator.compare(target, currentVersion) > 0
        }.getOrDefault(false)
    }

    private fun isOperationActionable(
        state: DeviceOtaState,
        expectedTargetVersion: String
    ): Boolean = when (state) {
        is DeviceOtaState.Starting -> targetMatches(
            expectedTargetVersion,
            state.plan.targetVersion
        )
        is DeviceOtaState.InProgress -> targetMatches(
            expectedTargetVersion,
            state.targetVersion
        )
        is DeviceOtaState.Recovering -> targetMatches(
            expectedTargetVersion,
            state.targetVersion
        )
        is DeviceOtaState.RestartRequired -> targetMatches(
            expectedTargetVersion,
            state.targetVersion
        )
        is DeviceOtaState.Succeeded -> targetMatches(
            expectedTargetVersion,
            state.targetVersion
        )
        is DeviceOtaState.Failed ->
            state.failure.stage == DeviceOtaFailureStage.UPDATE_EXECUTION
        is DeviceOtaState.Idle,
        is DeviceOtaState.Checking,
        is DeviceOtaState.Unsupported,
        is DeviceOtaState.UpToDate,
        is DeviceOtaState.UpdateAvailable -> false
    }

    private fun targetMatches(expected: String, actual: String): Boolean {
        val normalizedExpected = expected.trim()
        return normalizedExpected.isBlank() || normalizedExpected == actual.trim()
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
