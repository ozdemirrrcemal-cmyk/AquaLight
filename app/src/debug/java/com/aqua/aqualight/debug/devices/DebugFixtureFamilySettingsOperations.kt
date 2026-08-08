package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceFamilySettingsOperations
import com.aqua.aqualight.application.devices.DeviceLightProtectionSnapshot
import com.aqua.aqualight.application.devices.DeviceLightProtectionThresholdPolicy
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.data.devices.DefaultDeviceFamilySettingsOperations
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Keeps the shared Settings screen usable for fixtures without sending runtime commands. */
internal class DebugFixtureFamilySettingsOperations(
    repository: DevicesRepository,
    private val fixtures: DebugDeviceFixtureCatalog
) : DeviceFamilySettingsOperations {

    private val real = DefaultDeviceFamilySettingsOperations(repository)
    private val root = DebugFixtureDeviceRootOperations(real, fixtures)

    override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = root.observe(deviceUid)

    override fun current(deviceUid: String): DeviceRootSnapshot? = root.current(deviceUid)

    override fun connect(deviceUid: String): Result<Unit> = root.connect(deviceUid)

    override suspend fun updateCustomName(deviceUid: String, customName: String): Result<Unit> =
        if (fixtures.contains(deviceUid)) Result.success(Unit)
        else real.updateCustomName(deviceUid, customName)

    override fun observeLightProtection(deviceUid: String): Flow<DeviceLightProtectionSnapshot> =
        if (fixtures.contains(deviceUid)) flowOf(fixtureLightProtection(deviceUid))
        else real.observeLightProtection(deviceUid)

    override fun currentLightProtection(deviceUid: String): DeviceLightProtectionSnapshot =
        if (fixtures.contains(deviceUid)) fixtureLightProtection(deviceUid)
        else real.currentLightProtection(deviceUid)

    override suspend fun refreshLightProtection(deviceUid: String): Result<Unit> =
        if (fixtures.contains(deviceUid)) Result.success(Unit)
        else real.refreshLightProtection(deviceUid)

    override suspend fun updateLightProtectionThreshold(
        deviceUid: String,
        thresholdCelsius: Int
    ): Result<Unit> = if (fixtures.contains(deviceUid)) {
        Result.success(Unit)
    } else {
        real.updateLightProtectionThreshold(deviceUid, thresholdCelsius)
    }

    private fun fixtureLightProtection(deviceUid: String): DeviceLightProtectionSnapshot {
        val rootSnapshot = fixtures.rootSnapshot(deviceUid)
        return if (rootSnapshot?.family == OwnerDeviceFamily.LIGHT) {
            DeviceLightProtectionSnapshot(
                available = true,
                currentTemperatureCelsius = 26.4,
                thresholdCelsius = 32.0,
                thresholdPolicy = DeviceLightProtectionThresholdPolicy(
                    currentCelsius = 32,
                    minimumCelsius = 20,
                    maximumCelsius = 50,
                    stepCelsius = 1
                ),
                loaded = true
            )
        } else {
            DeviceLightProtectionSnapshot(loaded = true)
        }
    }
}
