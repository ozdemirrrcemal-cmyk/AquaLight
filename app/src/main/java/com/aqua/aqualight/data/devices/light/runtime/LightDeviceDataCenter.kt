package com.aqua.aqualight.data.devices.light.runtime

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Single entry point for light runtime data.
 *
 * Screens must not create independent live/manual data pipelines. This facade
 * shares the ESP32 refresh loop, initializes snapshot cache, and exposes one
 * combined runtime stream for dashboard, manual controls and tank cards.
 *
 * LightManualRuntimeStore is intentionally in-memory and does not require
 * Context/configuration.
 */
object LightDeviceDataCenter {

    private const val DEFAULT_REFRESH_INTERVAL_MS = 4_000L

    fun configure(
        context: Context
    ) {
        val appContext = context.applicationContext

        LightDeviceSnapshotCache.configure(
            context = appContext
        )
    }

    fun start(
        context: Context,
        deviceId: Long,
        ownerKey: String,
        refreshIntervalMs: Long = DEFAULT_REFRESH_INTERVAL_MS
    ) {
        val appContext = context.applicationContext

        configure(
            context = appContext
        )

        LightDeviceLiveRefreshManager.start(
            context = appContext,
            deviceId = deviceId,
            ownerKey = ownerKey,
            refreshIntervalMs = refreshIntervalMs
        )
    }

    fun stop(
        deviceId: Long,
        ownerKey: String
    ) {
        LightDeviceLiveRefreshManager.stop(
            deviceId = deviceId,
            ownerKey = ownerKey
        )
    }

    fun stopAll() {
        LightDeviceLiveRefreshManager.stopAll()
        LightManualRuntimeStore.clearAll()
    }

    fun refreshNow(
        context: Context,
        deviceId: Long
    ) {
        val appContext = context.applicationContext

        configure(
            context = appContext
        )

        LightDeviceLiveRefreshManager.refreshNow(
            context = appContext,
            deviceId = deviceId
        )
    }

    fun observe(
        deviceId: Long
    ): Flow<LightDeviceRuntimeSnapshot> {
        return combine(
            LightDeviceLiveRefreshManager.observe(deviceId),
            LightManualRuntimeStore.observe(deviceId)
        ) { liveState, manualRuntime ->
            LightDeviceRuntimeSnapshot(
                liveState = liveState,
                manualRuntime = manualRuntime
            )
        }.distinctUntilChanged()
    }

    fun observeLiveState(
        deviceId: Long
    ): StateFlow<LightDeviceLiveState> {
        return LightDeviceLiveRefreshManager.observe(
            deviceId = deviceId
        )
    }

    fun observeManualRuntime(
        deviceId: Long
    ): Flow<LightManualRuntimeState> {
        return LightManualRuntimeStore.observe(
            deviceId = deviceId
        )
    }

    fun currentManualRuntime(
        deviceId: Long
    ): LightManualRuntimeState {
        return LightManualRuntimeStore.current(
            deviceId = deviceId
        )
    }
}