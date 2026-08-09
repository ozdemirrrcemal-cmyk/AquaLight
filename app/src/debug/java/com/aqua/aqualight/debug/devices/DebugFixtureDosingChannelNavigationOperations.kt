package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceDosingChannelDestinationPolicy
import com.aqua.aqualight.application.devices.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily

/** Resolves known fixture channels without weakening the production runtime-backed boundary. */
internal class DebugFixtureDosingChannelNavigationOperations(
    private val delegate: DeviceDosingChannelNavigationOperations,
    private val fixtures: DebugDeviceFixtureCatalog
) : DeviceDosingChannelNavigationOperations {

    override suspend fun resolve(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? {
        val fixture = fixtures.rootSnapshot(deviceUid)
        return if (fixture == null) {
            delegate.resolve(deviceUid, slotId)
        } else {
            resolveFixture(fixture, slotId)
        }
    }

    private fun resolveFixture(
        fixture: DeviceRootSnapshot,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? {
        val normalizedSlotId = slotId.trim()
        return fixture
            .takeIf { root ->
                root.catalogState == DeviceRootCatalogState.VALID &&
                    root.family == OwnerDeviceFamily.DOSING
            }
            ?.channelSlots
            ?.dosingChannels
            ?.singleOrNull { channel -> channel.id.value == normalizedSlotId }
            ?.let { slot ->
                DeviceDosingChannelDestinationPolicy.resolve(
                    calibrated = false,
                    allowedRoutes = fixture.allowedRoutes
                )?.let { destination ->
                    DeviceDosingChannelNavigationTarget(
                        deviceUid = fixture.deviceUid,
                        slotId = slot.id.value,
                        channelTitle = slot.defaultDisplayName,
                        destination = destination
                    )
                }
            }
    }
}
