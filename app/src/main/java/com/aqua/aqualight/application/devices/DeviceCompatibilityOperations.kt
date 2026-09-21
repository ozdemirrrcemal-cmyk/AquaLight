package com.aqua.aqualight.application.devices

/**
 * Owner-scoped application boundary for commercial device compatibility.
 *
 * Presence/liveness is intentionally not represented here. A device can remain physically
 * reachable while its base contract or an optional feature is unavailable to this app build.
 */
interface DeviceCompatibilityOperations {
    fun current(deviceUid: String): DeviceCompatibilitySnapshot
}

/**
 * Central on-demand feature gate.
 *
 * Implementations may refresh signed OTA availability when a requested optional feature is absent,
 * then re-evaluate through [DeviceAccessPolicy]. UI and family modules must not implement their own
 * firmware/version checks.
 */
interface DeviceFeatureAccessOperations {
    suspend fun resolve(
        deviceUid: String,
        feature: DeviceRootMenuFeature
    ): DeviceAccessDecision
}

data class DeviceCompatibilitySnapshot(
    val deviceUid: String,
    val family: OwnerDeviceFamily = OwnerDeviceFamily.UNKNOWN,
    val status: DeviceCompatibilityStatus,
    val menuFeatures: Set<DeviceRootMenuFeature> = emptySet(),
    val firmwareUpdateRequiredFeatures: Set<DeviceRootMenuFeature> = emptySet(),
    val allowedRoutes: Set<DeviceRootRoute> = emptySet()
)

enum class DeviceCompatibilityStatus {
    COMPATIBLE,
    DEVICE_NOT_REGISTERED,
    RUNTIME_METADATA_UNAVAILABLE,
    COMMERCIAL_PRODUCT_MISMATCH,
    BASE_CONTRACT_INCOMPATIBLE,
    APPLICATION_UPDATE_REQUIRED
}

/**
 * One commercial access policy shared by Devices, Tank Devices, provisioning handoff and
 * destination revalidation. Feature screens must not invent their own compatibility policy.
 */
interface DeviceAccessPolicy {
    fun evaluateRoot(compatibility: DeviceCompatibilitySnapshot): DeviceAccessDecision

    fun evaluateFeature(
        compatibility: DeviceCompatibilitySnapshot,
        feature: DeviceRootMenuFeature
    ): DeviceAccessDecision
}

sealed interface DeviceAccessDecision {
    data object Allowed : DeviceAccessDecision

    data class Blocked(
        val reason: DeviceMenuUnavailableReason
    ) : DeviceAccessDecision
}

object DefaultDeviceAccessPolicy : DeviceAccessPolicy {

    override fun evaluateRoot(
        compatibility: DeviceCompatibilitySnapshot
    ): DeviceAccessDecision = compatibility.status.toAccessDecision()

    override fun evaluateFeature(
        compatibility: DeviceCompatibilitySnapshot,
        feature: DeviceRootMenuFeature
    ): DeviceAccessDecision {
        val rootDecision = evaluateRoot(compatibility)
        if (rootDecision !is DeviceAccessDecision.Allowed) return rootDecision
        return when {
            feature in compatibility.menuFeatures -> DeviceAccessDecision.Allowed
            feature in compatibility.firmwareUpdateRequiredFeatures ->
                DeviceAccessDecision.Blocked(DeviceMenuUnavailableReason.FIRMWARE_UPDATE_REQUIRED)
            else -> DeviceAccessDecision.Blocked(DeviceMenuUnavailableReason.FEATURE_UNAVAILABLE)
        }
    }

    private fun DeviceCompatibilityStatus.toAccessDecision(): DeviceAccessDecision = when (this) {
        DeviceCompatibilityStatus.COMPATIBLE -> DeviceAccessDecision.Allowed
        DeviceCompatibilityStatus.DEVICE_NOT_REGISTERED -> DeviceAccessDecision.Blocked(
            DeviceMenuUnavailableReason.DEVICE_NOT_REGISTERED
        )
        DeviceCompatibilityStatus.RUNTIME_METADATA_UNAVAILABLE -> DeviceAccessDecision.Blocked(
            DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
        )
        DeviceCompatibilityStatus.COMMERCIAL_PRODUCT_MISMATCH -> DeviceAccessDecision.Blocked(
            DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH
        )
        DeviceCompatibilityStatus.BASE_CONTRACT_INCOMPATIBLE -> DeviceAccessDecision.Blocked(
            DeviceMenuUnavailableReason.CONTRACT_INCOMPATIBLE
        )
        DeviceCompatibilityStatus.APPLICATION_UPDATE_REQUIRED -> DeviceAccessDecision.Blocked(
            DeviceMenuUnavailableReason.APPLICATION_UPDATE_REQUIRED
        )
    }
}
