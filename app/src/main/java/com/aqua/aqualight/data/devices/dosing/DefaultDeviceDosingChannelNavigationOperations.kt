package com.aqua.aqualight.data.devices.dosing

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDestinationPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.awaitAuthoritative
import kotlinx.coroutines.flow.first

/** Resolves Dosing navigation from the same authoritative channel boundary used by the screens. */
internal class DefaultDeviceDosingChannelNavigationOperations(
    private val rootOperations: DeviceRootOperations,
    private val channelOperations: DeviceDosingChannelOperations
) : DeviceDosingChannelNavigationOperations {

    override suspend fun resolve(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? = navigationAddress(deviceUid, slotId)?.let { address ->
        channelOperations.observe(address.deviceUid, address.slotId).first()?.let { snapshot ->
            resolveSnapshot(address, snapshot)
        }
    }

    override suspend fun resolveCurrent(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? = navigationAddress(deviceUid, slotId)?.let { address ->
        val authoritative = channelOperations.awaitAuthoritative(
            address.deviceUid,
            address.slotId
        )
        (authoritative as? DeviceDosingChannelOperationResult.Success)?.snapshot?.let { snapshot ->
            resolveSnapshot(address, snapshot)
        }
    }

    private fun resolveSnapshot(
        address: DosingNavigationAddress,
        channel: DeviceDosingChannelSnapshot
    ): DeviceDosingChannelNavigationTarget? = rootOperations.current(address.deviceUid)
        ?.authorizedDosingRoot(address.deviceUid)
        ?.let { root ->
            val catalogChannels = root.channelSlots.dosingChannels
            val pumpCount = catalogChannels.size.takeIf(::isSupportedDosingPumpCount)
            pumpCount?.let { supportedCount ->
                catalogChannels.singleOrNull { candidate -> candidate.id.value == address.slotId }
                    ?.takeIf { slot ->
                        channel.matchesAddress(address) &&
                            channel.matchesCatalogSlot(
                                slotId = slot.id.value,
                                pumpCount = supportedCount,
                                channelNumber = slot.index.position
                            )
                    }
                    ?.let {
                        DeviceDosingChannelDestinationPolicy.resolve(
                            calibrated = channel.calibrated,
                            allowedRoutes = root.allowedRoutes
                        )
                    }
                    ?.let { destination -> channel.toNavigationTarget(destination) }
            }
        }

    private fun navigationAddress(deviceUid: String, slotId: String): DosingNavigationAddress? =
        DosingNavigationAddress(deviceUid.trim(), slotId.trim()).takeIf { address ->
            address.deviceUid.isNotEmpty() && address.slotId.isNotEmpty()
        }
}

private data class DosingNavigationAddress(
    val deviceUid: String,
    val slotId: String
)

private fun DeviceDosingChannelSnapshot.toNavigationTarget(
    destination: com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDestination
): DeviceDosingChannelNavigationTarget = DeviceDosingChannelNavigationTarget(
    deviceUid = deviceUid,
    slotId = slotId,
    pumpCount = pumpCount,
    channelNumber = channelNumber,
    lastCalibratedAtEpochSeconds = lastCalibratedAtEpochSeconds,
    destination = destination
)

private fun DeviceDosingChannelSnapshot.matchesAddress(address: DosingNavigationAddress): Boolean =
    deviceUid == address.deviceUid && slotId == address.slotId

private fun DeviceDosingChannelSnapshot.matchesCatalogSlot(
    slotId: String,
    pumpCount: Int,
    channelNumber: Int
): Boolean = this.slotId == slotId &&
    this.pumpCount == pumpCount &&
    this.channelNumber == channelNumber

private fun DeviceRootSnapshot.authorizedDosingRoot(deviceUid: String): DeviceRootSnapshot? =
    takeIf { root ->
        root.deviceUid == deviceUid &&
            root.catalogState == DeviceRootCatalogState.VALID &&
            root.family == OwnerDeviceFamily.DOSING
    }

private fun isSupportedDosingPumpCount(count: Int): Boolean =
    count == TWO_PUMP_DOSING_COUNT || count == FOUR_PUMP_DOSING_COUNT

private const val TWO_PUMP_DOSING_COUNT = 2
private const val FOUR_PUMP_DOSING_COUNT = 4
