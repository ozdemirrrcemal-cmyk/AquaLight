package com.aqua.aqualight.application.devices

/**
 * Owner-scoped application use-case for every user-initiated device menu open.
 *
 * The use-case is the single commercial gate before UI route mapping: it proves current menu
 * access first, then prepares the family control surface. UI entry points must not bypass this
 * boundary when opening an already-registered device menu.
 */
class DeviceMenuOpenUseCase(
    private val menuAccessOperations: DeviceMenuAccessOperations,
    private val controlSurfacePreparationOperations: DeviceControlSurfacePreparationOperations
) {

    suspend fun resolve(deviceUid: String): DeviceMenuOpenResult =
        when (val access = menuAccessOperations.resolve(deviceUid)) {
            is DeviceMenuAccessResult.Available -> prepare(access)
            is DeviceMenuAccessResult.Unavailable -> DeviceMenuOpenResult.Unavailable(
                title = access.title,
                reason = access.reason
            )
        }

    /**
     * Invalidates a successful preparation when its navigation attempt is abandoned.
     * The operation is idempotent and must be called for every uncommitted Ready result.
     */
    fun abandon(ready: DeviceMenuOpenResult.Ready) {
        controlSurfacePreparationOperations.discardFreshPreparation(
            deviceUid = ready.access.deviceUid,
            family = ready.access.family
        )
    }

    private suspend fun prepare(
        access: DeviceMenuAccessResult.Available
    ): DeviceMenuOpenResult = when (
        val preparation = controlSurfacePreparationOperations.prepare(
            DeviceControlSurfacePreparationRequest(
                deviceUid = access.deviceUid,
                family = access.family
            )
        )
    ) {
        DeviceControlSurfacePreparationResult.Ready -> DeviceMenuOpenResult.Ready(access)
        is DeviceControlSurfacePreparationResult.Unavailable -> DeviceMenuOpenResult.Unavailable(
            title = access.title,
            reason = preparation.reason
        )
    }
}

sealed interface DeviceMenuOpenResult {
    data class Ready(
        val access: DeviceMenuAccessResult.Available
    ) : DeviceMenuOpenResult

    data class Unavailable(
        val title: String,
        val reason: DeviceMenuUnavailableReason
    ) : DeviceMenuOpenResult
}
