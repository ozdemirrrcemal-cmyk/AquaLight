package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import org.json.JSONObject

object DeviceDosingV1EventParser {
    private val DIRECT_EVENT_KEYS = setOf(
        "schema",
        "schemaVersion",
        "channelKey",
        "revision",
        "storageHealthy",
        "change"
    )

    fun parseDirect(data: JSONObject): DeviceDosingV1DirectEvent {
        data.requireDosingKeys(DIRECT_EVENT_KEYS, "direct dosing event")
        return DeviceDosingV1DirectEvent(
            schema = data.requireDosingString("schema"),
            schemaVersion = data.requireDosingLong("schemaVersion", minimum = 1L),
            channelKey = data.requireDosingChannelKey("channelKey"),
            revision = data.requireDosingLong(
                "revision",
                minimum = 0L,
                maximum = DeviceDosingV1Contract.Limit.MAX_UNSIGNED_INT
            ),
            storageHealthy = data.requireDosingBoolean("storageHealthy"),
            change = DeviceDosingV1StatusParser.parseRuntimeEvent(
                data.requireDosingObject("change")
            )
        ).also { event ->
            require(event.schema == DeviceDosingV1Contract.SCHEMA)
            require(event.schemaVersion == DeviceDosingV1Contract.SCHEMA_VERSION)
        }
    }

    /**
     * Converts either supported firmware event shape into a read request. No snapshot fields are
     * synthesized from the event payload.
     */
    fun parseInvalidation(payload: DeviceRuntimeEventPayload): DeviceDosingV1Invalidation =
        when (payload) {
            is DeviceRuntimeEventPayload.Snapshot -> parseDirect(payload.data).let { event ->
                DeviceDosingV1Invalidation(
                    channelKey = event.channelKey,
                    revisionHint = event.revision
                )
            }
            is DeviceRuntimeEventPayload.CommandResult -> {
                require(payload.commandModule == DeviceDosingV1Contract.MODULE) {
                    "Command-result event is not a Dosing command."
                }
                require(payload.commandAction in MUTATION_ACTIONS) {
                    "Command-result event is not a Dosing mutation."
                }
                val channel = parseMutationChannel(
                    action = payload.commandAction,
                    result = payload.result
                )
                DeviceDosingV1Invalidation(
                    channelKey = channel.channelKey,
                    revisionHint = channel.revision
                )
            }
        }

    private fun parseMutationChannel(
        action: String,
        result: JSONObject
    ): DeviceDosingV1ChannelDetail = mutationChannelParsers[action]?.invoke(result)
        ?: error("Unsupported Dosing mutation action: $action")

    private val mutationChannelParsers:
        Map<String, (JSONObject) -> DeviceDosingV1ChannelDetail> = mapOf(
            DeviceDosingV1Contract.Action.CONFIG_APPLY to
                { data -> DeviceDosingV1SavedMutationParser.parseConfigApply(data).channel },
            DeviceDosingV1Contract.Action.PROGRAM_APPLY to
                { data -> DeviceDosingV1SavedMutationParser.parseProgramApply(data).channel },
            DeviceDosingV1Contract.Action.CHANNEL_RESET to
                { data -> DeviceDosingV1SavedMutationParser.parseChannelReset(data).channel },
            DeviceDosingV1Contract.Action.PRIME_START to
                { data -> DeviceDosingV1MutationParser.parsePrimeStart(data).channel },
            DeviceDosingV1Contract.Action.PRIME_STOP to
                { data -> DeviceDosingV1MutationParser.parsePrimeStop(data).channel },
            DeviceDosingV1Contract.Action.CALIBRATION_START to
                { data -> DeviceDosingV1MutationParser.parseCalibrationStart(data).channel },
            DeviceDosingV1Contract.Action.CALIBRATION_FINISH to
                { data -> DeviceDosingV1MutationParser.parseCalibrationFinish(data).channel },
            DeviceDosingV1Contract.Action.CALIBRATION_CONFIRM to
                { data -> DeviceDosingV1MutationParser.parseCalibrationConfirm(data).channel },
            DeviceDosingV1Contract.Action.CALIBRATION_CANCEL to
                { data -> DeviceDosingV1MutationParser.parseCalibrationCancel(data).channel },
            DeviceDosingV1Contract.Action.DOSE_NOW to
                { data -> DeviceDosingV1MutationParser.parseDoseNow(data).channel },
            DeviceDosingV1Contract.Action.DOSE_STOP to
                { data -> DeviceDosingV1MutationParser.parseDoseStop(data).channel },
            DeviceDosingV1Contract.Action.RESERVOIR_REFILL to
                { data -> DeviceDosingV1MutationParser.parseReservoirRefill(data).channel }
        )

    private val MUTATION_ACTIONS = DeviceDosingV1Contract.Action.ALL - setOf(
        DeviceDosingV1Contract.Action.STATUS_GET,
        DeviceDosingV1Contract.Action.PROGRESS_GET
    )
}
