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
import com.aqua.aqualight.data.devices.runtime.modules.device.DeviceCommonRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareUpdatePlanner
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareUpdateRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightEventApplyResult
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeStateStore
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTemperatureProtectionRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightThermalRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightTypedEventReducer
import com.aqua.aqualight.data.devices.runtime.modules.network.DeviceNetworkRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.security.DeviceSecurityRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.time.DeviceTimeRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerEventApplyResult
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeAccess
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeStateStore
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerTypedEventReducer
import com.aqua.aqualight.i18n.AppLanguageController
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
    timerAccessProvider: (DeviceUid) -> DeviceTimerRuntimeAccess
) {
    private val lightStateStore = DeviceLightRuntimeStateStore()
    private val lightEventReducer = DeviceLightTypedEventReducer(lightStateStore)
    private val timerStateStore = DeviceTimerRuntimeStateStore()
    private val timerEventReducer = DeviceTimerTypedEventReducer(
        timerStateStore,
        timerAccessProvider
    )

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
    val light = DeviceLightRuntimeRepository(commandGateway, lightStateStore)
    val lightTemperatureProtection =
        DeviceLightTemperatureProtectionRuntimeRepository(commandGateway, lightStateStore)
    val lightThermal = DeviceLightThermalRuntimeRepository(commandGateway)
    val cooling = DeviceCoolingRuntimeRepository(commandGateway)

    internal val domainBootstrapPorts: List<DeviceRuntimeDomainBootstrapPort> = listOf(
        CommandBootstrapPort(
            domain = DeviceRuntimeDomain.LIGHT,
            request = light::requestStatus,
            isAuthoritative = light::isAuthoritative
        ),
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
            isAuthoritative = cooling::isAuthoritative
        ),
        CommandBootstrapPort(
            domain = DeviceRuntimeDomain.TIMER,
            request = timer::requestStatus,
            isAuthoritative = timer::isAuthoritative
        )
    )

    internal fun beginRuntimeGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ) {
        light.beginGeneration(deviceUid, generation)
        lightThermal.beginGeneration(deviceUid, generation)
        cooling.beginGeneration(deviceUid, generation)
        timer.beginGeneration(deviceUid, generation)
    }

    internal fun invalidateRuntimeAuthority(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) {
        light.invalidate(deviceUid, generation)
        lightThermal.invalidate(deviceUid, generation)
        cooling.invalidate(deviceUid, generation)
        timer.invalidate(deviceUid, generation)
    }

    internal suspend fun acceptTypedRuntimeEvent(event: DeviceRuntimeTypedEvent) {
        val lightResult = lightEventReducer.apply(event)
        if (
            event.type == DeviceRuntimeTypedEvent.Type.LIGHT_STATUS_CHANGED &&
            lightResult == DeviceLightEventApplyResult.Ignored &&
            event.payload is DeviceRuntimeEventPayload.CommandResult
        ) {
            val command = event.payload as DeviceRuntimeEventPayload.CommandResult
            if (
                command.commandAction ==
                DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_SET
            ) {
                lightTemperatureProtection.requestStatus(event.deviceUid)
            } else {
                light.requestStatus(event.deviceUid)
            }
        }

        lightThermal.consume(event)
        cooling.consume(event)

        val timerResult = timerEventReducer.apply(event)
        if (
            event.type == DeviceRuntimeTypedEvent.Type.TIMER_STATUS_CHANGED &&
            timerResult == DeviceTimerEventApplyResult.Ignored &&
            event.payload is DeviceRuntimeEventPayload.CommandResult
        ) {
            timer.requestStatus(event.deviceUid)
        }
    }

    /** Permanent owner cleanup only; socket lifecycle must use [invalidateRuntimeAuthority]. */
    internal fun clearRuntimeState(deviceUid: DeviceUid) {
        lightStateStore.clear(deviceUid)
        lightThermal.clear(deviceUid)
        cooling.clear(deviceUid)
        timerStateStore.clear(deviceUid)
    }
}

private class CommandBootstrapPort(
    override val domain: DeviceRuntimeDomain,
    private val request: suspend (DeviceUid) -> DeviceRuntimeCommandOutcome<*>,
    private val isAuthoritative: (DeviceUid, DeviceRuntimeConnectionGeneration) -> Boolean
) : DeviceRuntimeDomainBootstrapPort {
    override suspend fun hydrate(
        context: DeviceRuntimeBootstrapContext
    ): DeviceRuntimeDomainHydrationResult {
        var outcome = request(context.deviceUid)
        repeat(DOMAIN_BOOTSTRAP_MAX_ATTEMPTS - 1) {
            if (!outcome.isTransientBootstrapFailure()) return@repeat
            delay(DOMAIN_BOOTSTRAP_RETRY_DELAY_MILLIS)
            outcome = request(context.deviceUid)
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
