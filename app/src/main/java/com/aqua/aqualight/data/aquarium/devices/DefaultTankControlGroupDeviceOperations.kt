package com.aqua.aqualight.data.aquarium.devices

import com.aqua.aqualight.application.devices.groups.TankControlGroupDevice
import com.aqua.aqualight.application.devices.groups.TankControlGroupDeviceOperations
import com.aqua.aqualight.application.devices.groups.TankControlGroupDevices
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal class DefaultTankControlGroupDeviceOperations(
    private val assignments: TankDeviceAssignmentRepository,
    private val repository: DevicesRepository,
    private val tanks: AquariumTankDataStoreManager
) : TankControlGroupDeviceOperations {
    override fun start(scope: CoroutineScope): Job = repository.start(scope)

    override fun observe(tankId: Long): Flow<TankControlGroupDevices> = combine(
        assignments.assignedDevicesForTank(tankId), repository.ready, tanks.tanksFlow
    ) { snapshots, ready, storedTanks ->
        val tank = storedTanks.firstOrNull { it.id == tankId }
        val devices = if (tank == null) emptyList() else snapshots.mapNotNull { it.toControlGroupDevice() }
            .distinctBy { it.deviceUid }
            .sortedWith(compareBy<TankControlGroupDevice> {
                it.device.displayName.lowercase(Locale.ROOT)
            }.thenBy { it.deviceUid })
        TankControlGroupDevices(tankName = tank?.name.orEmpty(), devices = devices,
            isLoading = !ready, tankExists = tank != null)
    }
}
