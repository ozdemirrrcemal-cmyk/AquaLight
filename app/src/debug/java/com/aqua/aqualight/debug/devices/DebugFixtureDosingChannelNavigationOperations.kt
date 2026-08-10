package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceDosingChannelDestinationPolicy
import com.aqua.aqualight.application.devices.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.application.devices.DeviceDosingDiagnosticSnapshot
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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

    override fun observeTargets(
        deviceUid: String
    ): Flow<List<DeviceDosingChannelNavigationTarget>> {
        val fixture = fixtures.rootSnapshot(deviceUid)
        return if (fixture == null) {
            delegate.observeTargets(deviceUid)
        } else {
            flowOf(fixture.dosingTargets())
        }
    }

    override suspend fun refreshTargets(deviceUid: String): Boolean =
        fixtures.rootSnapshot(deviceUid) != null || delegate.refreshTargets(deviceUid)

    override suspend fun resolveCurrent(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? = fixtures.rootSnapshot(deviceUid)
        ?.let { fixture -> resolveFixture(fixture, slotId) }
        ?: delegate.resolveCurrent(deviceUid, slotId)

    override fun observeDiagnostics(deviceUid: String): Flow<DeviceDosingDiagnosticSnapshot> =
        delegate.observeDiagnostics(deviceUid)

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
                        pumpCount = fixture.channelSlots.dosingChannels.size,
                        channelNumber = slot.index.position,
                        channelTitle = slot.defaultDisplayName,
                        destination = destination
                    )
                }
            }
    }

    private fun DeviceRootSnapshot.dosingTargets(): List<DeviceDosingChannelNavigationTarget> =
        channelSlots.dosingChannels.mapNotNull { slot -> resolveFixture(this, slot.id.value) }
}
