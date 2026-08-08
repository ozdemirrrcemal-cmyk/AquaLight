package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Uses real root operations for real UIDs and catalog-derived snapshots for fixture UIDs. */
internal class DebugFixtureDeviceRootOperations(
    private val delegate: DeviceRootOperations,
    private val fixtures: DebugDeviceFixtureCatalog
) : DeviceRootOperations {

    override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> {
        val fixture = fixtures.rootSnapshot(deviceUid)
        return if (fixture != null) flowOf(fixture) else delegate.observe(deviceUid)
    }

    override fun current(deviceUid: String): DeviceRootSnapshot? =
        fixtures.rootSnapshot(deviceUid) ?: delegate.current(deviceUid)

    override fun connect(deviceUid: String): Result<Unit> =
        if (fixtures.contains(deviceUid)) Result.success(Unit) else delegate.connect(deviceUid)
}
