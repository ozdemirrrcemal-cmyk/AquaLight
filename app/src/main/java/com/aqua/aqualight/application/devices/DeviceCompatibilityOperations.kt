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

data class DeviceCompatibilitySnapshot(
    val deviceUid: String,
    val family: OwnerDeviceFamily = OwnerDeviceFamily.UNKNOWN,
    val status: DeviceCompatibilityStatus,
    val menuFeatures: Set<DeviceRootMenuFeature> = emptySet(),
    val allowedRoutes: Set<DeviceRootRoute> = emptySet()
)

enum class DeviceCompatibilityStatus {
    COMPATIBLE,
    DEVICE_NOT_REGISTERED,
    RUNTIME_METADATA_UNAVAILABLE,
    COMMERCIAL_PRODUCT_MISMATCH,
    BASE_CONTRACT_INCOMPATIBLE
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
        return if (feature in compatibility.menuFeatures) {
            DeviceAccessDecision.Allowed
        } else {
            DeviceAccessDecision.Blocked(DeviceMenuUnavailableReason.FEATURE_UNAVAILABLE)
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
    }
}
