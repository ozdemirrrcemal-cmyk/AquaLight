package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeleteOwnerDevicesResult
import com.aqua.aqualight.application.devices.OwnerDeviceListItem
import com.aqua.aqualight.application.devices.OwnerDevicesOperations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Adds fixture cards without changing or persisting the real owner device registry. */
internal class DebugFixtureOwnerDevicesOperations(
    private val delegate: OwnerDevicesOperations,
    private val fixtures: DebugDeviceFixtureCatalog
) : OwnerDevicesOperations {

    override val devices: Flow<List<OwnerDeviceListItem>> = delegate.devices.map { realDevices ->
        fixtures.listItems() + realDevices.filterNot { item -> fixtures.contains(item.deviceUid) }
    }

    override fun start(scope: CoroutineScope): Job = delegate.start(scope)

    override fun refreshVisibleDevices() = delegate.refreshVisibleDevices()

    override suspend fun deleteDevices(deviceUids: Set<String>): DeleteOwnerDevicesResult {
        val fixtureUids = deviceUids.filterTo(linkedSetOf(), fixtures::contains)
        val realUids = deviceUids - fixtureUids
        return if (realUids.isEmpty()) {
            DeleteOwnerDevicesResult(
                succeededDeviceUids = emptySet(),
                failedDeviceUids = fixtureUids
            )
        } else {
            val realResult = delegate.deleteDevices(realUids)
            realResult.copy(
                failedDeviceUids = realResult.failedDeviceUids + fixtureUids
            )
        }
    }
}
