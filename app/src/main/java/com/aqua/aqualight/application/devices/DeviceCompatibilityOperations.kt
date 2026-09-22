package com.aqua.aqualight.application.devices

/**
 * Commercial compatibility boundary independent from transport presence.
 *
 * A reachable device may be incompatible with the current application without being offline.
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

interface DeviceAccessPolicy {
    fun evaluateRoot(compatibility: DeviceCompatibilitySnapshot): DeviceAccessDecision
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
            DeviceMenuUnavailableReason.MALFORMED_DEVICE_STATE
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

    private fun blocked(reason: DeviceMenuUnavailableReason) =
        DeviceAccessDecision.Blocked(reason)
}
