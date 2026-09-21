package com.aqua.aqualight.data.devices.runtime.modules

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.bootstrap.DeviceRuntimeBootstrapContext
import com.aqua.aqualight.data.devices.runtime.bootstrap.DeviceRuntimeDomain
import com.aqua.aqualight.data.devices.runtime.bootstrap.DeviceRuntimeDomainBootstrapPort
import com.aqua.aqualight.data.devices.runtime.bootstrap.DeviceRuntimeDomainHydrationResult
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.cooling.isAuthoritative as isCoolingAuthoritative
import com.aqua.aqualight.data.devices.runtime.modules.device.DeviceCommonRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareUpdatePlanner
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareUpdateRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightCommittedReconciliationScheduler
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightEventApplyResult
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMode
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeAccess
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRefreshCoordinator
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRefreshResult
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeStateOwner
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTypedEventReducer
import com.aqua.aqualight.data.devices.runtime.modules.light.beginGeneration
import com.aqua.aqualight.data.devices.runtime.modules.light.invalidate
import com.aqua.aqualight.data.devices.runtime.modules.network.DeviceNetworkRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.security.DeviceSecurityRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.time.DeviceTimeRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeAccess
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeStateStore
import com.aqua.aqualight.data.devices.runtime.modules.timer.isAuthoritative as isTimerAuthoritative
import com.aqua.aqualight.i18n.AppLanguageController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

/**
 * Owner-scoped runtime module composition.
 *
 * Each stateful domain has one authoritative owner/facade. Lifecycle generation, bootstrap,
 * correlated replies and typed events all enter through this composition; presentation never owns
 * freshness or creates a parallel state store.
 */
class DeviceRuntimeModuleProvider internal constructor(
    internal val commandGateway: DeviceRuntimeCommandGateway,
    revokeLocalCredential: suspend (DeviceUid) -> Result<Unit>,
    timerAccessProvider: (DeviceUid) -> DeviceTimerRuntimeAccess,
    lightAccessProvider: (DeviceUid) -> DeviceLightRuntimeAccess,
    reconciliationScope: CoroutineScope? = null
) {
    private val lightStateOwner = DeviceLightRuntimeStateOwner()
    private val lightEventReducer = DeviceLightTypedEventReducer(
        stateOwner = lightStateOwner,
        accessProvider = lightAccessProvider
    )
    private val timerStateStore = DeviceTimerRuntimeStateStore()

    val device = DeviceCommonRuntimeRepository(commandGateway)
    val security = DeviceSecurityRuntimeRepository(commandGateway, revokeLocalCredential)
    val network = DeviceNetworkRuntimeRepository(commandGateway)
    val time = DeviceTimeRuntimeRepository(commandGateway)

    val firmware = DeviceFirmwareRuntimeRepository(commandGateway)
    val firmwareUpdate = DeviceFirmwareUpdateRepository(
        runtime = firmware,
        planner = DeviceFirmwareUpdatePlanner {
            listOf(AppLanguageController.current())
        }
    )

    val timer = DeviceTimerRuntimeRepository(commandGateway, timerStateStore, timerAccessProvider)
    val light = DeviceLightRuntimeRepository(
        gateway = commandGateway,
        stateOwner = lightStateOwner,
        accessProvider = lightAccessProvider
    )
    val lightTemperatureProtection =
        DeviceLightTemperatureProtectionRuntimeRepository(commandGateway, lightStateOwner)
    val lightThermal = DeviceLightThermalRuntimeRepository(commandGateway, lightStateOwner)
    private val lightRuntimeRefreshCoordinator = DeviceLightRuntimeRefreshCoordinator(
        runtime = light,
        thermal = lightThermal,
        protection = lightTemperatureProtection
    )
    private val lightCommittedReconciliationScheduler = reconciliationScope?.let { scope ->
        DeviceLightCommittedReconciliationScheduler(scope, lightRuntimeRefreshCoordinator)
    }
    val cooling = DeviceCoolingRuntimeRepository(commandGateway)

    internal val domainBootstrapPorts: List<DeviceRuntimeDomainBootstrapPort> = listOf(
        LightRuntimeBootstrapPort(lightRuntimeRefreshCoordinator),
        CommandBootstrapPort(
            domain = DeviceRuntimeDomain.LIGHT_PROTECTION,
            request = lightTemperatureProtection::requestStatus,
            isAuthoritative = lightTemperatureProtection::isAuthoritative
        ),
        CommandBootstrapPort(
            domain = DeviceRuntimeDomain.LIGHT_THERMAL,
            request = lightThermal::requestStatus,
            isAuthoritative = lightThermal::isAuthoritative
        ),
        CommandBootstrapPort(
            domain = DeviceRuntimeDomain.COOLING,
            request = cooling::requestStatus,
            isAuthoritative = { deviceUid, generation ->
                cooling.isCoolingAuthoritative(deviceUid, generation)
            }
        ),
        CommandBootstrapPort(
            domain = DeviceRuntimeDomain.TIMER,
            request = { deviceUid -> timer.requestStatus(deviceUid) },
            isAuthoritative = { deviceUid, generation ->
                timer.isTimerAuthoritative(deviceUid, generation)
            }
        )
    )

    internal fun beginRuntimeGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ) {
        light.beginGeneration(deviceUid, generation)
        cooling.beginGeneration(deviceUid, generation)
        timer.beginGeneration(deviceUid, generation)
    }

    internal fun invalidateRuntimeAuthority(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) {
        lightCommittedReconciliationScheduler?.cancel(deviceUid)
        light.invalidate(deviceUid, generation)
        cooling.invalidate(deviceUid, generation)
        timer.invalidate(deviceUid, generation)
    }

    internal suspend fun refreshLightRuntime(
        deviceUid: DeviceUid
    ): DeviceLightRuntimeRefreshResult = lightRuntimeRefreshCoordinator.refreshAll(deviceUid)

    internal fun scheduleLightControlReconciliation(
        deviceUid: DeviceUid,
        expectedMode: DeviceLightMode,
        generation: DeviceRuntimeConnectionGeneration
    ) {
        lightCommittedReconciliationScheduler?.schedule(deviceUid, expectedMode, generation)
    }

    internal suspend fun acceptTypedRuntimeEvent(event: DeviceRuntimeTypedEvent) {
        val lightResult = lightEventReducer.apply(event)
        if (event.type == DeviceRuntimeTypedEvent.Type.LIGHT_STATUS_CHANGED) {
            consumeLightStatusChanged(event, lightResult)
        }

        if (
            event.type == DeviceRuntimeTypedEvent.Type.LIGHT_THERMAL_STATUS_CHANGED &&
            event.payload is DeviceRuntimeEventPayload.CommandResult &&
            event.payload.commandAction ==
            DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_SET
        ) {
            lightTemperatureProtection.requestStatus(event.deviceUid)
        }

        lightThermal.consume(event)
        cooling.consume(event)
        timer.consume(event)
    }

    private suspend fun consumeLightStatusChanged(
        event: DeviceRuntimeTypedEvent,
        lightResult: DeviceLightEventApplyResult
    ) {
        if (lightResult is DeviceLightEventApplyResult.Malformed) return
        val command = event.payload as? DeviceRuntimeEventPayload.CommandResult
        if (
            command?.commandAction ==
            DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_SET
        ) {
            lightTemperatureProtection.requestStatus(event.deviceUid)
            return
        }

        command?.committedControlModeOrNull()
            ?.let { mode ->
                lightRuntimeRefreshCoordinator.reconcileCommitted(
                    event.deviceUid,
                    mode,
                    event.generation
                )
            }
            ?: lightRuntimeRefreshCoordinator.refreshAll(event.deviceUid)
    }

    /** Permanent owner cleanup only; socket lifecycle must use [invalidateRuntimeAuthority]. */
    internal fun clearRuntimeState(deviceUid: DeviceUid) {
        lightCommittedReconciliationScheduler?.cancel(deviceUid)
        lightStateOwner.clear(deviceUid)
        cooling.clear(deviceUid)
        timerStateStore.clear(deviceUid)
    }
}

private fun DeviceRuntimeEventPayload.CommandResult.committedControlModeOrNull(): DeviceLightMode? {
    if (
        commandModule != DeviceLightRuntimeContract.MODULE ||
        commandAction != DeviceLightRuntimeContract.Action.CONTROL_SET
    ) {
        return null
    }
    return runCatching {
        DeviceLightMode.fromWireExact(result.getString(DeviceLightRuntimeContract.Field.MODE))
    }.getOrNull()
}

private class LightRuntimeBootstrapPort(
    private val refreshCoordinator: DeviceLightRuntimeRefreshCoordinator
) : DeviceRuntimeDomainBootstrapPort {
    override val domain: DeviceRuntimeDomain = DeviceRuntimeDomain.LIGHT

    override suspend fun hydrate(
        context: DeviceRuntimeBootstrapContext
    ): DeviceRuntimeDomainHydrationResult {
        var refresh = refreshCoordinator.refreshGeneration(
            context.deviceUid,
            context.connectionGeneration
        )
        var remainingAttempts = DOMAIN_BOOTSTRAP_MAX_ATTEMPTS - 1
        while (refresh.isTransientBootstrapFailure() && remainingAttempts > 0) {
            delay(DOMAIN_BOOTSTRAP_RETRY_DELAY_MILLIS)
            refresh = refreshCoordinator.refreshGeneration(
                context.deviceUid,
                context.connectionGeneration
            )
            remainingAttempts -= 1
        }
        return refresh.toHydrationResult(domain)
    }
}

private fun DeviceLightRuntimeRefreshResult.toHydrationResult(
    domain: DeviceRuntimeDomain
): DeviceRuntimeDomainHydrationResult = when (this) {
    is DeviceLightRuntimeRefreshResult.Success ->
        DeviceRuntimeDomainHydrationResult.Hydrated(domain, generation)
    is DeviceLightRuntimeRefreshResult.Failed ->
        DeviceRuntimeDomainHydrationResult.Failed(domain, outcome)
    DeviceLightRuntimeRefreshResult.RejectedStale,
    DeviceLightRuntimeRefreshResult.Malformed ->
        DeviceRuntimeDomainHydrationResult.RejectedStale(domain)
}

private fun DeviceLightRuntimeRefreshResult.isTransientBootstrapFailure(): Boolean =
    this is DeviceLightRuntimeRefreshResult.Failed && outcome.isTransientBootstrapFailure()

private class CommandBootstrapPort(
    override val domain: DeviceRuntimeDomain,
    private val request: suspend (DeviceUid) -> DeviceRuntimeCommandOutcome<*>,
    private val isAuthoritative: (DeviceUid, DeviceRuntimeConnectionGeneration) -> Boolean
) : DeviceRuntimeDomainBootstrapPort {
    override suspend fun hydrate(
        context: DeviceRuntimeBootstrapContext
    ): DeviceRuntimeDomainHydrationResult {
        var outcome = request(context.deviceUid)
        var remainingAttempts = DOMAIN_BOOTSTRAP_MAX_ATTEMPTS - 1
        while (outcome.isTransientBootstrapFailure() && remainingAttempts > 0) {
            delay(DOMAIN_BOOTSTRAP_RETRY_DELAY_MILLIS)
            outcome = request(context.deviceUid)
            remainingAttempts -= 1
        }
        return when {
            outcome !is DeviceRuntimeCommandOutcome.Success<*> ->
                DeviceRuntimeDomainHydrationResult.Failed(domain, outcome)
            outcome.generation != context.connectionGeneration ->
                DeviceRuntimeDomainHydrationResult.RejectedStale(domain)
            !isAuthoritative(context.deviceUid, context.connectionGeneration) ->
                DeviceRuntimeDomainHydrationResult.RejectedStale(domain)
            else -> DeviceRuntimeDomainHydrationResult.Hydrated(
                domain = domain,
                generation = outcome.generation
            )
        }
    }
}

private fun DeviceRuntimeCommandOutcome<*>.isTransientBootstrapFailure(): Boolean = when (this) {
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated,
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.Cancelled -> true
    is DeviceRuntimeCommandOutcome.Success,
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice,
    is DeviceRuntimeCommandOutcome.FirmwareError,
    is DeviceRuntimeCommandOutcome.ProtocolError -> false
}

private const val DOMAIN_BOOTSTRAP_MAX_ATTEMPTS = 8
private const val DOMAIN_BOOTSTRAP_RETRY_DELAY_MILLIS = 250L
