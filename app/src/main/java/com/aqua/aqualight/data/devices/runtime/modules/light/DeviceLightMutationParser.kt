package com.aqua.aqualight.data.devices.runtime.modules.light

import org.json.JSONObject

/** Exact parsers for successful firmware Light mutation responses. */
internal object DeviceLightMutationParser {

    fun parseManual(data: JSONObject): DeviceLightManualMutationResult {
        data.requireLightKeys(MANUAL_KEYS, "light.manual.set.data")
        validateEnvelope(
            data = data,
            command = DeviceLightRuntimeContract.QualifiedCommand.MANUAL_SET
        )
        val operation = DeviceLightManualOperation.fromWireExact(
            data.requireLightText(DeviceLightRuntimeContract.Field.OPERATION)
        )
        val manualActive = data.requireLightBoolean(
            DeviceLightRuntimeContract.Field.MANUAL_ACTIVE
        )
        require(manualActive == (operation == DeviceLightManualOperation.MANUAL_STATE)) {
            "manualActive differs from the reported manual operation."
        }
        val durationMs = data.requireLightLong(
            DeviceLightRuntimeContract.Field.DURATION_MS,
            minimum = 0L,
            maximum = DeviceLightRuntimeContract.Limit.MAX_MANUAL_DURATION_MS
        )
        if (manualActive) {
            require(durationMs >= DeviceLightRuntimeContract.Limit.MIN_MANUAL_DURATION_MS)
        } else {
            require(durationMs == 0L)
        }

        val channelData = data.requireLightArray(DeviceLightRuntimeContract.Field.CHANNELS)
        val channels = List(channelData.length()) { index ->
            DeviceLightChannelParser.parseMutation(channelData.requireLightObject(index))
        }
        val affectedChannelCount = data.requireLightInt(
            DeviceLightRuntimeContract.Field.AFFECTED_CHANNEL_COUNT,
            minimum = 1
        )
        require(affectedChannelCount == channels.size) {
            "affectedChannelCount differs from the returned channels size."
        }
        require(channels.map { item -> item.channel.key }.toSet().size == channels.size) {
            "light.manual.set returned duplicate channel keys."
        }
        val saved = data.requireLightBoolean(DeviceLightRuntimeContract.Field.SAVED)
        require(!saved) { "Manual Light state must not be persisted by firmware." }

        return DeviceLightManualMutationResult(
            operation = operation,
            manualActive = manualActive,
            durationMs = durationMs,
            affectedChannelCount = affectedChannelCount,
            saved = saved,
            channels = channels
        )
    }

    fun parseChannelRegime(data: JSONObject): DeviceLightChannelRegimeMutationResult {
        data.requireLightKeys(CHANNEL_REGIME_KEYS, "light.channel.regime.set.data")
        validateOperation(
            data,
            DeviceLightRuntimeContract.Operation.CHANNEL_REGIME_SET,
            DeviceLightRuntimeContract.QualifiedCommand.CHANNEL_REGIME_SET
        )
        val changed = data.requireLightBoolean(DeviceLightRuntimeContract.Field.CHANGED)
        val saved = data.requireLightBoolean(DeviceLightRuntimeContract.Field.SAVED)
        val saveRequested = data.requireLightBoolean(
            DeviceLightRuntimeContract.Field.SAVE_REQUESTED
        )
        require(saved == saveRequested) {
            "Firmware persistence echo differs from saveRequested."
        }
        val channelKey = data.requireLightText(DeviceLightRuntimeContract.Field.CHANNEL_KEY)
        val regime = exactRegime(data.requireLightText(DeviceLightRuntimeContract.Field.REGIME))
        val channel = DeviceLightChannelParser.parseMutation(
            data.requireLightObject(DeviceLightRuntimeContract.Field.CHANNEL)
        )
        require(channel.channel.key == channelKey)
        require(channel.channel.regime == regime)

        return DeviceLightChannelRegimeMutationResult(
            changed = changed,
            saved = saved,
            saveRequested = saveRequested,
            channelKey = channelKey,
            regime = regime,
            channel = channel
        )
    }

    fun parseProgramApply(data: JSONObject): DeviceLightProgramApplyResult {
        data.requireLightKeys(PROGRAM_APPLY_KEYS, "light.program.apply.data")
        validateOperation(
            data,
            DeviceLightRuntimeContract.Operation.PROGRAM_APPLY,
            DeviceLightRuntimeContract.QualifiedCommand.PROGRAM_APPLY
        )
        val created = data.requireLightBoolean(DeviceLightRuntimeContract.Field.CREATED)
        val changed = data.requireLightBoolean(DeviceLightRuntimeContract.Field.CHANGED)
        require(changed) { "Successful program apply must report changed=true." }
        val saved = data.requireLightBoolean(DeviceLightRuntimeContract.Field.SAVED)
        val saveRequested = data.requireLightBoolean(
            DeviceLightRuntimeContract.Field.SAVE_REQUESTED
        )
        require(saved == saveRequested) {
            "Firmware persistence echo differs from saveRequested."
        }
        val programIndex = data.requireLightInt(
            DeviceLightRuntimeContract.Field.PROGRAM_INDEX,
            minimum = 0
        )
        val channelKey = data.requireLightText(DeviceLightRuntimeContract.Field.CHANNEL_KEY)
        val channelListIndex = data.requireLightInt(
            DeviceLightRuntimeContract.Field.CHANNEL_LIST_INDEX,
            minimum = 0
        )
        val program = DeviceLightProgramParser.parseMutation(
            data.requireLightObject(DeviceLightRuntimeContract.Field.PROGRAM)
        )
        require(program.index == programIndex)
        require(program.channelKey == channelKey)

        return DeviceLightProgramApplyResult(
            created = created,
            changed = changed,
            saved = saved,
            saveRequested = saveRequested,
            programIndex = programIndex,
            channelKey = channelKey,
            channelListIndex = channelListIndex,
            program = program
        )
    }

    fun parseProgramDelete(data: JSONObject): DeviceLightProgramDeleteResult {
        data.requireLightKeys(PROGRAM_DELETE_KEYS, "light.program.delete.data")
        validateOperation(
            data,
            DeviceLightRuntimeContract.Operation.PROGRAM_DELETE,
            DeviceLightRuntimeContract.QualifiedCommand.PROGRAM_DELETE
        )
        val deleted = data.requireLightBoolean(DeviceLightRuntimeContract.Field.DELETED)
        val changed = data.requireLightBoolean(DeviceLightRuntimeContract.Field.CHANGED)
        require(deleted && changed) {
            "Successful program delete must report deleted=true and changed=true."
        }
        val saved = data.requireLightBoolean(DeviceLightRuntimeContract.Field.SAVED)
        val saveRequested = data.requireLightBoolean(
            DeviceLightRuntimeContract.Field.SAVE_REQUESTED
        )
        require(saved == saveRequested) {
            "Firmware persistence echo differs from saveRequested."
        }

        return DeviceLightProgramDeleteResult(
            deleted = deleted,
            changed = changed,
            saved = saved,
            saveRequested = saveRequested,
            programIndex = data.requireLightInt(
                DeviceLightRuntimeContract.Field.PROGRAM_INDEX,
                minimum = 0
            ),
            deletedListIndex = data.requireLightInt(
                DeviceLightRuntimeContract.Field.DELETED_LIST_INDEX,
                minimum = 0
            ),
            channelKey = data.requireLightText(DeviceLightRuntimeContract.Field.CHANNEL_KEY),
            deletedPointCount = data.requireLightInt(
                DeviceLightRuntimeContract.Field.DELETED_POINT_COUNT,
                minimum = 0
            ),
            programCount = data.requireLightInt(
                DeviceLightRuntimeContract.Field.PROGRAM_COUNT,
                minimum = 0
            )
        )
    }

    private fun validateOperation(data: JSONObject, operation: String, command: String) {
        require(data.requireLightText(DeviceLightRuntimeContract.Field.OPERATION) == operation)
        validateEnvelope(data, command)
    }

    private fun validateEnvelope(data: JSONObject, command: String) {
        require(
            data.requireLightText(DeviceLightRuntimeContract.Field.RUNTIME_TRANSPORT) ==
                DeviceLightRuntimeContract.Transport.WEBSOCKET
        )
        require(data.requireLightText(DeviceLightRuntimeContract.Field.COMMAND) == command)
        require(
            data.requireLightText(DeviceLightRuntimeContract.Field.EVENT) ==
                DeviceLightRuntimeContract.Event.STATUS_CHANGED
        )
    }

    private fun exactRegime(value: String): DeviceLightRegime =
        DeviceLightRegime.values().singleOrNull { regime -> regime.wireValue == value }
            ?: error("Unknown firmware light regime: $value")

    private val MANUAL_KEYS = setOf(
        DeviceLightRuntimeContract.Field.OPERATION,
        DeviceLightRuntimeContract.Field.MANUAL_ACTIVE,
        DeviceLightRuntimeContract.Field.DURATION_MS,
        DeviceLightRuntimeContract.Field.RUNTIME_TRANSPORT,
        DeviceLightRuntimeContract.Field.COMMAND,
        DeviceLightRuntimeContract.Field.EVENT,
        DeviceLightRuntimeContract.Field.CHANNELS,
        DeviceLightRuntimeContract.Field.AFFECTED_CHANNEL_COUNT,
        DeviceLightRuntimeContract.Field.SAVED
    )
    private val CHANNEL_REGIME_KEYS = setOf(
        DeviceLightRuntimeContract.Field.OPERATION,
        DeviceLightRuntimeContract.Field.CHANGED,
        DeviceLightRuntimeContract.Field.SAVED,
        DeviceLightRuntimeContract.Field.SAVE_REQUESTED,
        DeviceLightRuntimeContract.Field.CHANNEL_KEY,
        DeviceLightRuntimeContract.Field.REGIME,
        DeviceLightRuntimeContract.Field.RUNTIME_TRANSPORT,
        DeviceLightRuntimeContract.Field.COMMAND,
        DeviceLightRuntimeContract.Field.EVENT,
        DeviceLightRuntimeContract.Field.CHANNEL
    )
    private val PROGRAM_APPLY_KEYS = setOf(
        DeviceLightRuntimeContract.Field.OPERATION,
        DeviceLightRuntimeContract.Field.CREATED,
        DeviceLightRuntimeContract.Field.CHANGED,
        DeviceLightRuntimeContract.Field.SAVED,
        DeviceLightRuntimeContract.Field.SAVE_REQUESTED,
        DeviceLightRuntimeContract.Field.PROGRAM_INDEX,
        DeviceLightRuntimeContract.Field.CHANNEL_KEY,
        DeviceLightRuntimeContract.Field.CHANNEL_LIST_INDEX,
        DeviceLightRuntimeContract.Field.RUNTIME_TRANSPORT,
        DeviceLightRuntimeContract.Field.COMMAND,
        DeviceLightRuntimeContract.Field.EVENT,
        DeviceLightRuntimeContract.Field.PROGRAM
    )
    private val PROGRAM_DELETE_KEYS = setOf(
        DeviceLightRuntimeContract.Field.OPERATION,
        DeviceLightRuntimeContract.Field.DELETED,
        DeviceLightRuntimeContract.Field.CHANGED,
        DeviceLightRuntimeContract.Field.SAVED,
        DeviceLightRuntimeContract.Field.SAVE_REQUESTED,
        DeviceLightRuntimeContract.Field.PROGRAM_INDEX,
        DeviceLightRuntimeContract.Field.DELETED_LIST_INDEX,
        DeviceLightRuntimeContract.Field.CHANNEL_KEY,
        DeviceLightRuntimeContract.Field.DELETED_POINT_COUNT,
        DeviceLightRuntimeContract.Field.PROGRAM_COUNT,
        DeviceLightRuntimeContract.Field.RUNTIME_TRANSPORT,
        DeviceLightRuntimeContract.Field.COMMAND,
        DeviceLightRuntimeContract.Field.EVENT
    )
}
