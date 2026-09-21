package com.aqua.aqualight.data.devices.compatibility

import com.aqua.aqualight.application.devices.DEVICE_FIRMWARE_MANIFEST_URL
import com.aqua.aqualight.application.devices.DeviceAccessDecision
import com.aqua.aqualight.application.devices.DeviceAccessPolicy
import com.aqua.aqualight.application.devices.DeviceCompatibilityOperations
import com.aqua.aqualight.application.devices.DeviceFeatureAccessOperations
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.DeviceRootMenuFeature

/**
 * One owner-scoped feature gate for every commercial family.
 *
 * A missing optional feature first remains a feature-availability problem. The gate then refreshes
 * signed OTA availability through the shared OTA application boundary and re-evaluates the same
 * central compatibility snapshot. No family UI performs version comparisons or manifest parsing.
 */
internal class DefaultDeviceFeatureAccessOperations(
    private val compatibilityOperations: DeviceCompatibilityOperations,
    private val accessPolicy: DeviceAccessPolicy,
    private val firmwareUpdateOperations: DeviceFirmwareUpdateOperations
) : DeviceFeatureAccessOperations {

    override suspend fun resolve(
        deviceUid: String,
        feature: DeviceRootMenuFeature
    ): DeviceAccessDecision {
        val initial = accessPolicy.evaluateFeature(
            compatibility = compatibilityOperations.current(deviceUid),
            feature = feature
        )
        if (
            initial !is DeviceAccessDecision.Blocked ||
            initial.reason != DeviceMenuUnavailableReason.FEATURE_UNAVAILABLE
        ) {
            return initial
        }

        val refresh = firmwareUpdateOperations.refreshAvailabilityIfStale(
            deviceUid = deviceUid,
            manifestUrl = DEVICE_FIRMWARE_MANIFEST_URL,
            applyNow = true
        )
        val otaState = refresh.getOrNull() ?: firmwareUpdateOperations.observe(deviceUid).value
        if (
            otaState is DeviceOtaState.Failed &&
            otaState.failure.reason == DeviceOtaFailureReason.APPLICATION_UPDATE_REQUIRED
        ) {
            return DeviceAccessDecision.Blocked(
                DeviceMenuUnavailableReason.APPLICATION_UPDATE_REQUIRED
            )
        }

        return accessPolicy.evaluateFeature(
            compatibility = compatibilityOperations.current(deviceUid),
            feature = feature
        )
    }
}
