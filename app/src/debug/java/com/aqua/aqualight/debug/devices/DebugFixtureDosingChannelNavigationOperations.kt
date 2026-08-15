package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDestinationPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily

/** Resolves known fixture channels without weakening the production runtime-backed boundary. */
internal class DebugFixtureDosingChannelNavigationOperations(
    private val delegate: DeviceDosingChannelNavigationOperations,
    private val fixtures: DebugDeviceFixtureCatalog,
    private val stateStore: DebugFixtureDosingStateStore
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

    override suspend fun resolveCurrent(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? = fixtures.rootSnapshot(deviceUid)
        ?.let { fixture -> resolveFixture(fixture, slotId) }
        ?: delegate.resolveCurrent(deviceUid, slotId)

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
                    calibrated = stateStore.isCalibrated(fixture.deviceUid, slot.id.value),
                    allowedRoutes = fixture.allowedRoutes
                )?.let { destination ->
                    DeviceDosingChannelNavigationTarget(
                        deviceUid = fixture.deviceUid,
                        slotId = slot.id.value,
                        pumpCount = fixture.channelSlots.dosingChannels.size,
                        channelNumber = slot.index.position,
                        lastCalibratedAtEpochSeconds = stateStore
                            .current(fixture.deviceUid, slot.id.value)
                            ?.lastCalibratedAt
                            ?: 0L,
                        destination = destination
                    )
                }
            }
    }
}
