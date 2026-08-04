package com.aqua.aqualight.application.devices

import kotlinx.coroutines.flow.Flow

/**
 * Owner-scoped application boundary for the Light temperature-protection Settings card.
 *
 * Presentation receives only stable product values and edit policy. Runtime modules, transport
 * outcomes, firmware payloads and persistence details remain behind the data implementation.
 */
interface DeviceLightProtectionOperations {
    fun observeLightProtection(deviceUid: String): Flow<DeviceLightProtectionSnapshot>

    fun currentLightProtection(deviceUid: String): DeviceLightProtectionSnapshot

    suspend fun refreshLightProtection(deviceUid: String): Result<Unit>

    suspend fun updateLightProtectionThreshold(
        deviceUid: String,
        thresholdCelsius: Int
    ): Result<Unit>
}

data class DeviceLightProtectionSnapshot(
    val available: Boolean = false,
    val currentTemperatureCelsius: Double? = null,
    val thresholdCelsius: Double? = null,
    val thresholdPolicy: DeviceLightProtectionThresholdPolicy? = null,
    val loaded: Boolean = false
)

data class DeviceLightProtectionThresholdPolicy(
    val currentCelsius: Int,
    val minimumCelsius: Int,
    val maximumCelsius: Int,
    val stepCelsius: Int
) {
    init {
        require(minimumCelsius <= maximumCelsius) {
            "Temperature protection minimum exceeds maximum."
        }
        require(currentCelsius in minimumCelsius..maximumCelsius) {
            "Temperature protection threshold is outside the editable range."
        }
        require(stepCelsius > 0) {
            "Temperature protection step must be positive."
        }
    }
}
