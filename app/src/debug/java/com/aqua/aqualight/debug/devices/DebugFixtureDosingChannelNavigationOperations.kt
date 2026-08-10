package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceDosingChannelDestinationPolicy
import com.aqua.aqualight.application.devices.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

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

    override fun observeTargets(
        deviceUid: String
    ): Flow<List<DeviceDosingChannelNavigationTarget>> {
        val fixture = fixtures.rootSnapshot(deviceUid)
        return if (fixture == null) {
            delegate.observeTargets(deviceUid)
        } else {
            observeFixtureTargets(fixture)
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

    private fun observeFixtureTargets(
        fixture: DeviceRootSnapshot
    ): Flow<List<DeviceDosingChannelNavigationTarget>> {
        val channels = fixture.channelSlots.dosingChannels
        if (channels.isEmpty()) return flowOf(emptyList())
        val stateFlows = channels.map { slot ->
            stateStore.observe(fixture.deviceUid, slot.id.value)
        }
        return combine(stateFlows) { states ->
            channels.mapIndexedNotNull { index, slot ->
                resolveFixture(
                    fixture = fixture,
                    slotId = slot.id.value,
                    calibrated = states[index]?.calibrated
                )
            }
        }
    }

    private fun resolveFixture(
        fixture: DeviceRootSnapshot,
        slotId: String,
        calibrated: Boolean? = null
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
                    calibrated = calibrated
                        ?: stateStore.isCalibrated(fixture.deviceUid, slot.id.value),
                    allowedRoutes = fixture.allowedRoutes
                )?.let { destination ->
                    DeviceDosingChannelNavigationTarget(
                        deviceUid = fixture.deviceUid,
                        slotId = slot.id.value,
                        pumpCount = fixture.channelSlots.dosingChannels.size,
                        channelNumber = slot.index.position,
                        channelTitle = stateStore.current(fixture.deviceUid, slot.id.value)
                            ?.channelTitle
                            ?: slot.defaultDisplayName,
                        destination = destination
                    )
                }
            }
    }
}
