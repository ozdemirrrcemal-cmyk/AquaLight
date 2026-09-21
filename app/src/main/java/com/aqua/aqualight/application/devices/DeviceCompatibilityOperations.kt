package com.aqua.aqualight.application.devices

/**
 * Owner-scoped commercial compatibility boundary.
 *
 * Presence/liveness is deliberately not part of this model. A device may remain reachable while
 * one base contract or optional feature is unavailable to the current Android build.
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
    BASE_CONTRACT_INCOMPATIBLE,
    APPLICATION_UPDATE_REQUIRED
}

/** One family-neutral policy used by root menus and optional feature entry points. */
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
    ): DeviceAccessDecision = when (compatibility.status) {
        DeviceCompatibilityStatus.COMPATIBLE -> DeviceAccessDecision.Allowed
        DeviceCompatibilityStatus.DEVICE_NOT_REGISTERED -> blocked(
            DeviceMenuUnavailableReason.DEVICE_NOT_REGISTERED
        )
        DeviceCompatibilityStatus.RUNTIME_METADATA_UNAVAILABLE -> blocked(
            DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
        )
        DeviceCompatibilityStatus.COMMERCIAL_PRODUCT_MISMATCH -> blocked(
            DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH
        )
        DeviceCompatibilityStatus.BASE_CONTRACT_INCOMPATIBLE -> blocked(
            DeviceMenuUnavailableReason.CONTRACT_INCOMPATIBLE
        )
        DeviceCompatibilityStatus.APPLICATION_UPDATE_REQUIRED -> blocked(
            DeviceMenuUnavailableReason.APPLICATION_UPDATE_REQUIRED
        )
    }

    override fun evaluateFeature(
        compatibility: DeviceCompatibilitySnapshot,
        feature: DeviceRootMenuFeature
    ): DeviceAccessDecision {
        val root = evaluateRoot(compatibility)
        return if (root is DeviceAccessDecision.Allowed && feature !in compatibility.menuFeatures) {
            blocked(DeviceMenuUnavailableReason.FEATURE_UNAVAILABLE)
        } else {
            root
        }
    }

    private fun blocked(reason: DeviceMenuUnavailableReason) =
        DeviceAccessDecision.Blocked(reason)
}
