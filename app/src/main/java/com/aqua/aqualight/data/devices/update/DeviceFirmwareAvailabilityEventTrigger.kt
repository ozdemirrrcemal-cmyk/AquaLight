package com.aqua.aqualight.data.devices.update

import android.content.Context
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeLifecycleEvent
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

internal data class DeviceFirmwareAvailabilityEventTriggerDependencies(
    val trust: DeviceFirmwareAvailabilityTrust,
    val policy: DeviceFirmwareAvailabilityTrustPolicy,
    val dispatcher: CoroutineDispatcher,
    val enqueueCheck: (Context, String) -> Unit
) {
    companion object {
        fun create(context: Context): DeviceFirmwareAvailabilityEventTriggerDependencies {
            return DeviceFirmwareAvailabilityEventTriggerDependencies(
                trust = DeviceFirmwareAvailabilityTrustStore.create(context),
                policy = DeviceFirmwareAvailabilityTrustPolicy(),
                dispatcher = Dispatchers.Default,
                enqueueCheck = { appContext, ownerUid ->
                    DeviceFirmwareAvailabilityWorker.enqueueValidated(
                        appContext,
                        ownerUid
                    )
                }
            )
        }
    }
}

/** Converts validated runtime metadata into durable, owner-scoped availability trust. */
internal class DeviceFirmwareAvailabilityEventTrigger(
    context: Context,
    ownerUid: String,
    lifecycleEvents: SharedFlow<DeviceRuntimeLifecycleEvent>?,
    snapshots: StateFlow<Map<DeviceUid, DeviceSnapshot>>? =
        DevicesRepositoryProvider.currentRepository(ownerUid)?.snapshots,
    dependencies: DeviceFirmwareAvailabilityEventTriggerDependencies =
        DeviceFirmwareAvailabilityEventTriggerDependencies.create(context)
) : AutoCloseable {

    private val appContext = context.applicationContext
    private val ownerUid =
        DeviceFirmwareAvailabilityTrustCodec.normalizeOwnerUid(ownerUid)
    private val trust = dependencies.trust
    private val policy = dependencies.policy
    private val enqueueCheck = dependencies.enqueueCheck
    private val scope = CoroutineScope(SupervisorJob() + dependencies.dispatcher)
    private val triggeredFingerprints = ConcurrentHashMap<String, String>()

    init {
        snapshots?.let(::observeSnapshots)
        lifecycleEvents?.let(::observeUnavailableEvents)
    }

    internal suspend fun acceptSnapshot(snapshot: DeviceSnapshot) {
        val deviceUid = snapshot.deviceUid.value
        if (!trust.recordValidated(ownerUid, snapshot)) {
            triggeredFingerprints.remove(deviceUid)
            trust.clearDevice(ownerUid, deviceUid)
            return
        }
        val fingerprint = policy.fingerprint(snapshot)
        if (triggeredFingerprints.put(deviceUid, fingerprint) != fingerprint) {
            enqueueCheck(appContext, ownerUid)
        }
    }

    internal suspend fun acceptUnavailable(deviceUid: DeviceUid) {
        triggeredFingerprints.remove(deviceUid.value)
        trust.clearDevice(ownerUid, deviceUid.value)
    }

    override fun close() {
        scope.cancel()
    }

    private fun observeSnapshots(
        snapshots: StateFlow<Map<DeviceUid, DeviceSnapshot>>
    ) {
        scope.launch {
            snapshots.collect { current ->
                current.values.forEach { snapshot ->
                    acceptSnapshot(snapshot)
                }
            }
        }
    }

    private fun observeUnavailableEvents(
        lifecycleEvents: SharedFlow<DeviceRuntimeLifecycleEvent>
    ) {
        scope.launch {
            lifecycleEvents
                .filterIsInstance<DeviceRuntimeLifecycleEvent.Unavailable>()
                .collect { event -> acceptUnavailable(event.deviceUid) }
        }
    }
}
