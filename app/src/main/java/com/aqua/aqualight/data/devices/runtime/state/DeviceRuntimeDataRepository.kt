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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

@Suppress("TooManyFunctions")
class DeviceRuntimeDataRepository(
    private val devicesRepository: DevicesRepository,
    private val stateStore: DeviceRuntimeStateStore = DeviceRuntimeStateStore(),
    private val commandTimeoutMillis: Long = DEFAULT_COMMAND_TIMEOUT_MS
) : AutoCloseable {

    private data class PendingRequest(
        val deviceUid: DeviceUid,
        val module: String,
        val action: String,
        val deferred: CompletableDeferred<AqlWsIncomingMessage>
    )

    private val lifecycleLock = Any()
    private val refreshLock = Any()
    private val pendingRequests = ConcurrentHashMap<String, PendingRequest>()
    private val bootstrappedGenerations = ConcurrentHashMap<DeviceUid, Long>()
    private val queuedRefreshTargets = mutableMapOf<DeviceUid, MutableSet<DeviceRuntimeRefreshTarget>>()
    private val refreshJobs = mutableMapOf<DeviceUid, Job>()

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
                        cancelPending("runtime data repository stopped")
                        cancelRefreshJobs()
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
            return DeviceRuntimeCommandOutcome.SendFailed(
                deviceUid = deviceUid,
                module = module,
                action = action,
                messageId = command.id
            )
        }

        val incoming = withTimeoutOrNull(timeoutMillis) {
            pending.deferred.await()
        }
        pendingRequests.remove(command.id, pending)

        return when (incoming) {
            null -> DeviceRuntimeCommandOutcome.Timeout(
                deviceUid = deviceUid,
                module = module,
                action = action,
                messageId = command.id,
                timeoutMillis = timeoutMillis
            )
            is AqlWsIncomingMessage.Response -> DeviceRuntimeCommandOutcome.Success(
                deviceUid = deviceUid,
                module = module,
                action = action,
                messageId = incoming.id,
                statusCode = incoming.statusCode,
                data = JSONObject(incoming.data.toString())
            )
            is AqlWsIncomingMessage.Error -> DeviceRuntimeCommandOutcome.FirmwareError(
                deviceUid = deviceUid,
                module = module,
                action = action,
                messageId = incoming.id,
                statusCode = incoming.statusCode,
                code = incoming.code,
                message = incoming.message,
                field = incoming.field
            )
            is AqlWsIncomingMessage.Event -> DeviceRuntimeCommandOutcome.Cancelled(
                deviceUid = deviceUid,
                module = module,
                action = action,
                messageId = command.id,
                reason = "Unexpected event used as a command response."
            )
        }
    }

    suspend fun refreshAll(deviceUid: DeviceUid): Map<DeviceRuntimeRefreshTarget, DeviceRuntimeCommandOutcome> {
        val snapshot = devicesRepository.currentDevice(deviceUid)
            ?: return emptyMap()
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
                is AqlWsEvent.Authenticated -> stateStore.markAuthenticated(event.deviceUid)
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
                .forEach { deviceUid ->
                    bootstrappedGenerations.remove(deviceUid)
                    stateStore.retire(deviceUid)
                }

            snapshots.values.forEach { snapshot ->
                if (!snapshot.hasValidatedRuntimeMetadata) return@forEach
                if (!stateStore.current(snapshot.deviceUid).authenticated) return@forEach
                val generation = snapshot.runtimeMetadataGeneration
                if (bootstrappedGenerations[snapshot.deviceUid] == generation) return@forEach

                bootstrappedGenerations[snapshot.deviceUid] = generation
                val targets = bootstrapTargets(snapshot)
                stateStore.beginBootstrap(snapshot.deviceUid, generation)
                activeScope?.launch {
                    refreshTargets(snapshot.deviceUid, targets)
                }
            }
        }
    }

    private fun onMessage(deviceUid: DeviceUid, message: AqlWsIncomingMessage) {
        if (message is AqlWsIncomingMessage.Response || message is AqlWsIncomingMessage.Error) {
            val pending = pendingRequests[message.id]
            if (pending != null && pending.deviceUid == deviceUid) {
                pendingRequests.remove(message.id, pending)
                pending.deferred.complete(message)
            }
        }

        val targets = stateStore.applyMessage(deviceUid, message)
        val shouldAutoRefresh = message is AqlWsIncomingMessage.Event ||
            message is AqlWsIncomingMessage.Response && !isReadCommand(message.module, message.action)
        if (shouldAutoRefresh && targets.isNotEmpty()) {
            scheduleRefresh(deviceUid, targets)
        }
    }

    private fun onUnavailable(deviceUid: DeviceUid, code: String, message: String) {
        stateStore.applyTransportFault(deviceUid, code, message)
        bootstrappedGenerations.remove(deviceUid)
        cancelPending(deviceUid, message)
        synchronized(refreshLock) {
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
            queuedRefreshTargets.getOrPut(deviceUid, ::linkedSetOf).addAll(targets)
            if (refreshJobs[deviceUid]?.isActive == true) return
            refreshJobs[deviceUid] = scope.launch {
                delay(STATUS_REFRESH_DEBOUNCE_MS)
                val pending = synchronized(refreshLock) {
                    queuedRefreshTargets.remove(deviceUid).orEmpty().toSet()
                }
                try {
                    refreshTargets(deviceUid, pending)
                } finally {
                    synchronized(refreshLock) {
                        refreshJobs.remove(deviceUid)
                        val more = queuedRefreshTargets[deviceUid].orEmpty().isNotEmpty()
                        if (more) scheduleRefresh(deviceUid, emptySet())
                    }
                }
            }
        }
    }

    private suspend fun refreshTargets(
        deviceUid: DeviceUid,
        targets: Set<DeviceRuntimeRefreshTarget>
    ): Map<DeviceRuntimeRefreshTarget, DeviceRuntimeCommandOutcome> = coroutineScope {
        targets.associateWith { target ->
            executeRefresh(deviceUid, target)
        }
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
        add(DeviceRuntimeRefreshTarget.NETWORK)
        add(DeviceRuntimeRefreshTarget.TIME)
        add(DeviceRuntimeRefreshTarget.FIRMWARE)

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

    private fun cancelPending(deviceUid: DeviceUid, reason: String) {
        pendingRequests.entries
            .filter { (_, pending) -> pending.deviceUid == deviceUid }
            .forEach { (id, pending) ->
                if (pendingRequests.remove(id, pending)) {
                    pending.deferred.cancel(reason)
                }
            }
    }

    private fun cancelPending(reason: String) {
        pendingRequests.entries.forEach { (id, pending) ->
            if (pendingRequests.remove(id, pending)) {
                pending.deferred.cancel(reason)
            }
        }
    }

    private fun cancelRefreshJobs() {
        synchronized(refreshLock) {
            refreshJobs.values.forEach(Job::cancel)
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
        cancelPending("runtime data repository closed")
        cancelRefreshJobs()
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
