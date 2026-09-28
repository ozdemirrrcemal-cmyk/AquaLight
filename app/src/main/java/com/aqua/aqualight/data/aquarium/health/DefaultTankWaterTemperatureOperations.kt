package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.TankWaterTemperatureOperations
import com.aqua.aqualight.application.aquarium.health.TankWaterTemperatureState
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCardOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCardState
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

internal class DefaultTankWaterTemperatureOperations(
    private val assignments: TankDeviceAssignmentRepository,
    private val cooling: DeviceCoolingCardOperations
) : TankWaterTemperatureOperations {
    override fun observe(tankId: Long): Flow<TankWaterTemperatureState> =
        if (tankId <= 0L) kotlinx.coroutines.flow.flowOf(TankWaterTemperatureState.Invalid)
        else assignments.assignedDevicesForTank(tankId)
            .map { devices -> devices.firstOrNull { it.product.family.name == "COOLING" } }
            .distinctUntilChanged { a, b -> a?.deviceUid == b?.deviceUid }
            .flatMapLatest { device ->
                if (device == null) kotlinx.coroutines.flow.flowOf(TankWaterTemperatureState.NoAssignedCooling)
                else cooling.observe(device.deviceUid.value).map { state ->
                    when (state) {
                        DeviceCoolingCardState.Preparing -> TankWaterTemperatureState.Loading
                        is DeviceCoolingCardState.Unavailable -> TankWaterTemperatureState.Unavailable
                        is DeviceCoolingCardState.Ready -> state.summary.waterTemperatureC?.let { value ->
                            if (value.isFinite()) TankWaterTemperatureState.Reading(device.deviceUid.value, value)
                            else TankWaterTemperatureState.Invalid
                        } ?: TankWaterTemperatureState.Invalid
                    }
                }
            }.catch { emit(TankWaterTemperatureState.Unavailable) }
}

