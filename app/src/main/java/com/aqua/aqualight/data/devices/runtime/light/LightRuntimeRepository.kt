package com.aqua.aqualight.data.devices.runtime.light

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide single source of truth for Light runtime state.
 *
 * Dashboard, Manual, Presets and Settings screens all obtain the same
 * LightRuntimeSession for a deviceId. This prevents each screen from owning a
 * separate polling/cache pipeline.
 */
class LightRuntimeRepository private constructor(
    context: Context
) {

    private val appContext = context.applicationContext
    private val accessor = LightRuntimeDeviceAccessor(
        context = appContext
    )
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )
    private val sessions = ConcurrentHashMap<Long, LightRuntimeSession>()

    fun session(
        deviceId: Long
    ): LightRuntimeSession {
        return sessions.getOrPut(deviceId) {
            LightRuntimeSession(
                deviceId = deviceId,
                accessor = accessor,
                scope = scope,
                ioDispatcher = Dispatchers.IO
            )
        }
    }

    companion object {
        @Volatile
        private var instance: LightRuntimeRepository? = null

        fun get(
            context: Context
        ): LightRuntimeRepository {
            return instance ?: synchronized(this) {
                instance ?: LightRuntimeRepository(
                    context = context.applicationContext
                ).also { repository ->
                    instance = repository
                }
            }
        }
    }
}
