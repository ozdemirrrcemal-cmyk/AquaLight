package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

@Suppress("TooManyFunctions", "LargeClass")
class DeviceRuntimeDataRepository(
    private val devicesRepository: DevicesRepository,
    private val stateStore: DeviceRuntimeStateStore = DeviceRuntimeStateStore(),
    private val commandTimeoutMillis: Long = DEFAULT_COMMAND_TIMEOUT_MS
) : AutoCloseable {

    private data class PendingRequest(
        val deviceUid: DeviceUid,
        val module: String,
        val action: String,
        val deferred: CompletableDeferred<DeviceRuntimeCommandOutcome>
    )

    private val lifecycleLock = Any()
    private val refreshLock = Any()
    private val pendingRequests = ConcurrentHashMap<String, PendingRequest>()
    private val bootstrappedGenerations = ConcurrentHashMap<DeviceUid, Long>()
    private val queuedRefreshTargets = mutableMapOf<DeviceUid, MutableSet<DeviceRuntimeRefreshTarget>>()
    private val refreshJobs = mutableMapOf<DeviceUid, Job>()
    private val bootstrapJobs = mutableMapOf<DeviceUid, Job>()

    @Volatile
    private var startJob: Job? = null

    @Volatile
    private var activeScope: CoroutineScope? = null

    val states: StateFlow<Map<DeviceUid, DeviceRuntimeState>> = stateStore.states

    init {
        require(commandTimeoutMillis in MIN_COMMAND_TIMEOUT_MS..MAX_COMMAND_TIMEOUT_MS) {
            "commandTimeoutMillis is outside the commercial runtime range."
        }
    }

    fun observe(deviceUid: DeviceUid): Flow<DeviceRuntimeState> = stateStore.observe(deviceUid)

    fun current(deviceUid: DeviceUid): DeviceRuntimeState = stateStore.current(deviceUid)

    fun start(scope: CoroutineScope): Job {
        val active = startJob
        if (active?.isActive == true) return active
        return synchronized(lifecycleLock) {
            val current = startJob
            if (current?.isActive == true) {
                current
            } else {
                scope.launch {
                    activeScope = this
                    try {
                        coroutineScope {
                            launch { collectRuntimeEvents() }
                            launch { collectValidatedSnapshots() }
                            awaitCancellation()
                        }
                    } finally {
                        activeScope = null
                        completeAllPending("runtime data repository stopped")
                        cancelBackgroundJobs()
                    }
                }.also { job ->
                    startJob = job
                    job.invokeOnCompletion {
                        if (startJob == job) startJob = null
                    }
                }
            }
        }
    }

    suspend fun execute(
        deviceUid: DeviceUid,
        module: String,
        action: String,
        data: JSONObject = JSONObject(),
        timeoutMillis: Long = commandTimeoutMillis
    ): DeviceRuntimeCommandOutcome {
        require(timeoutMillis in MIN_COMMAND_TIMEOUT_MS..MAX_COMMAND_TIMEOUT_MS) {
            "timeoutMillis is outside the commercial runtime range."
        }
        require(AqlWsContract.isAuthenticatedCommand(module, action)) {
            "Unregistered firmware command: $module.$action"
        }
        if (startJob?.isActive != true) {
            return DeviceRuntimeCommandOutcome.Cancelled(
                deviceUid = deviceUid,
                module = module,
                action = action,
                messageId = "",
                reason = "Runtime data repository is not active."
            )
        }

        val client = devicesRepository.commandClient(deviceUid)
            ?: return DeviceRuntimeCommandOutcome.NotConnected(deviceUid, module, action)
        val command = AqlWsOutgoingMessage.Command(
            module = module,
            action = action,
            data = JSONObject(data.toString())
        )
        val pending = PendingRequest(
            deviceUid = deviceUid,
            module = module,
            action = action,
            deferred = CompletableDeferred()
        )
        check(pendingRequests.putIfAbsent(command.id, pending) == null) {
            "Duplicate WebSocket command id: ${command.id}"
        }

        if (!client.send(command)) {
            pendingRequests.remove(command.id, pending)
            pending.deferred.cancel()
            stateStore.applyCommandFault(
                deviceUid = deviceUid,
                code = "send_failed",
                message = "WebSocket command could not be queued.",
                module = module,
                action = action,
                messageId = command.id
            )
            return DeviceRuntimeCommandOutcome.SendFailed(
                deviceUid = deviceUid,
                module = module,
                action = action,
                messageId = command.id
            )
        }

        return try {
            withTimeoutOrNull(timeoutMillis) {
                pending.deferred.await()
            } ?: DeviceRuntimeCommandOutcome.Timeout(
                deviceUid = deviceUid,
                module = module,
                action = action,
                messageId = command.id,
                timeoutMillis = timeoutMillis
            ).also {
                stateStore.applyCommandFault(
                    deviceUid = deviceUid,
                    code = "command_timeout",
                    message = "Firmware did not answer within ${timeoutMillis}ms.",
                    module = module,
                    action = action,
                    messageId = command.id
                )
            }
        } finally {
            pendingRequests.remove(command.id, pending)
            pending.deferred.cancel()
        }
    }

    suspend fun refreshAll(
        deviceUid: DeviceUid
    ): Map<DeviceRuntimeRefreshTarget, DeviceRuntimeCommandOutcome> {
        val snapshot = devicesRepository.currentDevice(deviceUid) ?: return emptyMap()
        return refreshTargets(deviceUid, bootstrapTargets(snapshot))
    }

    suspend fun refresh(
        deviceUid: DeviceUid,
        target: DeviceRuntimeRefreshTarget
    ): DeviceRuntimeCommandOutcome = executeRefresh(deviceUid, target)

    private suspend fun collectRuntimeEvents() {
        val events = devicesRepository.runtimeEvents() ?: return
        events.collect { event ->
            when (event) {
                is AqlWsEvent.Authenticated -> {
                    stateStore.markAuthenticated(event.deviceUid)
                    devicesRepository.currentDevice(event.deviceUid)?.let(::maybeBootstrap)
                }
                is AqlWsEvent.Message -> onMessage(event.deviceUid, event.parsed)
                is AqlWsEvent.Closed -> onUnavailable(
                    event.deviceUid,
                    "socket_closed_${event.code}",
                    event.reason
                )
                is AqlWsEvent.Failure -> onUnavailable(
                    event.deviceUid,
                    "socket_failure",
                    event.message
                )
                is AqlWsEvent.Opened -> Unit
            }
        }
    }

    private suspend fun collectValidatedSnapshots() {
        devicesRepository.snapshots.collect { snapshots ->
            val present = snapshots.keys
            bootstrappedGenerations.keys
                .filterNot(present::contains)
                .forEach(::retire)
            snapshots.values.forEach(::maybeBootstrap)
        }
    }

    private fun maybeBootstrap(snapshot: DeviceSnapshot) {
        if (!snapshot.hasValidatedRuntimeMetadata) return
        if (!stateStore.current(snapshot.deviceUid).authenticated) return
        val generation = snapshot.runtimeMetadataGeneration
        if (bootstrappedGenerations.put(snapshot.deviceUid, generation) == generation) return

        val targets = bootstrapTargets(snapshot)
        stateStore.beginBootstrap(snapshot.deviceUid, generation, targets)
        val scope = activeScope ?: return
        synchronized(refreshLock) {
            bootstrapJobs.remove(snapshot.deviceUid)?.cancel()
            val job = scope.launch {
                try {
                    refreshTargets(snapshot.deviceUid, targets)
                } finally {
                    synchronized(refreshLock) {
                        if (bootstrapJobs[snapshot.deviceUid] == coroutineContext[Job]) {
                            bootstrapJobs.remove(snapshot.deviceUid)
                        }
                    }
                }
            }
            bootstrapJobs[snapshot.deviceUid] = job
        }
    }

    private fun onMessage(deviceUid: DeviceUid, message: AqlWsIncomingMessage) {
        val targets = stateStore.applyMessage(deviceUid, message)
        completeCorrelatedRequest(deviceUid, message)

        val shouldAutoRefresh = message is AqlWsIncomingMessage.Event ||
            message is AqlWsIncomingMessage.Response && !isReadCommand(message.module, message.action)
        if (shouldAutoRefresh && targets.isNotEmpty()) {
            scheduleRefresh(deviceUid, targets)
        }
    }

    private fun completeCorrelatedRequest(
        deviceUid: DeviceUid,
        message: AqlWsIncomingMessage
    ) {
        if (message !is AqlWsIncomingMessage.Response && message !is AqlWsIncomingMessage.Error) {
            return
        }
        val pending = pendingRequests[message.id] ?: return
        val exactMatch = pending.deviceUid == deviceUid &&
            pending.module == message.module &&
            pending.action == message.action
        val outcome = if (exactMatch) {
            message.toCommandOutcome(deviceUid)
        } else {
            stateStore.applyCommandFault(
                deviceUid = pending.deviceUid,
                code = "correlation_mismatch",
                message = "Firmware response id matched a different device/module/action.",
                module = pending.module,
                action = pending.action,
                messageId = message.id
            )
            DeviceRuntimeCommandOutcome.FirmwareError(
                deviceUid = pending.deviceUid,
                module = pending.module,
                action = pending.action,
                messageId = message.id,
                statusCode = 500,
                code = "correlation_mismatch",
                message = "Firmware response correlation did not match the request.",
                field = "id"
            )
        }
        if (pendingRequests.remove(message.id, pending)) {
            pending.deferred.complete(outcome)
        }
    }

    private fun AqlWsIncomingMessage.toCommandOutcome(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome = when (this) {
        is AqlWsIncomingMessage.Response -> if (ok) {
            DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = module,
                action = action,
                messageId = id,
                statusCode = statusCode,
                data = JSONObject(data.toString())
            )
        } else {
            DeviceRuntimeCommandOutcome.FirmwareError(
                deviceUid = deviceUid,
                module = module,
                action = action,
                messageId = id,
                statusCode = statusCode,
                code = "response_not_ok",
                message = "Firmware returned a non-success response.",
                field = ""
            )
        }
        is AqlWsIncomingMessage.Error -> DeviceRuntimeCommandOutcome.FirmwareError(
            deviceUid = deviceUid,
            module = module,
            action = action,
            messageId = id,
            statusCode = statusCode,
            code = code,
            message = message,
            field = field
        )
        is AqlWsIncomingMessage.Event -> error("Events cannot complete command requests.")
    }

    private fun onUnavailable(deviceUid: DeviceUid, code: String, message: String) {
        stateStore.applyTransportFault(deviceUid, code, message)
        bootstrappedGenerations.remove(deviceUid)
        completePending(deviceUid, message)
        synchronized(refreshLock) {
            bootstrapJobs.remove(deviceUid)?.cancel()
            refreshJobs.remove(deviceUid)?.cancel()
            queuedRefreshTargets.remove(deviceUid)
        }
    }

    private fun scheduleRefresh(
        deviceUid: DeviceUid,
        targets: Set<DeviceRuntimeRefreshTarget>
    ) {
        val scope = activeScope ?: return
        synchronized(refreshLock) {
            queuedRefreshTargets.getOrPut(deviceUid) { linkedSetOf() }.addAll(targets)
            if (refreshJobs[deviceUid]?.isActive == true) return
            refreshJobs[deviceUid] = scope.launch {
                var restart = false
                try {
                    delay(STATUS_REFRESH_DEBOUNCE_MS)
                    val batch = synchronized(refreshLock) {
                        queuedRefreshTargets.remove(deviceUid).orEmpty().toSet()
                    }
                    if (batch.isNotEmpty()) refreshTargets(deviceUid, batch)
                } finally {
                    synchronized(refreshLock) {
                        refreshJobs.remove(deviceUid)
                        restart = queuedRefreshTargets[deviceUid].orEmpty().isNotEmpty()
                    }
                    if (restart) scheduleRefresh(deviceUid, emptySet())
                }
            }
        }
    }

    private suspend fun refreshTargets(
        deviceUid: DeviceUid,
        targets: Set<DeviceRuntimeRefreshTarget>
    ): Map<DeviceRuntimeRefreshTarget, DeviceRuntimeCommandOutcome> {
        val outcomes = linkedMapOf<DeviceRuntimeRefreshTarget, DeviceRuntimeCommandOutcome>()
        for (target in targets) {
            outcomes[target] = executeRefresh(deviceUid, target)
        }
        return outcomes
    }

    private suspend fun executeRefresh(
        deviceUid: DeviceUid,
        target: DeviceRuntimeRefreshTarget
    ): DeviceRuntimeCommandOutcome {
        val (module, action) = target.command()
        return execute(deviceUid, module, action)
    }

    private fun bootstrapTargets(snapshot: DeviceSnapshot): Set<DeviceRuntimeRefreshTarget> = buildSet {
        add(DeviceRuntimeRefreshTarget.DEVICE)
        add(DeviceRuntimeRefreshTarget.SECURITY)
        add(DeviceRuntimeRefreshTarget.TIME)
        if ("network" in snapshot.modules) add(DeviceRuntimeRefreshTarget.NETWORK)
        if ("firmware" in snapshot.modules) add(DeviceRuntimeRefreshTarget.FIRMWARE)
        if (snapshot.capabilities.ota) add(DeviceRuntimeRefreshTarget.OTA)
        if (snapshot.capabilities.light) {
            add(DeviceRuntimeRefreshTarget.LIGHT)
            if (
                AqlDeviceFeatureKey.LIGHT_TEMPERATURE_PROTECTION.wireValue in
                snapshot.supportedFeatures
            ) {
                add(DeviceRuntimeRefreshTarget.LIGHT_TEMPERATURE_PROTECTION)
            }
        }
        if (snapshot.capabilities.cooling || snapshot.capabilities.fan) {
            add(DeviceRuntimeRefreshTarget.COOLING)
        }
        if (snapshot.capabilities.standaloneTimer) add(DeviceRuntimeRefreshTarget.TIMER)
        if (snapshot.capabilities.dosing) add(DeviceRuntimeRefreshTarget.DOSING)
    }

    private fun DeviceRuntimeRefreshTarget.command(): Pair<String, String> = when (this) {
        DeviceRuntimeRefreshTarget.DEVICE ->
            AqlWsContract.MODULE_DEVICE to AqlWsContract.ACTION_DEVICE_STATUS_GET
        DeviceRuntimeRefreshTarget.SECURITY ->
            AqlWsContract.MODULE_SECURITY to AqlWsContract.ACTION_SECURITY_STATUS_GET
        DeviceRuntimeRefreshTarget.NETWORK ->
            AqlWsContract.MODULE_NETWORK to AqlWsContract.ACTION_NETWORK_STATUS_GET
        DeviceRuntimeRefreshTarget.TIME ->
            AqlWsContract.MODULE_TIME to AqlWsContract.ACTION_TIME_STATUS_GET
        DeviceRuntimeRefreshTarget.FIRMWARE ->
            AqlWsContract.MODULE_FIRMWARE to AqlWsContract.ACTION_FIRMWARE_STATUS_GET
        DeviceRuntimeRefreshTarget.OTA ->
            AqlWsContract.MODULE_FIRMWARE to AqlWsContract.ACTION_FIRMWARE_OTA_STATUS
        DeviceRuntimeRefreshTarget.LIGHT ->
            AqlWsContract.MODULE_LIGHT to AqlWsContract.ACTION_LIGHT_STATUS_GET
        DeviceRuntimeRefreshTarget.LIGHT_TEMPERATURE_PROTECTION ->
            AqlWsContract.MODULE_LIGHT to
                AqlWsContract.ACTION_LIGHT_TEMPERATURE_PROTECTION_STATUS_GET
        DeviceRuntimeRefreshTarget.COOLING ->
            AqlWsContract.MODULE_COOLING to AqlWsContract.ACTION_COOLING_STATUS_GET
        DeviceRuntimeRefreshTarget.TIMER ->
            AqlWsContract.MODULE_TIMER to AqlWsContract.ACTION_TIMER_STATUS_GET
        DeviceRuntimeRefreshTarget.DOSING ->
            AqlWsContract.MODULE_DOSING to AqlWsContract.ACTION_DOSING_STATUS_GET
    }

    private fun isReadCommand(module: String, action: String): Boolean =
        action == AqlWsContract.ACTION_STATUS_GET ||
            module == AqlWsContract.MODULE_FIRMWARE &&
            action == AqlWsContract.ACTION_FIRMWARE_OTA_STATUS ||
            module == AqlWsContract.MODULE_LIGHT &&
            action == AqlWsContract.ACTION_LIGHT_TEMPERATURE_PROTECTION_STATUS_GET ||
            module == AqlWsContract.MODULE_DEVICE &&
            action in setOf(
                AqlWsContract.ACTION_DEVICE_IDENTITY_GET,
                AqlWsContract.ACTION_DEVICE_CAPABILITIES_GET
            )

    private fun retire(deviceUid: DeviceUid) {
        bootstrappedGenerations.remove(deviceUid)
        completePending(deviceUid, "device retired")
        synchronized(refreshLock) {
            bootstrapJobs.remove(deviceUid)?.cancel()
            refreshJobs.remove(deviceUid)?.cancel()
            queuedRefreshTargets.remove(deviceUid)
        }
        stateStore.retire(deviceUid)
    }

    private fun completePending(deviceUid: DeviceUid, reason: String) {
        pendingRequests.entries
            .filter { (_, pending) -> pending.deviceUid == deviceUid }
            .forEach { (id, pending) ->
                if (pendingRequests.remove(id, pending)) {
                    pending.deferred.complete(
                        DeviceRuntimeCommandOutcome.Cancelled(
                            deviceUid = pending.deviceUid,
                            module = pending.module,
                            action = pending.action,
                            messageId = id,
                            reason = reason
                        )
                    )
                }
            }
    }

    private fun completeAllPending(reason: String) {
        pendingRequests.entries.forEach { (id, pending) ->
            if (pendingRequests.remove(id, pending)) {
                pending.deferred.complete(
                    DeviceRuntimeCommandOutcome.Cancelled(
                        deviceUid = pending.deviceUid,
                        module = pending.module,
                        action = pending.action,
                        messageId = id,
                        reason = reason
                    )
                )
            }
        }
    }

    private fun cancelBackgroundJobs() {
        synchronized(refreshLock) {
            bootstrapJobs.values.forEach { it.cancel() }
            refreshJobs.values.forEach { it.cancel() }
            bootstrapJobs.clear()
            refreshJobs.clear()
            queuedRefreshTargets.clear()
        }
    }

    override fun close() {
        val job = synchronized(lifecycleLock) {
            startJob.also { startJob = null }
        }
        job?.cancel()
        activeScope = null
        completeAllPending("runtime data repository closed")
        cancelBackgroundJobs()
        bootstrappedGenerations.clear()
        stateStore.clear()
    }

    companion object {
        const val DEFAULT_COMMAND_TIMEOUT_MS = 8_000L
        private const val MIN_COMMAND_TIMEOUT_MS = 1_000L
        private const val MAX_COMMAND_TIMEOUT_MS = 30_000L
        private const val STATUS_REFRESH_DEBOUNCE_MS = 120L
    }
}
