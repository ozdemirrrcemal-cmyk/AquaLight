package com.aqua.aqualight.data.devices.repository

import android.content.Context
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogValidation
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFailureCode
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGeneration
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGenerationState
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataReduction
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandExecutor
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandSession
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCompletionDisposition
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeLifecycleEvent
import com.aqua.aqualight.data.devices.runtime.modules.DeviceRuntimeModuleProvider
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeAccess
import com.aqua.aqualight.data.devices.runtime.modules.time.DeviceTimeSyncCoordinator
import com.aqua.aqualight.data.devices.runtime.ws.AqlPrivateLanEndpoint
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsClient
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTokenProvider
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTransport
import com.aqua.aqualight.data.devices.store.DeviceCredentialStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal sealed interface DeviceRuntimeMetadataUpdate {
    data object Unmatched : DeviceRuntimeMetadataUpdate
    data class Collecting(
        val state: DeviceRuntimeMetadataGenerationState.Collecting
    ) : DeviceRuntimeMetadataUpdate
    data class Ready(
        val state: DeviceRuntimeMetadataGenerationState.Ready
    ) : DeviceRuntimeMetadataUpdate
    data class Rejected(
        val state: DeviceRuntimeMetadataGenerationState.Rejected
    ) : DeviceRuntimeMetadataUpdate
}

/**
 * Owner-scoped runtime session orchestrator.
 *
 * The public and private operations intentionally remain together because session ownership,
 * authenticated bootstrap correlation, token lifecycle and socket teardown share one lock domain.
 */
@Suppress("TooManyFunctions")
class DeviceRuntimeRepository(
    private val tokenProvider: AqlWsTokenProvider? = null,
    private val wsClientFactory: (AqlWsTokenProvider?) -> AqlWsTransport = { provider ->
        AqlWsClient(tokenProvider = provider)
    },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AutoCloseable, DeviceRuntimeCommandGateway {

    private class RuntimeSession(
        val deviceUid: DeviceUid,
        val wsClient: AqlWsTransport,
        val sessionJob: CompletableJob,
        val sessionScope: CoroutineScope,
        @Volatile var generation: DeviceRuntimeConnectionGeneration
    ) : AutoCloseable {
        @Volatile
        var connectionStarted: Boolean = false

        @Volatile
        var endpointUrl: String? = null

        @Volatile
        var endpointAddressBytes: ByteArray? = null

        private val closed = AtomicBoolean(false)
        private val collectorJobs = CopyOnWriteArrayList<Job>()

        fun track(job: Job) {
            if (closed.get()) {
                job.cancel()
                return
            }
            collectorJobs += job
            if (closed.get() && collectorJobs.remove(job)) job.cancel()
        }

        override fun close() {
            val jobs = synchronized(this) { beginCloseLocked() } ?: return
            jobs.forEach(Job::cancel)
            sessionJob.cancel()
            wsClient.close()
        }

        suspend fun shutdown() {
            val jobs = synchronized(this) { beginCloseLocked() } ?: return
            jobs.forEach(Job::cancel)
            sessionJob.cancel()
            try {
                wsClient.shutdown()
            } finally {
                jobs.joinAll()
                sessionJob.join()
            }
        }

        private fun beginCloseLocked(): List<Job>? {
            if (!closed.compareAndSet(false, true)) return null
            return collectorJobs.toList().also { collectorJobs.clear() }
        }
    }

    private val lifecycleLock = Any()
    private val repositoryJob = SupervisorJob()
    private val repositoryScope = CoroutineScope(repositoryJob + dispatcher)
    private val tokenLifecycleMutex = Mutex()
    private val sessions = ConcurrentHashMap<DeviceUid, RuntimeSession>()
    private val retiredDeviceUids = ConcurrentHashMap.newKeySet<DeviceUid>()
    private val metadataTimeoutJobs = ConcurrentHashMap<DeviceUid, Job>()
    private val runtimeGenerationCounter = AtomicLong(0L)

    internal val metadataBootstrapCoordinator = DeviceRuntimeMetadataBootstrapCoordinator()
    private val domainBootstrapCoordinator = DeviceRuntimeDomainBootstrapCoordinator()

    private val commandExecutor = DeviceRuntimeCommandExecutor(
        sessionProvider = ::currentCommandSession,
        supportChecker = ::supportsCommand
    )

    val runtimeModules: DeviceRuntimeModuleProvider = DeviceRuntimeModuleProvider(
        commandGateway = this,
        revokeLocalCredential = ::revokeLocalCredentialAndSession,
        timerAccessProvider = ::currentTimerRuntimeAccess
    )

    private val timeSyncCoordinator = DeviceTimeSyncCoordinator(
        requestStatus = { deviceUid, generation ->
            executeIfCurrentAuthenticatedGeneration(deviceUid, generation) {
                runtimeModules.time.requestStatus(deviceUid)
            }
        },
        syncPhoneNow = { deviceUid, generation ->
            executeIfCurrentAuthenticatedGeneration(deviceUid, generation) {
                runtimeModules.time.syncPhoneNow(deviceUid = deviceUid, save = false)
            }
        },
        currentConnectionGeneration = ::currentConnectionGeneration
    )

    private val _connectionState = MutableSharedFlow<AqlWsConnectionState>(
        extraBufferCapacity = EVENT_BUFFER_CAPACITY
    )
    val connectionState: SharedFlow<AqlWsConnectionState> = _connectionState.asSharedFlow()

    private val _events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val events: SharedFlow<AqlWsEvent> = _events.asSharedFlow()

    private val _lifecycleEvents = MutableSharedFlow<DeviceRuntimeLifecycleEvent>(
        extraBufferCapacity = EVENT_BUFFER_CAPACITY
    )
    val lifecycleEvents: SharedFlow<DeviceRuntimeLifecycleEvent> =
        _lifecycleEvents.asSharedFlow()

    private val _runtimeReadiness = MutableStateFlow<Map<DeviceUid, DeviceRuntimeSessionReadiness>>(
        emptyMap()
    )
    internal val runtimeReadiness: StateFlow<Map<DeviceUid, DeviceRuntimeSessionReadiness>> =
        _runtimeReadiness.asStateFlow()

    @Volatile
    private var closed: Boolean = false

    fun connect(snapshot: DeviceSnapshot): Result<Unit> {
        val deviceUid = snapshot.deviceUid
        val session = runCatching {
            synchronized(lifecycleLock) {
                check(!closed) { "Device runtime repository is closed." }
                check(deviceUid !in retiredDeviceUids) {
                    "Device runtime is retired and cannot be reopened without explicit registration."
                }
                sessions[deviceUid] ?: createSession(deviceUid).also { created ->
                    sessions[deviceUid] = created
                    observeSession(created)
                }
            }
        }.getOrElse { return Result.failure(it) }

        return synchronized(session) {
            if (!isCurrentSession(session)) {
                return@synchronized Result.failure(
                    IllegalStateException("Device runtime session is no longer active.")
                )
            }
            val endpointRoute = AqlPrivateLanEndpoint.route(deviceUid, snapshot.endpoint)
            val endpointUrl = endpointRoute?.url
            val endpointAddressMatches = when (val currentAddress = session.endpointAddressBytes) {
                null -> endpointRoute?.addressBytes == null
                else -> endpointRoute?.addressBytes?.let { currentAddress.contentEquals(it) } == true
            }
            val endpointMatches = session.endpointUrl == endpointUrl && endpointAddressMatches
            val currentState = session.wsClient.connectionState.value
            if (!RuntimeConnectionReusePolicy.shouldReconnect(currentState, deviceUid, endpointMatches)) {
                return@synchronized ensureCurrentMetadataBootstrap(
                    session = session,
                    snapshot = snapshot,
                    connectionState = currentState
                )
            }

            if (session.connectionStarted) {
                val previousGeneration = session.generation
                commandExecutor.cancelGeneration(
                    deviceUid = deviceUid,
                    generation = previousGeneration,
                    reason = COMMAND_CANCELLED_CONNECTION_REPLACED
                )
                domainBootstrapCoordinator.clear(deviceUid)
                clearRuntimeReadiness(deviceUid)
                session.generation = nextRuntimeGeneration()
            } else {
                session.connectionStarted = true
            }
            session.endpointUrl = endpointUrl
            session.endpointAddressBytes = endpointRoute?.addressBytes?.copyOf()
            session.wsClient.connect(deviceUid = deviceUid, endpoint = snapshot.endpoint)
        }
    }

    fun reconnectAfterNetworkRestore(snapshot: DeviceSnapshot): Result<Unit> {
        val detached = synchronized(lifecycleLock) {
            if (closed || snapshot.deviceUid in retiredDeviceUids) return@synchronized null
            sessions.remove(snapshot.deviceUid)
        }
        cancelMetadataTimeout(snapshot.deviceUid)
        detached?.let { session ->
            commandExecutor.cancelGeneration(
                deviceUid = session.deviceUid,
                generation = session.generation,
                reason = COMMAND_CANCELLED_NETWORK_ROUTE_CHANGED
            )
            session.close()
        }
        metadataBootstrapCoordinator.clear(snapshot.deviceUid)
        domainBootstrapCoordinator.clear(snapshot.deviceUid)
        clearRuntimeReadiness(snapshot.deviceUid)
        return connect(snapshot)
    }

    fun activate(deviceUid: DeviceUid) {
        synchronized(lifecycleLock) {
            check(!closed) { "Device runtime repository is closed." }
            retiredDeviceUids.remove(deviceUid)
        }
    }

    fun currentConnectionState(deviceUid: DeviceUid): AqlWsConnectionState? =
        sessions[deviceUid]?.wsClient?.connectionState?.value

    internal fun currentConnectionGeneration(
        deviceUid: DeviceUid
    ): DeviceRuntimeConnectionGeneration? = sessions[deviceUid]
        ?.takeIf(::isCurrentSession)
        ?.generation

    /**
     * Commits a proof only while the authenticated session that produced it is still current.
     *
     * Lock order matches connect(): session first, lifecycle second. Holding both locks through
     * [action] prevents a route replacement, retirement or generation change between validation
     * and the canonical proof write.
     */
    internal fun runIfCurrentAuthenticatedGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        action: () -> Unit
    ): Boolean {
        val session = sessions[deviceUid] ?: return false
        return synchronized(session) {
            synchronized(lifecycleLock) {
                val currentSession = !closed &&
                    deviceUid !in retiredDeviceUids &&
                    sessions[deviceUid] === session
                val currentGeneration = session.generation == generation
                val authenticated =
                    session.wsClient.connectionState.value is AqlWsConnectionState.Authenticated
                if (currentSession && currentGeneration && authenticated) {
                    action()
                    true
                } else {
                    false
                }
            }
        }
    }

    /**
     * Starts a runtime command only while [generation] is still the current authenticated session.
     * UNDISPATCHED execution reaches command dispatch while the same lifecycle locks are held, so
     * a reconnect cannot move an old status decision onto a replacement socket generation.
     */
    private suspend fun <T> executeIfCurrentAuthenticatedGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        operation: suspend () -> DeviceRuntimeCommandOutcome<T>
    ): DeviceRuntimeCommandOutcome<T>? {
        var result: Deferred<DeviceRuntimeCommandOutcome<T>>? = null
        val started = runIfCurrentAuthenticatedGeneration(deviceUid, generation) {
            result = repositoryScope.async(start = CoroutineStart.UNDISPATCHED) {
                operation()
            }
        }
        return if (started) checkNotNull(result).await() else null
    }

    internal fun pendingCommandCount(): Int = commandExecutor.pendingCount()

    override suspend fun <T> execute(
        deviceUid: DeviceUid,
        command: DeviceRuntimeCommand<T>,
        timeoutMillis: Long
    ): DeviceRuntimeCommandOutcome<T> = commandExecutor.execute(
        deviceUid = deviceUid,
        command = command,
        timeoutMillis = timeoutMillis
    )

    suspend fun <T> executeCommand(
        deviceUid: DeviceUid,
        command: DeviceRuntimeCommand<T>,
        timeoutMillis: Long = DeviceRuntimeCommandExecutor.DEFAULT_TIMEOUT_MILLIS
    ): DeviceRuntimeCommandOutcome<T> = execute(deviceUid, command, timeoutMillis)

    internal fun isCurrentValidatedMetadata(snapshot: DeviceSnapshot): Boolean {
        val ready = metadataBootstrapCoordinator.currentState(snapshot.deviceUid) as?
            DeviceRuntimeMetadataGenerationState.Ready
        return snapshot.hasValidatedRuntimeMetadata &&
            ready?.generation?.value == snapshot.runtimeMetadataGeneration
    }

    internal fun processMetadataResponse(
        deviceUid: DeviceUid,
        response: AqlWsIncomingMessage.Response
    ): DeviceRuntimeMetadataUpdate {
        return when (val processing = metadataBootstrapCoordinator.process(deviceUid, response)) {
            DeviceRuntimeMetadataBootstrapProcessing.Unmatched -> DeviceRuntimeMetadataUpdate.Unmatched
            is DeviceRuntimeMetadataBootstrapProcessing.Reduced ->
                mapMetadataReduction(deviceUid, processing.reduction)
        }
    }

    private fun ensureCurrentMetadataBootstrap(
        session: RuntimeSession,
        snapshot: DeviceSnapshot,
        connectionState: AqlWsConnectionState
    ): Result<Unit> {
        val metadataState = metadataBootstrapCoordinator.currentState(snapshot.deviceUid)
        return when {
            isCurrentValidatedMetadata(snapshot) -> Result.success(Unit)
            metadataState is DeviceRuntimeMetadataGenerationState.Collecting -> Result.success(Unit)
            connectionState !is AqlWsConnectionState.Authenticated -> Result.success(Unit)
            sendAuthenticatedBootstrap(session) -> Result.success(Unit)
            else -> Result.failure(
                IllegalStateException("Authenticated metadata bootstrap could not be restarted.")
            )
        }
    }

    fun disconnectForLocalNetworkLoss() {
        val activeSessions = synchronized(lifecycleLock) {
            if (closed) return
            sessions.values.toList()
        }
        activeSessions.forEach { session ->
            rejectActiveGeneration(session.deviceUid, "localNetwork")
            rejectRuntimeReadiness(session.deviceUid, session.generation, "localNetwork")
            domainBootstrapCoordinator.clear(session.deviceUid)
            commandExecutor.cancelGeneration(
                deviceUid = session.deviceUid,
                generation = session.generation,
                reason = COMMAND_CANCELLED_LOCAL_NETWORK_LOSS
            )
            synchronized(session) {
                session.wsClient.disconnect(reason = LOCAL_NETWORK_UNAVAILABLE_REASON)
            }
        }
    }

    suspend fun saveToken(deviceUid: DeviceUid, token: String) {
        val provider = tokenProvider ?: return
        tokenLifecycleMutex.withLock {
            ensureOpen()
            provider.saveToken(deviceUid = deviceUid, token = token)
            ensureOpen()
        }
    }

    suspend fun clearToken(deviceUid: DeviceUid) {
        val provider = tokenProvider ?: return
        tokenLifecycleMutex.withLock {
            ensureOpen()
            provider.clearToken(deviceUid)
            ensureOpen()
        }
    }

    private suspend fun revokeLocalCredentialAndSession(deviceUid: DeviceUid): Result<Unit> {
        val session = detachSessionWithoutRetirement(deviceUid)
        commandExecutor.cancelDevice(deviceUid, COMMAND_CANCELLED_CREDENTIAL_REVOKED)
        cancelMetadataTimeout(deviceUid)
        metadataBootstrapCoordinator.clear(deviceUid)
        domainBootstrapCoordinator.clear(deviceUid)
        clearRuntimeReadiness(deviceUid)
        timeSyncCoordinator.clearSessionMemory(deviceUid)

        val tokenFailure = runCatching { clearToken(deviceUid) }.exceptionOrNull()
        val sessionFailure = runCatching { session?.shutdown() }.exceptionOrNull()
        return if (tokenFailure == null && sessionFailure == null) {
            Result.success(Unit)
        } else {
            Result.failure(localCredentialTeardownFailure(tokenFailure, sessionFailure))
        }
    }

    private fun localCredentialTeardownFailure(
        tokenFailure: Throwable?,
        sessionFailure: Throwable?
    ): Throwable {
        val primary = tokenFailure ?: checkNotNull(sessionFailure)
        return IllegalStateException(
            "Device credential was revoked, but local credential/session teardown failed.",
            primary
        ).also { failure ->
            if (sessionFailure != null && sessionFailure !== primary) {
                failure.addSuppressed(sessionFailure)
            }
        }
    }

    suspend fun retire(deviceUid: DeviceUid) {
        val session = detachSessionForRetirement(deviceUid)
        commandExecutor.cancelDevice(deviceUid, COMMAND_CANCELLED_DEVICE_RETIRED)
        cancelMetadataTimeout(deviceUid)
        metadataBootstrapCoordinator.clear(deviceUid)
        domainBootstrapCoordinator.clear(deviceUid)
        clearRuntimeReadiness(deviceUid)
        timeSyncCoordinator.clearSessionMemory(deviceUid)
        session?.shutdown()
    }

    fun close(deviceUid: DeviceUid) {
        val session = detachSessionForRetirement(deviceUid)
        commandExecutor.cancelDevice(deviceUid, COMMAND_CANCELLED_DEVICE_CLOSED)
        cancelMetadataTimeout(deviceUid)
        metadataBootstrapCoordinator.clear(deviceUid)
        domainBootstrapCoordinator.clear(deviceUid)
        clearRuntimeReadiness(deviceUid)
        timeSyncCoordinator.clearSessionMemory(deviceUid)
        session?.close()
    }

    override fun close() {
        val activeSessions = beginRepositoryClose() ?: return
        commandExecutor.cancelAll(COMMAND_CANCELLED_REPOSITORY_CLOSED)
        cancelAllMetadataTimeouts()
        metadataBootstrapCoordinator.clearAll()
        domainBootstrapCoordinator.clearAll()
        _runtimeReadiness.value = emptyMap()
        repositoryJob.cancel()
        activeSessions.forEach { session ->
            timeSyncCoordinator.clearSessionMemory(session.deviceUid)
            session.close()
        }
    }

    suspend fun shutdown() {
        val activeSessions = beginRepositoryClose()
        commandExecutor.cancelAll(COMMAND_CANCELLED_REPOSITORY_SHUTDOWN)
        cancelAllMetadataTimeouts()
        metadataBootstrapCoordinator.clearAll()
        domainBootstrapCoordinator.clearAll()
        _runtimeReadiness.value = emptyMap()
        repositoryJob.cancel()
        activeSessions?.forEach { session ->
            timeSyncCoordinator.clearSessionMemory(session.deviceUid)
            session.shutdown()
        }
        tokenLifecycleMutex.withLock { }
        repositoryJob.join()
    }

    private fun mapMetadataReduction(
        deviceUid: DeviceUid,
        reduction: DeviceRuntimeMetadataReduction
    ): DeviceRuntimeMetadataUpdate = when (reduction) {
        is DeviceRuntimeMetadataReduction.IgnoredStale -> DeviceRuntimeMetadataUpdate.Unmatched
        is DeviceRuntimeMetadataReduction.Rejected -> {
            cancelMetadataTimeout(deviceUid)
            currentConnectionGeneration(deviceUid)?.let { generation ->
                rejectRuntimeReadiness(deviceUid, generation, "metadataRejected")
            }
            domainBootstrapCoordinator.clear(deviceUid)
            disconnectMetadataFailure(deviceUid)
            DeviceRuntimeMetadataUpdate.Rejected(reduction.state)
        }
        is DeviceRuntimeMetadataReduction.Accepted -> when (val state = reduction.state) {
            is DeviceRuntimeMetadataGenerationState.Collecting ->
                DeviceRuntimeMetadataUpdate.Collecting(state)
            is DeviceRuntimeMetadataGenerationState.Ready -> {
                cancelMetadataTimeout(deviceUid)
                validateReadyState(state)
            }
            is DeviceRuntimeMetadataGenerationState.Rejected -> {
                cancelMetadataTimeout(deviceUid)
                currentConnectionGeneration(deviceUid)?.let { generation ->
                    rejectRuntimeReadiness(deviceUid, generation, "metadataRejected")
                }
                domainBootstrapCoordinator.clear(deviceUid)
                disconnectMetadataFailure(deviceUid)
                DeviceRuntimeMetadataUpdate.Rejected(state)
            }
        }
    }

    private fun validateReadyState(
        state: DeviceRuntimeMetadataGenerationState.Ready
    ): DeviceRuntimeMetadataUpdate {
        return when (val validation = AqlCommercialDeviceCatalog.validate(state.metadata)) {
            is AqlCommercialCatalogValidation.Valid -> {
                val connectionGeneration = currentConnectionGeneration(state.deviceUid)
                if (connectionGeneration != null) {
                    val plan = DeviceRuntimeDomainBootstrapPlanResolver.resolve(
                        deviceUid = state.deviceUid,
                        connectionGeneration = connectionGeneration,
                        metadataGeneration = state.generation,
                        product = validation.product
                    )
                    setRuntimeReadiness(
                        DeviceRuntimeSessionReadiness.MetadataReady(
                            deviceUid = state.deviceUid,
                            connectionGeneration = connectionGeneration,
                            metadataGeneration = state.generation
                        )
                    )
                    repositoryScope.launch { runDomainBootstrap(plan) }
                }
                DeviceRuntimeMetadataUpdate.Ready(state)
            }
            is AqlCommercialCatalogValidation.Invalid -> {
                val rejected = checkNotNull(
                    metadataBootstrapCoordinator.reject(
                        deviceUid = state.deviceUid,
                        generation = state.generation,
                        code = DeviceRuntimeMetadataFailureCode.CATALOG_VALIDATION_FAILED,
                        field = "${validation.failure.code}:${validation.failure.field}"
                    )
                )
                currentConnectionGeneration(state.deviceUid)?.let { generation ->
                    rejectRuntimeReadiness(state.deviceUid, generation, "catalogValidationFailed")
                }
                domainBootstrapCoordinator.clear(state.deviceUid)
                disconnectMetadataFailure(state.deviceUid)
                DeviceRuntimeMetadataUpdate.Rejected(rejected)
            }
        }
    }

    private suspend fun runDomainBootstrap(plan: DeviceRuntimeDomainBootstrapPlan) {
        val accepted = runIfCurrentAuthenticatedGeneration(
            deviceUid = plan.deviceUid,
            generation = plan.connectionGeneration
        ) {
            setRuntimeReadiness(DeviceRuntimeSessionReadiness.DomainBootstrapping(plan))
        }
        if (!accepted) return

        when (
            val result = domainBootstrapCoordinator.run(plan) { step ->
                executeDomainBootstrapStepWithRetry(plan, step)
            }
        ) {
            is DeviceRuntimeDomainBootstrapResult.Completed -> {
                val stillCurrent = runIfCurrentAuthenticatedGeneration(
                    deviceUid = plan.deviceUid,
                    generation = plan.connectionGeneration
                ) {
                    setRuntimeReadiness(DeviceRuntimeSessionReadiness.RuntimeReady(plan))
                    if (DeviceRuntimeDomainBootstrapStep.DOSING_STATUS in plan.steps) {
                        runtimeModules.dosing.markRuntimeReady(
                            deviceUid = plan.deviceUid,
                            generation = plan.connectionGeneration
                        )
                    }
                }
                if (stillCurrent) {
                    timeSyncCoordinator.syncPhoneNowIfNeeded(
                        deviceUid = plan.deviceUid,
                        generation = plan.connectionGeneration
                    )
                }
            }
            is DeviceRuntimeDomainBootstrapResult.Failed -> {
                rejectDomainBootstrap(result)
            }
            is DeviceRuntimeDomainBootstrapResult.Stale,
            is DeviceRuntimeDomainBootstrapResult.AlreadyStarted -> Unit
        }
    }

    private suspend fun executeDomainBootstrapStepWithRetry(
        plan: DeviceRuntimeDomainBootstrapPlan,
        step: DeviceRuntimeDomainBootstrapStep
    ): DeviceRuntimeCommandOutcome<*>? {
        var outcome = executeDomainBootstrapStep(plan, step)
        if (outcome is DeviceRuntimeCommandOutcome.Timeout ||
            outcome is DeviceRuntimeCommandOutcome.SendFailed
        ) {
            delay(DOMAIN_BOOTSTRAP_RETRY_DELAY_MILLIS)
            outcome = executeDomainBootstrapStep(plan, step)
        }
        return outcome
    }

    private suspend fun executeDomainBootstrapStep(
        plan: DeviceRuntimeDomainBootstrapPlan,
        step: DeviceRuntimeDomainBootstrapStep
    ): DeviceRuntimeCommandOutcome<*>? = when (step) {
        DeviceRuntimeDomainBootstrapStep.LIGHT_STATUS ->
            executeIfCurrentAuthenticatedGeneration(
                plan.deviceUid,
                plan.connectionGeneration
            ) { runtimeModules.light.requestStatus(plan.deviceUid) }
        DeviceRuntimeDomainBootstrapStep.LIGHT_THERMAL_STATUS ->
            executeIfCurrentAuthenticatedGeneration(
                plan.deviceUid,
                plan.connectionGeneration
            ) { runtimeModules.lightThermal.requestStatus(plan.deviceUid) }
        DeviceRuntimeDomainBootstrapStep.COOLING_STATUS ->
            executeIfCurrentAuthenticatedGeneration(
                plan.deviceUid,
                plan.connectionGeneration
            ) { runtimeModules.cooling.requestStatus(plan.deviceUid) }
        DeviceRuntimeDomainBootstrapStep.TIMER_STATUS ->
            executeIfCurrentAuthenticatedGeneration(
                plan.deviceUid,
                plan.connectionGeneration
            ) { runtimeModules.timer.requestStatus(plan.deviceUid) }
        DeviceRuntimeDomainBootstrapStep.DOSING_STATUS ->
            executeIfCurrentAuthenticatedGeneration(
                plan.deviceUid,
                plan.connectionGeneration
            ) { runtimeModules.dosing.requestStatus(plan.deviceUid) }
    }

    private fun rejectDomainBootstrap(result: DeviceRuntimeDomainBootstrapResult.Failed) {
        val plan = result.plan
        val reason = "${result.step}:${result.outcome.javaClass.simpleName}"
        val current = runIfCurrentAuthenticatedGeneration(
            deviceUid = plan.deviceUid,
            generation = plan.connectionGeneration
        ) {
            rejectRuntimeReadiness(plan.deviceUid, plan.connectionGeneration, reason)
        }
        if (!current) return
        disconnectDomainBootstrapFailure(plan.deviceUid, plan.connectionGeneration)
    }

    private fun disconnectDomainBootstrapFailure(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ) {
        val session = sessions[deviceUid] ?: return
        synchronized(session) {
            if (!isCurrentSession(session) || session.generation != generation) return
            if (session.wsClient.connectionState.value !is AqlWsConnectionState.Authenticated) return
            commandExecutor.cancelGeneration(
                deviceUid = deviceUid,
                generation = generation,
                reason = COMMAND_CANCELLED_DOMAIN_BOOTSTRAP_FAILURE
            )
            session.wsClient.disconnect(reason = DOMAIN_BOOTSTRAP_FAILED_REASON)
        }
    }

    private fun setRuntimeReadiness(readiness: DeviceRuntimeSessionReadiness) {
        _runtimeReadiness.update { current -> current + (readiness.deviceUid to readiness) }
    }

    private fun rejectRuntimeReadiness(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        reason: String
    ) {
        setRuntimeReadiness(
            DeviceRuntimeSessionReadiness.Rejected(
                deviceUid = deviceUid,
                connectionGeneration = generation,
                reason = reason
            )
        )
    }

    private fun clearRuntimeReadiness(deviceUid: DeviceUid) {
        _runtimeReadiness.update { current -> current - deviceUid }
    }

    private fun disconnectMetadataFailure(deviceUid: DeviceUid) {
        val session = sessions[deviceUid] ?: return
        commandExecutor.cancelGeneration(
            deviceUid = deviceUid,
            generation = session.generation,
            reason = COMMAND_CANCELLED_METADATA_FAILURE
        )
        synchronized(session) {
            if (isCurrentSession(session)) {
                session.wsClient.disconnect(reason = METADATA_BOOTSTRAP_FAILED_REASON)
            }
        }
    }

    private fun rejectActiveGeneration(deviceUid: DeviceUid, field: String) {
        cancelMetadataTimeout(deviceUid)
        val state = metadataBootstrapCoordinator.currentState(deviceUid) ?: return
        if (state is DeviceRuntimeMetadataGenerationState.Rejected) return
        metadataBootstrapCoordinator.reject(
            deviceUid = deviceUid,
            generation = state.generation,
            code = DeviceRuntimeMetadataFailureCode.RUNTIME_UNAVAILABLE,
            field = field
        )
    }

    private fun scheduleMetadataTimeout(
        session: RuntimeSession,
        generation: DeviceRuntimeMetadataGeneration
    ) {
        cancelMetadataTimeout(session.deviceUid)
        val timeoutJob = session.sessionScope.launch {
            delay(METADATA_BOOTSTRAP_TIMEOUT_MILLIS)
            val rejected = metadataBootstrapCoordinator.expire(
                deviceUid = session.deviceUid,
                generation = generation
            )
            if (rejected != null) {
                rejectRuntimeReadiness(session.deviceUid, session.generation, "metadataTimeout")
                domainBootstrapCoordinator.clear(session.deviceUid)
                disconnectMetadataFailure(session.deviceUid)
            }
        }
        metadataTimeoutJobs[session.deviceUid] = timeoutJob
        timeoutJob.invokeOnCompletion {
            metadataTimeoutJobs.remove(session.deviceUid, timeoutJob)
        }
    }

    private fun cancelMetadataTimeout(deviceUid: DeviceUid) {
        metadataTimeoutJobs.remove(deviceUid)?.cancel()
    }

    private fun cancelAllMetadataTimeouts() {
        metadataTimeoutJobs.values.forEach(Job::cancel)
        metadataTimeoutJobs.clear()
    }

    private fun detachSessionForRetirement(deviceUid: DeviceUid): RuntimeSession? =
        synchronized(lifecycleLock) {
            if (closed) return@synchronized null
            retiredDeviceUids.add(deviceUid)
            sessions.remove(deviceUid)
        }

    private fun detachSessionWithoutRetirement(deviceUid: DeviceUid): RuntimeSession? =
        synchronized(lifecycleLock) {
            if (closed) return@synchronized null
            sessions.remove(deviceUid)
        }

    private fun beginRepositoryClose(): List<RuntimeSession>? = synchronized(lifecycleLock) {
        if (closed) return@synchronized null
        closed = true
        retiredDeviceUids.clear()
        sessions.values.toList().also { sessions.clear() }
    }

    private fun createSession(deviceUid: DeviceUid): RuntimeSession {
        val wsClient = wsClientFactory(tokenProvider)
        val sessionJob = SupervisorJob(repositoryJob)
        return RuntimeSession(
            deviceUid = deviceUid,
            wsClient = wsClient,
            sessionJob = sessionJob,
            sessionScope = CoroutineScope(sessionJob + dispatcher),
            generation = nextRuntimeGeneration()
        )
    }

    private fun observeSession(session: RuntimeSession) {
        session.track(
            session.sessionScope.launch(start = CoroutineStart.UNDISPATCHED) {
                session.wsClient.connectionState.collect { state ->
                    if (!isCurrentSession(session)) return@collect
                    if (state.isTerminalForPendingCommands()) {
                        commandExecutor.cancelGeneration(
                            deviceUid = session.deviceUid,
                            generation = session.generation,
                            reason = COMMAND_CANCELLED_TRANSPORT_UNAVAILABLE
                        )
                    }
                    _connectionState.emit(state)
                }
            }
        )
        session.track(
            session.sessionScope.launch(start = CoroutineStart.UNDISPATCHED) {
                session.wsClient.events.collect { event ->
                    if (!isCurrentSession(session)) return@collect
                    val shouldPublish = handleAuthLifecycle(session, event)
                    if (shouldPublish && isCurrentSession(session)) {
                        _events.emit(event)
                        event.toLifecycleEvent()?.let { lifecycle ->
                            _lifecycleEvents.emit(lifecycle)
                        }
                    }
                }
            }
        )
    }

    private fun isCurrentSession(session: RuntimeSession): Boolean = synchronized(lifecycleLock) {
        !closed &&
            session.deviceUid !in retiredDeviceUids &&
            sessions[session.deviceUid] === session
    }

    private fun currentCommandSession(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandSession? = sessions[deviceUid]
        ?.takeIf(::isCurrentSession)
        ?.let { session ->
            DeviceRuntimeCommandSession(
                deviceUid = deviceUid,
                generation = session.generation,
                authenticated = session.wsClient.connectionState.value is
                    AqlWsConnectionState.Authenticated,
                send = session.wsClient::send
            )
        }

    private fun supportsCommand(
        deviceUid: DeviceUid,
        module: String,
        action: String
    ): Boolean {
        @Suppress("UNUSED_VARIABLE")
        val isolatedDeviceUid = deviceUid
        return AqlWsContract.isAuthenticatedCommand(module, action)
    }

    private fun currentTimerRuntimeAccess(deviceUid: DeviceUid): DeviceTimerRuntimeAccess =
        DeviceTimerRuntimeAccess.from(
            (metadataBootstrapCoordinator.currentState(deviceUid) as?
                DeviceRuntimeMetadataGenerationState.Ready)?.metadata
        )

    private fun nextRuntimeGeneration(): DeviceRuntimeConnectionGeneration {
        val value = runtimeGenerationCounter.incrementAndGet()
        check(value > 0L) { "Runtime connection generation exhausted." }
        return DeviceRuntimeConnectionGeneration(value)
    }

    private fun ensureOpen() {
        synchronized(lifecycleLock) { check(!closed) { "Device runtime repository is closed." } }
    }

    private suspend fun handleAuthLifecycle(
        session: RuntimeSession,
        event: AqlWsEvent
    ): Boolean = when (event) {
        is AqlWsEvent.Opened -> true
        is AqlWsEvent.Authenticated -> {
            domainBootstrapCoordinator.clear(session.deviceUid)
            setRuntimeReadiness(
                DeviceRuntimeSessionReadiness.CollectingSharedMetadata(
                    deviceUid = session.deviceUid,
                    connectionGeneration = session.generation
                )
            )
            if (!sendAuthenticatedBootstrap(session)) {
                rejectRuntimeReadiness(
                    session.deviceUid,
                    session.generation,
                    "metadataBootstrapDispatchFailed"
                )
                session.wsClient.disconnect(reason = METADATA_BOOTSTRAP_FAILED_REASON)
            }
            true
        }
        is AqlWsEvent.Message -> {
            commandExecutor.complete(
                deviceUid = session.deviceUid,
                generation = session.generation,
                message = event.parsed
            ) == DeviceRuntimeCompletionDisposition.UNMATCHED
        }
        is AqlWsEvent.Closed -> {
            commandExecutor.cancelGeneration(
                deviceUid = session.deviceUid,
                generation = session.generation,
                reason = COMMAND_CANCELLED_SOCKET_CLOSED
            )
            rejectActiveGeneration(event.deviceUid, "closed")
            rejectRuntimeReadiness(event.deviceUid, session.generation, "closed")
            domainBootstrapCoordinator.clear(event.deviceUid)
            true
        }
        is AqlWsEvent.Failure -> {
            commandExecutor.cancelGeneration(
                deviceUid = session.deviceUid,
                generation = session.generation,
                reason = COMMAND_CANCELLED_SOCKET_FAILURE
            )
            rejectActiveGeneration(event.deviceUid, "failure")
            rejectRuntimeReadiness(event.deviceUid, session.generation, "failure")
            domainBootstrapCoordinator.clear(event.deviceUid)
            true
        }
    }

    private fun AqlWsEvent.toLifecycleEvent(): DeviceRuntimeLifecycleEvent? = when (this) {
        is AqlWsEvent.Authenticated -> DeviceRuntimeLifecycleEvent.Authenticated(deviceUid)
        is AqlWsEvent.Closed,
        is AqlWsEvent.Failure -> DeviceRuntimeLifecycleEvent.Unavailable(deviceUid)
        is AqlWsEvent.Opened,
        is AqlWsEvent.Message -> null
    }

    private fun sendAuthenticatedBootstrap(session: RuntimeSession): Boolean {
        val generation = metadataBootstrapCoordinator.beginAndDispatch(
            deviceUid = session.deviceUid,
            send = session.wsClient::send
        ).getOrNull() ?: return false
        scheduleMetadataTimeout(session, generation)
        return true
    }

    companion object {
        private const val EVENT_BUFFER_CAPACITY = 256
        private const val LOCAL_NETWORK_UNAVAILABLE_REASON = "local network unavailable"
        private const val METADATA_BOOTSTRAP_FAILED_REASON = "metadata bootstrap failed"
        private const val DOMAIN_BOOTSTRAP_FAILED_REASON = "domain bootstrap failed"
        private const val METADATA_BOOTSTRAP_TIMEOUT_MILLIS = 10_000L
        private const val DOMAIN_BOOTSTRAP_RETRY_DELAY_MILLIS = 250L
        private const val COMMAND_CANCELLED_CONNECTION_REPLACED = "runtime connection replaced"
        private const val COMMAND_CANCELLED_NETWORK_ROUTE_CHANGED = "local network route changed"
        private const val COMMAND_CANCELLED_LOCAL_NETWORK_LOSS = "local network unavailable"
        private const val COMMAND_CANCELLED_DEVICE_RETIRED = "device runtime retired"
        private const val COMMAND_CANCELLED_DEVICE_CLOSED = "device runtime closed"
        private const val COMMAND_CANCELLED_CREDENTIAL_REVOKED = "runtime credential revoked"
        private const val COMMAND_CANCELLED_REPOSITORY_CLOSED = "runtime repository closed"
        private const val COMMAND_CANCELLED_REPOSITORY_SHUTDOWN = "runtime repository shutdown"
        private const val COMMAND_CANCELLED_METADATA_FAILURE = "metadata bootstrap failed"
        private const val COMMAND_CANCELLED_DOMAIN_BOOTSTRAP_FAILURE = "domain bootstrap failed"
        private const val COMMAND_CANCELLED_TRANSPORT_UNAVAILABLE = "runtime transport unavailable"
        private const val COMMAND_CANCELLED_SOCKET_CLOSED = "runtime socket closed"
        private const val COMMAND_CANCELLED_SOCKET_FAILURE = "runtime socket failure"

        fun withCredentialStore(context: Context, ownerUid: String): DeviceRuntimeRepository =
            DeviceRuntimeRepository(
                tokenProvider = DeviceCredentialStore(context = context, ownerUid = ownerUid)
            )
    }
}

private fun AqlWsConnectionState.isTerminalForPendingCommands(): Boolean = when (this) {
    AqlWsConnectionState.Disconnected,
    is AqlWsConnectionState.AuthRequired,
    is AqlWsConnectionState.Failed -> true
    is AqlWsConnectionState.Connecting,
    is AqlWsConnectionState.Connected,
    is AqlWsConnectionState.Authenticated -> false
}

internal object RuntimeConnectionReusePolicy {
    fun shouldReconnect(
        state: AqlWsConnectionState,
        requestedDeviceUid: DeviceUid,
        endpointMatches: Boolean
    ): Boolean {
        if (!endpointMatches) return true
        return when (state) {
            is AqlWsConnectionState.Connecting -> state.deviceUid != requestedDeviceUid
            is AqlWsConnectionState.Connected -> state.deviceUid != requestedDeviceUid
            is AqlWsConnectionState.Authenticated -> state.deviceUid != requestedDeviceUid
            AqlWsConnectionState.Disconnected,
            is AqlWsConnectionState.AuthRequired,
            is AqlWsConnectionState.Failed -> true
        }
    }
}
