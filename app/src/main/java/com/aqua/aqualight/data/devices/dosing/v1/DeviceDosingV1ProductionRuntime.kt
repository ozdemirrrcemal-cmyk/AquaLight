package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingLowLevelAlertTextResolver
import com.aqua.aqualight.application.notifications.NotificationDispatchUseCase
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
        alertTextResolver = alertTextResolver
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
        adapter.consume(event)
        if (
            event is DeviceRuntimeLifecycleEvent.Authenticated &&
            devicesRepository.currentDevice(event.deviceUid)?.product?.family == DeviceFamily.DOSING
        ) {
            try {
                // The normal bootstrap gets first chance to rebuild authoritative state. Recovery
                // workers are released afterwards and therefore restart from a fresh central baseline.
                channelOperations.refreshAll(event.deviceUid.value)
            } finally {
                adapter.resumePendingAssignments(event.deviceUid)
            }
        }
    }

    override fun close() {
        runtimeScope.cancel()
    }
}
