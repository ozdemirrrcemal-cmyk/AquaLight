package com.aqua.aqualight.data.devices.compatibility

import com.aqua.aqualight.application.devices.DeviceCompatibilityOperations
import com.aqua.aqualight.application.devices.DeviceCompatibilitySnapshot
import com.aqua.aqualight.application.devices.DeviceCompatibilityStatus
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.toOwnerDeviceFamily

internal class DefaultDeviceCompatibilityOperations(
    private val devicesRepository: DevicesRepository
) : DeviceCompatibilityOperations {

    override fun current(deviceUid: String): DeviceCompatibilitySnapshot {
        val normalized = deviceUid.trim()
        val snapshot = normalized
            .takeIf(String::isNotBlank)
            ?.let { value -> devicesRepository.currentDevice(DeviceUid(value)) }

        return when {
            normalized.isBlank() || snapshot == null -> DeviceCompatibilitySnapshot(
                deviceUid = normalized,
                status = DeviceCompatibilityStatus.DEVICE_NOT_REGISTERED
            )
            else -> when (val evaluation = DeviceCommercialCompatibilityEvaluator.evaluate(snapshot)) {
                is DeviceCommercialCompatibilityEvaluation.Compatible ->
                    DeviceCompatibilitySnapshot(
                        deviceUid = normalized,
                        family = evaluation.product.family.toOwnerDeviceFamily(),
                        status = DeviceCompatibilityStatus.COMPATIBLE,
                        menuFeatures = evaluation.menuFeatures,
                        allowedRoutes = evaluation.allowedRoutes
                    )
                is DeviceCommercialCompatibilityEvaluation.Incompatible ->
                    DeviceCompatibilitySnapshot(
                        deviceUid = normalized,
                        family = snapshot.product.family.toOwnerDeviceFamily(),
                        status = evaluation.issue.toApplicationStatus()
                    )
            }
        }
    }
}

private fun DeviceCommercialCompatibilityIssue.toApplicationStatus(): DeviceCompatibilityStatus =
    when (this) {
        DeviceCommercialCompatibilityIssue.RUNTIME_METADATA_UNAVAILABLE ->
            DeviceCompatibilityStatus.RUNTIME_METADATA_UNAVAILABLE
        DeviceCommercialCompatibilityIssue.COMMERCIAL_PRODUCT_MISMATCH ->
            DeviceCompatibilityStatus.COMMERCIAL_PRODUCT_MISMATCH
        DeviceCommercialCompatibilityIssue.BASE_CONTRACT_INCOMPATIBLE ->
            DeviceCompatibilityStatus.BASE_CONTRACT_INCOMPATIBLE
        DeviceCommercialCompatibilityIssue.APPLICATION_UPDATE_REQUIRED ->
            DeviceCompatibilityStatus.APPLICATION_UPDATE_REQUIRED
    }
