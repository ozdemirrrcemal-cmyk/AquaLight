package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuPresentationPreparationOperations
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.validatedDosingChannelSetOrNull
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pre-navigation current-state barrier for commercial device control surfaces.
 *
 * Families without a detailed runtime projection pass through today; adding their preparation here
 * keeps the Devices UI independent from protocol modules. Dosing is fail-closed until one complete
 * current-connection channel set matches the already validated commercial catalog topology.
 */
internal class DefaultDeviceMenuPresentationPreparationOperations(
    private val rootOperations: DeviceRootOperations,
    private val dosingOperations: DeviceDosingChannelOperations
) : DeviceMenuPresentationPreparationOperations {

    override suspend fun prepare(access: DeviceMenuAccessResult.Available): Boolean =
        withTimeoutOrNull(PRESENTATION_PREPARATION_BUDGET_MS) {
            when (access.family) {
                OwnerDeviceFamily.DOSING -> prepareDosing(access.deviceUid)
                OwnerDeviceFamily.LIGHT,
                OwnerDeviceFamily.TIMER,
                OwnerDeviceFamily.COOLING,
                OwnerDeviceFamily.UNKNOWN -> true
            }
        } ?: false

    private suspend fun prepareDosing(deviceUid: String): Boolean {
        val root = rootOperations.current(deviceUid) ?: return false
        if (
            root.catalogState != DeviceRootCatalogState.VALID ||
            root.family != OwnerDeviceFamily.DOSING
        ) {
            return false
        }

        val catalogChannels = root.channelSlots.dosingChannels
        if (catalogChannels.isEmpty() || !dosingOperations.refreshAll(deviceUid)) return false

        return validatedDosingChannelSetOrNull(
            deviceUid = deviceUid,
            catalogChannels = catalogChannels,
            snapshots = dosingOperations.currentAll(deviceUid)
        ) != null
    }

    private companion object {
        const val PRESENTATION_PREPARATION_BUDGET_MS = 6_000L
    }
}
