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

    @Suppress("CyclomaticComplexMethod")
    private fun parseMutationChannel(
        action: String,
        result: JSONObject
    ): DeviceDosingV1ChannelDetail = when (action) {
        DeviceDosingV1Contract.Action.CONFIG_APPLY ->
            DeviceDosingV1MutationParser.parseConfigApply(result).channel
        DeviceDosingV1Contract.Action.PROGRAM_APPLY ->
            DeviceDosingV1MutationParser.parseProgramApply(result).channel
        DeviceDosingV1Contract.Action.CHANNEL_RESET ->
            DeviceDosingV1MutationParser.parseChannelReset(result).channel
        DeviceDosingV1Contract.Action.PRIME_START ->
            DeviceDosingV1MutationParser.parsePrimeStart(result).channel
        DeviceDosingV1Contract.Action.PRIME_STOP ->
            DeviceDosingV1MutationParser.parsePrimeStop(result).channel
        DeviceDosingV1Contract.Action.CALIBRATION_START ->
            DeviceDosingV1MutationParser.parseCalibrationStart(result).channel
        DeviceDosingV1Contract.Action.CALIBRATION_FINISH ->
            DeviceDosingV1MutationParser.parseCalibrationFinish(result).channel
        DeviceDosingV1Contract.Action.CALIBRATION_CONFIRM ->
            DeviceDosingV1MutationParser.parseCalibrationConfirm(result).channel
        DeviceDosingV1Contract.Action.CALIBRATION_CANCEL ->
            DeviceDosingV1MutationParser.parseCalibrationCancel(result).channel
        DeviceDosingV1Contract.Action.DOSE_NOW ->
            DeviceDosingV1MutationParser.parseDoseNow(result).channel
        DeviceDosingV1Contract.Action.DOSE_STOP ->
            DeviceDosingV1MutationParser.parseDoseStop(result).channel
        DeviceDosingV1Contract.Action.RESERVOIR_REFILL ->
            DeviceDosingV1MutationParser.parseReservoirRefill(result).channel
        else -> error("Unsupported Dosing mutation action: " + action)
    }

    private val MUTATION_ACTIONS = DeviceDosingV1Contract.Action.ALL - setOf(
        DeviceDosingV1Contract.Action.STATUS_GET,
        DeviceDosingV1Contract.Action.PROGRESS_GET
    )
}
