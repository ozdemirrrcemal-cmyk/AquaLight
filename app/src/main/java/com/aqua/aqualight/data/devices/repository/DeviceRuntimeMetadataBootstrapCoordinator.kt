package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFailureCode
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFragment
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGeneration
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGenerationState
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataReduction
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage

/** Owner-scoped, correlated and fail-closed authenticated metadata bootstrap. */
@Suppress("TooManyFunctions")
internal class DeviceRuntimeMetadataBootstrapCoordinator(
    private val reducer: DeviceRuntimeMetadataReducer = DeviceRuntimeMetadataReducer()
) {
    private val lock = Any()
    private val states = linkedMapOf<DeviceUid, DeviceRuntimeMetadataGenerationState>()
    private val ticketsByRequestId = linkedMapOf<String, DeviceRuntimeMetadataBootstrapTicket>()

    fun beginAndDispatch(
        deviceUid: DeviceUid,
        send: (AqlWsOutgoingMessage.Command) -> Boolean
    ): Result<DeviceRuntimeMetadataGeneration> = runCatching {
        val collecting = startGeneration(deviceUid)
        deviceRuntimeMetadataBootstrapOrder.forEach { kind ->
            val command = kind.command()
            val ticket = DeviceRuntimeMetadataBootstrapTicket(
                deviceUid = deviceUid,
                generation = collecting.generation,
                kind = kind,
                requestId = command.id
            )
            if (!registerTicket(ticket)) {
                reject(
                    deviceUid,
                    collecting.generation,
                    DeviceRuntimeMetadataFailureCode.BOOTSTRAP_DISPATCH_FAILED,
                    "${kind.module}.${kind.action}:correlation"
                )
                error("Metadata bootstrap correlation registration failed.")
            }
            if (!send(command)) {
                reject(
                    deviceUid,
                    collecting.generation,
                    DeviceRuntimeMetadataFailureCode.BOOTSTRAP_DISPATCH_FAILED,
                    "${kind.module}.${kind.action}:transport"
                )
                error("Metadata bootstrap transport dispatch failed.")
            }
        }
        collecting.generation
    }

    fun process(
        deviceUid: DeviceUid,
        response: AqlWsIncomingMessage.Response
    ): DeviceRuntimeMetadataBootstrapProcessing = when (
        val claim = claim(deviceUid, response)
    ) {
        DeviceRuntimeMetadataBootstrapClaim.Unmatched ->
            DeviceRuntimeMetadataBootstrapProcessing.Unmatched
        is DeviceRuntimeMetadataBootstrapClaim.Rejected ->
            DeviceRuntimeMetadataBootstrapProcessing.Reduced(
                DeviceRuntimeMetadataReduction.Rejected(claim.state)
            )
        is DeviceRuntimeMetadataBootstrapClaim.Accepted ->
            processAccepted(claim.ticket, response)
    }

    fun claim(
        deviceUid: DeviceUid,
        response: AqlWsIncomingMessage.Response
    ): DeviceRuntimeMetadataBootstrapClaim {
        val ticket = synchronized(lock) { ticketsByRequestId.remove(response.id) }
            ?: return DeviceRuntimeMetadataBootstrapClaim.Unmatched
        val failure = ticket.responseFailure(deviceUid, response)
        val rejected = failure?.let {
            reject(ticket.deviceUid, ticket.generation, it.code, it.field)
        }
        val current = currentState(ticket.deviceUid)
        return when {
            rejected != null -> DeviceRuntimeMetadataBootstrapClaim.Rejected(rejected)
            failure != null -> DeviceRuntimeMetadataBootstrapClaim.Unmatched
            current?.generation == ticket.generation ->
                DeviceRuntimeMetadataBootstrapClaim.Accepted(ticket)
            else -> DeviceRuntimeMetadataBootstrapClaim.Unmatched
        }
    }

    fun accept(
        ticket: DeviceRuntimeMetadataBootstrapTicket,
        fragment: DeviceRuntimeMetadataFragment
    ): DeviceRuntimeMetadataReduction? {
        if (ticket.generation != fragment.generation || !ticket.kind.accepts(fragment)) {
            return reject(
                ticket.deviceUid,
                ticket.generation,
                DeviceRuntimeMetadataFailureCode.BOOTSTRAP_RESPONSE_MISMATCH,
                ticket.kind.name
            )?.let(DeviceRuntimeMetadataReduction::Rejected)
        }
        return synchronized(lock) {
            states[ticket.deviceUid]?.let { current ->
                reducer.reduce(current, fragment).also { reduction ->
                    states[ticket.deviceUid] = reduction.state
                    if (reduction.state !is DeviceRuntimeMetadataGenerationState.Collecting) {
                        removeTicketsLocked(ticket.deviceUid)
                    }
                }
            }
        }
    }

    fun reject(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeMetadataGeneration,
        code: DeviceRuntimeMetadataFailureCode,
        field: String?
    ): DeviceRuntimeMetadataGenerationState.Rejected? = synchronized(lock) {
        val current = states[deviceUid] ?: return@synchronized null
        if (current.generation != generation) return@synchronized null
        removeTicketsLocked(deviceUid)
        reducer.reject(current, code, field).state.also { states[deviceUid] = it }
    }

    fun clear(deviceUid: DeviceUid) {
        synchronized(lock) {
            removeTicketsLocked(deviceUid)
            states.remove(deviceUid)
        }
    }

    fun clearAll() {
        synchronized(lock) {
            ticketsByRequestId.clear()
            states.clear()
        }
    }

    fun currentState(deviceUid: DeviceUid): DeviceRuntimeMetadataGenerationState? =
        synchronized(lock) { states[deviceUid] }

    private fun processAccepted(
        ticket: DeviceRuntimeMetadataBootstrapTicket,
        response: AqlWsIncomingMessage.Response
    ): DeviceRuntimeMetadataBootstrapProcessing = parseFragment(ticket, response).fold(
        onSuccess = { fragment ->
            accept(ticket, fragment)
                ?.let(DeviceRuntimeMetadataBootstrapProcessing::Reduced)
                ?: DeviceRuntimeMetadataBootstrapProcessing.Unmatched
        },
        onFailure = { error ->
            reject(
                ticket.deviceUid,
                ticket.generation,
                ticket.kind.parseFailureCode,
                "${ticket.kind.module}.${ticket.kind.action}:${error.message.orEmpty()}"
            )?.let { rejected ->
                DeviceRuntimeMetadataBootstrapProcessing.Reduced(
                    DeviceRuntimeMetadataReduction.Rejected(rejected)
                )
            } ?: DeviceRuntimeMetadataBootstrapProcessing.Unmatched
        }
    )

    private fun parseFragment(
        ticket: DeviceRuntimeMetadataBootstrapTicket,
        response: AqlWsIncomingMessage.Response
    ): Result<DeviceRuntimeMetadataFragment> = when (ticket.kind) {
        DeviceRuntimeMetadataBootstrapKind.IDENTITY -> DeviceRuntimeIdentityParser.parse(
            ticket.deviceUid,
            response.data
        ).map { DeviceRuntimeMetadataFragment.Identity(ticket.generation, it) }
        DeviceRuntimeMetadataBootstrapKind.CAPABILITIES ->
            DeviceRuntimeCapabilitiesParser.parse(response.data).map {
                DeviceRuntimeMetadataFragment.Capabilities(ticket.generation, it)
            }
        DeviceRuntimeMetadataBootstrapKind.STATUS_MODULES ->
            DeviceRuntimeModulesParser.parseDeviceStatus(response.data).map {
                DeviceRuntimeMetadataFragment.Modules(ticket.generation, it)
            }
    }

    private fun startGeneration(deviceUid: DeviceUid):
        DeviceRuntimeMetadataGenerationState.Collecting = synchronized(lock) {
        removeTicketsLocked(deviceUid)
        reducer.begin(deviceUid, states[deviceUid]).also { states[deviceUid] = it }
    }

    private fun registerTicket(ticket: DeviceRuntimeMetadataBootstrapTicket): Boolean =
        synchronized(lock) {
            val current = states[ticket.deviceUid]
            val hasCurrentGeneration =
                current is DeviceRuntimeMetadataGenerationState.Collecting &&
                    current.generation == ticket.generation
            val hasUniqueRequestId = !ticketsByRequestId.containsKey(ticket.requestId)
            val hasUniqueKind = ticketsByRequestId.values.none { registered ->
                registered.deviceUid == ticket.deviceUid &&
                    registered.generation == ticket.generation &&
                    registered.kind == ticket.kind
            }
            val canRegister = ticket.requestId.isNotBlank() &&
                hasCurrentGeneration &&
                hasUniqueRequestId &&
                hasUniqueKind

            if (canRegister) {
                ticketsByRequestId[ticket.requestId] = ticket
            }
            canRegister
        }

    private fun removeTicketsLocked(deviceUid: DeviceUid) {
        val iterator = ticketsByRequestId.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.deviceUid == deviceUid) iterator.remove()
        }
    }
}

private val DeviceRuntimeMetadataBootstrapKind.parseFailureCode:
    DeviceRuntimeMetadataFailureCode
    get() = when (this) {
        DeviceRuntimeMetadataBootstrapKind.IDENTITY ->
            DeviceRuntimeMetadataFailureCode.IDENTITY_PARSE_FAILED
        DeviceRuntimeMetadataBootstrapKind.CAPABILITIES ->
            DeviceRuntimeMetadataFailureCode.CAPABILITIES_PARSE_FAILED
        DeviceRuntimeMetadataBootstrapKind.STATUS_MODULES ->
            DeviceRuntimeMetadataFailureCode.MODULES_PARSE_FAILED
    }

private data class BootstrapResponseFailure(
    val code: DeviceRuntimeMetadataFailureCode,
    val field: String
)

private fun DeviceRuntimeMetadataBootstrapTicket.responseFailure(
    deviceUid: DeviceUid,
    response: AqlWsIncomingMessage.Response
): BootstrapResponseFailure? = when {
    this.deviceUid != deviceUid -> BootstrapResponseFailure(
        DeviceRuntimeMetadataFailureCode.BOOTSTRAP_RESPONSE_MISMATCH,
        "deviceUid"
    )
    response.module != kind.module -> BootstrapResponseFailure(
        DeviceRuntimeMetadataFailureCode.BOOTSTRAP_RESPONSE_MISMATCH,
        "module"
    )
    response.action != kind.action -> BootstrapResponseFailure(
        DeviceRuntimeMetadataFailureCode.BOOTSTRAP_RESPONSE_MISMATCH,
        "action"
    )
    !response.ok || response.statusCode !in SUCCESS_MIN_STATUS..SUCCESS_MAX_STATUS ->
        BootstrapResponseFailure(
            DeviceRuntimeMetadataFailureCode.BOOTSTRAP_RESPONSE_FAILED,
            "${kind.module}.${kind.action}"
        )
    else -> null
}

private const val SUCCESS_MIN_STATUS = 200
private const val SUCCESS_MAX_STATUS = 299
