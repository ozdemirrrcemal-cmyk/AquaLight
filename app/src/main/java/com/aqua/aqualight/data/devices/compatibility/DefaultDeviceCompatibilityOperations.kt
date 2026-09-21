package com.aqua.aqualight.data.devices.compatibility

import com.aqua.aqualight.application.devices.DeviceCompatibilityOperations
import com.aqua.aqualight.application.devices.DeviceCompatibilitySnapshot
import com.aqua.aqualight.application.devices.DeviceCompatibilityStatus
import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.toOwnerDeviceFamily

/** Stateless owner-scoped adapter over the single DevicesRepository authority. */
internal class DefaultDeviceCompatibilityOperations(
    private val devicesRepository: DevicesRepository,
    private val updatePlanProvider: (String) -> PreparedDeviceFirmwareUpdate? = { null }
) : DeviceCompatibilityOperations {

    override fun current(deviceUid: String): DeviceCompatibilitySnapshot {
        val normalized = deviceUid.trim()
        if (normalized.isBlank()) {
            return incompatible(
                deviceUid = normalized,
                status = DeviceCompatibilityStatus.DEVICE_NOT_REGISTERED
            )
        }

        val snapshot = devicesRepository.currentDevice(DeviceUid(normalized))
            ?: return incompatible(
                deviceUid = normalized,
                status = DeviceCompatibilityStatus.DEVICE_NOT_REGISTERED
            )

        return when (val evaluation = DeviceCommercialCompatibilityEvaluator.evaluate(snapshot)) {
            is DeviceCommercialCompatibilityEvaluation.Compatible -> {
                val family = evaluation.product.family.toOwnerDeviceFamily()
                val targetFeatures = updatePlanProvider(normalized)?.targetFeatures.orEmpty()
                val updateFeatures = DeviceMenuUpdatePolicyProjector.resolve(
                    family = family,
                    targetFeatureTokens = targetFeatures
                ) - evaluation.menuFeatures
                DeviceCompatibilitySnapshot(
                    deviceUid = normalized,
                    family = family,
                    status = DeviceCompatibilityStatus.COMPATIBLE,
                    menuFeatures = evaluation.menuFeatures,
                    firmwareUpdateRequiredFeatures = updateFeatures,
                    allowedRoutes = evaluation.allowedRoutes
                )
            }
            is DeviceCommercialCompatibilityEvaluation.Incompatible -> incompatible(
                deviceUid = normalized,
                family = snapshot.product.family.toOwnerDeviceFamily(),
                status = evaluation.issue.toApplicationStatus()
            )
        }
    }

    private fun incompatible(
        deviceUid: String,
        status: DeviceCompatibilityStatus,
        family: OwnerDeviceFamily = OwnerDeviceFamily.UNKNOWN
    ) = DeviceCompatibilitySnapshot(
        deviceUid = deviceUid,
        family = family,
        status = status
    )
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


/** Maps signed release feature tokens onto existing application menu features without UI coupling. */
private object DeviceMenuUpdatePolicyProjector {
    fun resolve(
        family: OwnerDeviceFamily,
        targetFeatureTokens: Set<String>
    ): Set<DeviceRootMenuFeature> = targetFeatureTokens
        .mapNotNull(AqlDeviceFeatureKey::fromWireExact)
        .mapNotNullTo(linkedSetOf()) { feature -> feature.toMenuFeature(family) }

    private fun AqlDeviceFeatureKey.toMenuFeature(
        family: OwnerDeviceFamily
    ): DeviceRootMenuFeature? = when (this) {
        AqlDeviceFeatureKey.LIGHT_QUICK_SETUP ->
            DeviceRootMenuFeature.LIGHT_QUICK_SETUP.takeIf { family == OwnerDeviceFamily.LIGHT }
        AqlDeviceFeatureKey.LIGHT_CONTROL ->
            DeviceRootMenuFeature.LIGHT_MANUAL.takeIf { family == OwnerDeviceFamily.LIGHT }
        AqlDeviceFeatureKey.LIGHT_PRESETS ->
            DeviceRootMenuFeature.LIGHT_PRESETS.takeIf { family == OwnerDeviceFamily.LIGHT }
        AqlDeviceFeatureKey.DOSING_CALIBRATION ->
            DeviceRootMenuFeature.DOSING_CALIBRATION.takeIf { family == OwnerDeviceFamily.DOSING }
        AqlDeviceFeatureKey.DOSING_CONTROL ->
            DeviceRootMenuFeature.DOSING_CHANNELS.takeIf { family == OwnerDeviceFamily.DOSING }
        AqlDeviceFeatureKey.TIMER_CONTROL ->
            DeviceRootMenuFeature.TIMER_CHANNELS.takeIf { family == OwnerDeviceFamily.TIMER }
        AqlDeviceFeatureKey.COOLING_CONTROL ->
            DeviceRootMenuFeature.COOLING_FANS.takeIf { family == OwnerDeviceFamily.COOLING }
        AqlDeviceFeatureKey.LIGHT_FAN_CONTROL ->
            DeviceRootMenuFeature.COOLING_FANS.takeIf { family == OwnerDeviceFamily.LIGHT }
        AqlDeviceFeatureKey.LIGHT_TEMPERATURE_PROTECTION,
        AqlDeviceFeatureKey.TEMPERATURE_READ ->
            DeviceRootMenuFeature.COOLING_TEMPERATURE
        else -> null
    }
}
