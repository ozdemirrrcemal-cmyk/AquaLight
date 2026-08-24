package com.aqua.aqualight.application.devices.dosing

import kotlinx.coroutines.flow.Flow

/**
 * Read-only application port for the tank-detail Dosing device card.
 *
 * The application contract intentionally exposes observation only. Runtime/session preparation is
 * owned by the production Dosing runtime adapter and must never be initiated by UI/ViewModel code.
 */
interface DeviceDosingCardOperations {
    fun observe(deviceUid: String): Flow<DeviceDosingCardSummary?>
}
