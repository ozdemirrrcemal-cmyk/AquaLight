package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceFamilySettingsOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.data.devices.DefaultDeviceFamilySettingsOperations
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import kotlinx.coroutines.flow.Flow

/** Keeps the shared Settings screen usable for fixtures without sending runtime commands. */
internal class DebugFixtureFamilySettingsOperations(
    repository: DevicesRepository,
    private val fixtures: DebugDeviceFixtureCatalog
) : DeviceFamilySettingsOperations {

    private val real = DefaultDeviceFamilySettingsOperations(
        devicesRepository = repository
    )
    private val root = DebugFixtureDeviceRootOperations(real, fixtures)

    override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = root.observe(deviceUid)

    override fun current(deviceUid: String): DeviceRootSnapshot? = root.current(deviceUid)

    override fun connect(deviceUid: String): Result<Unit> = root.connect(deviceUid)

    override suspend fun updateCustomName(deviceUid: String, customName: String): Result<Unit> =
        if (fixtures.contains(deviceUid)) Result.success(Unit)
        else real.updateCustomName(deviceUid, customName)

}
