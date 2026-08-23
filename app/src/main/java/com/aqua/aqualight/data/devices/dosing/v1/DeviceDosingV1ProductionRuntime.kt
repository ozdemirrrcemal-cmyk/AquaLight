package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingLowLevelAlertTextResolver
import com.aqua.aqualight.application.notifications.NotificationDispatchUseCase
import com.aqua.aqualight.base.diagnostics.AppDiagnosticTrace
import com.aqua.aqualight.data.devices.dosing.DeviceDosingLowLevelAlertLedger
import com.aqua.aqualight.data.devices.dosing.DeviceDosingLowLevelAlertMonitor
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeLifecycleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Owner-scoped production bridge for the pinned Dosing v1 contract.
 *
 * The bridge reuses the one shared device-runtime command gateway and typed event pipeline. It owns
 * no transport, socket, protocol session or parallel state store: [DeviceDosingV1StateOwner] inside
 * [DeviceDosingV1StateAdapter] remains the only authoritative mutable Dosing state owner.
 */
internal class DeviceDosingV1ProductionRuntime(
    private val devicesRepository: DevicesRepository,
    ownerUid: String,
    lowLevelAlertLedger: DeviceDosingLowLevelAlertLedger,
    notificationDispatch: NotificationDispatchUseCase,
    alertTextResolver: DeviceDosingLowLevelAlertTextResolver
) : AutoCloseable {
    private val runtimeJob = SupervisorJob()
    private val runtimeScope = CoroutineScope(runtimeJob + Dispatchers.Default)
    private val runtimeModules = requireNotNull(devicesRepository.runtimeModules()) {
        "Device runtime modules are unavailable for production Dosing composition."
    }
    private val lifecycleEvents = requireNotNull(devicesRepository.runtimeLifecycleEvents()) {
        "Device runtime lifecycle is unavailable for production Dosing composition."
    }
    private val stateOwner = DeviceDosingV1StateOwner(lowLevelAlertLedger)
    private val adapter = DeviceDosingV1StateAdapter(
        repository = DeviceDosingV1Repository(runtimeModules.commandGateway),
        stateOwner = stateOwner,
        reconciliationScope = runtimeScope
    )
    private val alertMonitor = DeviceDosingLowLevelAlertMonitor(
        ownerUid = ownerUid,
        ledger = lowLevelAlertLedger,
        notificationDispatch = notificationDispatch,
        textResolver = alertTextResolver
    )

    val channelOperations: DeviceDosingChannelOperations = adapter.channelOperations
    val calibrationOperations: DeviceDosingCalibrationOperations = adapter.calibrationOperations

    init {
        runtimeScope.launch(start = CoroutineStart.UNDISPATCHED) {
            lifecycleEvents.collect(::consumeLifecycle)
        }
        runtimeScope.launch(start = CoroutineStart.UNDISPATCHED) {
            devicesRepository.typedRuntimeEvents().collect { event -> adapter.consume(event) }
        }
        runtimeScope.launch(start = CoroutineStart.UNDISPATCHED) {
            devicesRepository.devices.collectLatest { devices ->
                coroutineScope {
                    devices
                        .filter { device -> device.product.family == DeviceFamily.DOSING }
                        .forEach { device ->
                            launch {
                                alertMonitor.monitor(
                                    channelOperations.observeAll(device.deviceUid.value)
                                )
                            }
                        }
                }
            }
        }
    }

    private suspend fun consumeLifecycle(event: DeviceRuntimeLifecycleEvent) {
        event.traceLifecycle("arrived")
        adapter.consume(event)
        event.traceLifecycle("state_invalidated")
        if (
            event is DeviceRuntimeLifecycleEvent.Authenticated &&
            devicesRepository.currentDevice(event.deviceUid)?.product?.family == DeviceFamily.DOSING
        ) {
            event.traceLifecycle("readback_started")
            val authoritative = channelOperations.refreshAll(event.deviceUid.value)
            event.traceLifecycle("readback_completed", "authoritative" to authoritative)
        }
    }

    override fun close() {
        AppDiagnosticTrace.event(DOSING_LIFECYCLE_CATEGORY, "runtime_closed")
        runtimeScope.cancel()
    }
}

private fun DeviceRuntimeLifecycleEvent.traceLifecycle(
    name: String,
    vararg fields: Pair<String, Any?>
) {
    AppDiagnosticTrace.event(
        DOSING_LIFECYCLE_CATEGORY,
        name,
        "device" to AppDiagnosticTrace.deviceRef(deviceUid.value),
        "lifecycleType" to javaClass.simpleName,
        *fields
    )
}

private const val DOSING_LIFECYCLE_CATEGORY = "dosing_lifecycle"
