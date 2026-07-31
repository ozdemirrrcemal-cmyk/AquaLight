package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class DeviceRuntimeDataRepository(
    private val devicesRepository: DevicesRepository,
    private val stateStore: DeviceRuntimeStateStore = DeviceRuntimeStateStore(),
    private val commandTimeoutMillis: Long = DEFAULT_COMMAND_TIMEOUT_MS
) : AutoCloseable {

    private val lifecycleLock = Any()

    @Volatile
    private var startJob: Job? = null

    @Volatile
    private var activeScope: CoroutineScope? = null

    private val commandExecutor = DeviceRuntimeCommandExecutor(
        devicesRepository = devicesRepository,
        stateStore = stateStore,
        isActive = { startJob?.isActive == true }
    )
    private val refreshCoordinator = DeviceRuntimeRefreshCoordinator(
        stateStore = stateStore,
        commandExecutor = commandExecutor,
        scopeProvider = { activeScope },
        timing = DeviceRuntimeRefreshTiming(commandTimeoutMillis)
    )

    val states: StateFlow<Map<DeviceUid, DeviceRuntimeState>> = stateStore.states

    init {
        require(
            commandTimeoutMillis in
                DeviceRuntimeCommandExecutor.MIN_COMMAND_TIMEOUT_MS..
                DeviceRuntimeCommandExecutor.MAX_COMMAND_TIMEOUT_MS
        ) {
            "commandTimeoutMillis is outside the commercial runtime range."
        }
    }

    fun observe(deviceUid: DeviceUid): Flow<DeviceRuntimeState> = stateStore.observe(deviceUid)

    fun current(deviceUid: DeviceUid): DeviceRuntimeState = stateStore.current(deviceUid)

    fun start(scope: CoroutineScope): Job = synchronized(lifecycleLock) {
        val current = startJob
        if (current?.isActive == true) {
            current
        } else {
            scope.launch {
                val repositoryScope = this
                activeScope = repositoryScope
                try {
                    coroutineScope {
                        launch { collectRuntimeEvents() }
                        launch { collectValidatedSnapshots() }
                        awaitCancellation()
                    }
                } finally {
                    if (activeScope === repositoryScope) {
                        activeScope = null
                    }
                    commandExecutor.cancelAll("runtime data repository stopped")
                    refreshCoordinator.close()
                }
            }.also { job ->
                startJob = job
                job.invokeOnCompletion {
                    synchronized(lifecycleLock) {
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
    ): DeviceRuntimeCommandOutcome = commandExecutor.execute(
        DeviceRuntimeCommandRequest(
            deviceUid = deviceUid,
            module = module,
            action = action,
            data = data,
            timeoutMillis = timeoutMillis
        )
    )

    suspend fun refreshAll(
        deviceUid: DeviceUid
    ): Map<DeviceRuntimeRefreshTarget, DeviceRuntimeCommandOutcome> =
        devicesRepository.currentDevice(deviceUid)?.let { snapshot ->
            refreshCoordinator.refreshTargets(
                deviceUid,
                DeviceRuntimeRefreshCatalog.bootstrapTargets(snapshot)
            )
        }.orEmpty()

    suspend fun refresh(
        deviceUid: DeviceUid,
        target: DeviceRuntimeRefreshTarget
    ): DeviceRuntimeCommandOutcome = refreshCoordinator.refreshOne(deviceUid, target)

    private suspend fun collectRuntimeEvents() {
        devicesRepository.runtimeEvents()?.collect { event ->
            when (event) {
                is AqlWsEvent.Authenticated -> {
                    stateStore.markAuthenticated(event.deviceUid)
                    devicesRepository.currentDevice(event.deviceUid)?.let(
                        refreshCoordinator::maybeBootstrap
                    )
                }
                is AqlWsEvent.Message -> {
                    val message = event.parsed
                    val targets = stateStore.applyMessage(event.deviceUid, message)
                    commandExecutor.completeCorrelated(event.deviceUid, message)
                    val shouldRefresh = message is AqlWsIncomingMessage.Event ||
                        message is AqlWsIncomingMessage.Response &&
                        !DeviceRuntimeRefreshCatalog.isReadCommand(
                            message.module,
                            message.action
                        )
                    if (shouldRefresh) {
                        refreshCoordinator.schedule(event.deviceUid, targets)
                    }
                }
                is AqlWsEvent.Closed -> handleUnavailable(
                    event.deviceUid,
                    "socket_closed_${event.code}",
                    event.reason
                )
                is AqlWsEvent.Failure -> handleUnavailable(
                    event.deviceUid,
                    "socket_failure",
                    event.message
                )
                is AqlWsEvent.Opened -> Unit
            }
        }
    }

    private suspend fun collectValidatedSnapshots() {
        devicesRepository.snapshots.collect(refreshCoordinator::reconcile)
    }

    private fun handleUnavailable(
        deviceUid: DeviceUid,
        code: String,
        message: String
    ) {
        stateStore.applyTransportFault(deviceUid, code, message)
        refreshCoordinator.onUnavailable(deviceUid, message)
    }

    override fun close() {
        val job = synchronized(lifecycleLock) {
            startJob.also { startJob = null }
        }
        job?.cancel()
        activeScope = null
        commandExecutor.cancelAll("runtime data repository closed")
        refreshCoordinator.close()
        stateStore.clear()
    }

    companion object {
        const val DEFAULT_COMMAND_TIMEOUT_MS = 8_000L
    }
}
