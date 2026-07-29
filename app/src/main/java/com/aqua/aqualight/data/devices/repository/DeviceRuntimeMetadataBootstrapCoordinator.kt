package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFailureCode
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFragment
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGeneration
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGenerationState
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataReduction
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage

/**
 * Owner-scoped authenticated metadata bootstrap.
 *
 * Only the exact identity, capabilities and status/modules commands are dispatched. Every command is
 * correlated before transport send. Any dispatch or response-contract failure rejects the complete
 * generation and removes all remaining tickets; no partial metadata can become reusable.
 */
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
                    deviceUid = deviceUid,
                    generation = collecting.generation,
                    code = DeviceRuntimeMetadataFailureCode.BOOTSTRAP_DISPATCH_FAILED,
                    field = "${kind.module}.${kind.action}:correlation"
                )
                error("Metadata bootstrap correlation registration failed.")
            }
            if (!send(command)) {
                reject(
                    deviceUid = deviceUid,
                    generation = collecting.generation,
                    code = DeviceRuntimeMetadataFailureCode.BOOTSTRAP_DISPATCH_FAILED,
                    field = "${kind.module}.${kind.action}:transport"
                )
                error("Metadata bootstrap transport dispatch failed.")
            }
        }
        collecting.generation
    }

    fun claim(
        deviceUid: DeviceUid,
        response: AqlWsIncomingMessage.Response
    ): DeviceRuntimeMetadataBootstrapClaim {
        val ticket = synchronized(lock) {
            ticketsByRequestId.remove(response.id)
        }
        if (ticket == null) return DeviceRuntimeMetadataBootstrapClaim.Unmatched

        val failure = ticket.responseFailure(deviceUid = deviceUid, response = response)
        val rejected = failure?.let { responseFailure ->
            reject(
                deviceUid = ticket.deviceUid,
                generation = ticket.generation,
                code = responseFailure.code,
                field = responseFailure.field
            )
        }
        val current = currentState(ticket.deviceUid)
        return when {
            rejected != null -> DeviceRuntimeMetadataBootstrapClaim.Rejected(rejected)
            failure != null -> DeviceRuntimeMetadataBootstrapClaim.Unmatched
            current?.generation == ticket.generation -> {
                DeviceRuntimeMetadataBootstrapClaim.Accepted(ticket)
            }
            else -> DeviceRuntimeMetadataBootstrapClaim.Unmatched
        }
    }

    fun accept(
        ticket: DeviceRuntimeMetadataBootstrapTicket,
        fragment: DeviceRuntimeMetadataFragment
    ): DeviceRuntimeMetadataReduction? = if (
        ticket.generation != fragment.generation || !ticket.kind.accepts(fragment)
    ) {
        reject(
            deviceUid = ticket.deviceUid,
            generation = ticket.generation,
            code = DeviceRuntimeMetadataFailureCode.BOOTSTRAP_RESPONSE_MISMATCH,
            field = ticket.kind.name
        )?.let { rejected -> DeviceRuntimeMetadataReduction.Rejected(rejected) }
    } else {
        synchronized(lock) {
            states[ticket.deviceUid]?.let { current ->
                reducer.reduce(current = current, fragment = fragment).also { reduction ->
                    states[ticket.deviceUid] = reduction.state
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
        reducer.reject(current = current, code = code, field = field).state.also { rejected ->
            states[deviceUid] = rejected
        }
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

    private fun startGeneration(
        deviceUid: DeviceUid
    ): DeviceRuntimeMetadataGenerationState.Collecting = synchronized(lock) {
        removeTicketsLocked(deviceUid)
        reducer.begin(deviceUid = deviceUid, previous = states[deviceUid]).also { collecting ->
            states[deviceUid] = collecting
        }
    }

    private fun registerTicket(ticket: DeviceRuntimeMetadataBootstrapTicket): Boolean =
        synchronized(lock) {
            val current = states[ticket.deviceUid]
            val duplicateKind = ticketsByRequestId.values.any { registered ->
                registered.deviceUid == ticket.deviceUid &&
                    registered.generation == ticket.generation &&
                    registered.kind == ticket.kind
            }
            val registrationChecks = listOf(
                ticket.requestId.isNotBlank(),
                current is DeviceRuntimeMetadataGenerationState.Collecting,
                current?.generation == ticket.generation,
                !ticketsByRequestId.containsKey(ticket.requestId),
                !duplicateKind
            )
            if (registrationChecks.all { valid -> valid }) {
                ticketsByRequestId[ticket.requestId] = ticket
                true
            } else {
                false
            }
        }

    private fun removeTicketsLocked(deviceUid: DeviceUid) {
        val iterator = ticketsByRequestId.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.deviceUid == deviceUid) iterator.remove()
        }
    }
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
        code = DeviceRuntimeMetadataFailureCode.BOOTSTRAP_RESPONSE_MISMATCH,
        field = "deviceUid"
    )
    response.module != kind.module -> BootstrapResponseFailure(
        code = DeviceRuntimeMetadataFailureCode.BOOTSTRAP_RESPONSE_MISMATCH,
        field = "module"
    )
    response.action != kind.action -> BootstrapResponseFailure(
        code = DeviceRuntimeMetadataFailureCode.BOOTSTRAP_RESPONSE_MISMATCH,
        field = "action"
    )
    !response.ok || response.statusCode !in SUCCESS_MIN_STATUS..SUCCESS_MAX_STATUS -> {
        BootstrapResponseFailure(
            code = DeviceRuntimeMetadataFailureCode.BOOTSTRAP_RESPONSE_FAILED,
            field = "${kind.module}.${kind.action}"
        )
    }
    else -> null
}

private const val SUCCESS_MIN_STATUS = 200
private const val SUCCESS_MAX_STATUS = 299
