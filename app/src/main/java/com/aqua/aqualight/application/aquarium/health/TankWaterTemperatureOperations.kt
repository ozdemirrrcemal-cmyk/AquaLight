package com.aqua.aqualight.application.aquarium.health

import kotlinx.coroutines.flow.Flow

/** Authoritative tank temperature projection from an assigned Cooling device. */
interface TankWaterTemperatureOperations {
    fun observe(tankId: Long): Flow<TankWaterTemperatureState>
}

sealed interface TankWaterTemperatureState {
    data object Loading : TankWaterTemperatureState
    data object NoAssignedCooling : TankWaterTemperatureState
    data object Unavailable : TankWaterTemperatureState
    data object Invalid : TankWaterTemperatureState
    data class Reading(
        val deviceUid: String,
        val temperatureCelsius: Double
    ) : TankWaterTemperatureState
}

