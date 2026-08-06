package com.aqua.aqualight.data.devices.update

import android.content.Context
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeLifecycleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/** Coalesces authenticated runtime events into one owner-scoped availability check. */
internal class DeviceFirmwareAvailabilityEventTrigger(
    context: Context,
    ownerUid: String,
    lifecycleEvents: SharedFlow<DeviceRuntimeLifecycleEvent>?
) : AutoCloseable {

    private val appContext = context.applicationContext
    private val ownerUid = ownerUid.trim().also { normalized ->
        require(normalized.isNotBlank()) { "ownerUid must not be blank" }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        lifecycleEvents?.let { events ->
            scope.launch {
                events
                    .filterIsInstance<DeviceRuntimeLifecycleEvent.Authenticated>()
                    .collect {
                        DeviceFirmwareAvailabilityWorker.enqueueAuthenticated(
                            context = appContext,
                            ownerUid = this@DeviceFirmwareAvailabilityEventTrigger.ownerUid
                        )
                    }
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}
