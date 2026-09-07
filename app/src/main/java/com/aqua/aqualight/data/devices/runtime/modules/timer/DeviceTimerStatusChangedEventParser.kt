package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONObject

internal object DeviceTimerStatusChangedEventParser {
    private val EVENT_KEYS = setOf(
        "schema", "schemaVersion", "channelKey", "revision", "publishedAtMs", "change"
    )
    private val CHANGE_KEYS = setOf(
        "sequence", "occurredAtMs", "operatingState", "activeSlotId", "activeSlotName",
        "nextTransitionType", "nextTransitionAt", "runtimeReason", "clockReady",
        "temporaryOverrideActive", "temporaryOverrideRemainingMs"
    )

    fun parse(data: JSONObject): DeviceTimerStatusChangedEvent {
        data.requireTimerKeys(EVENT_KEYS, "timer.status.changed event")
        return DeviceTimerStatusChangedEvent(
            schema = data.requireTimerText("schema"),
            schemaVersion = data.requireTimerInt(
                "schemaVersion",
                DeviceTimerRuntimeContract.SCHEMA_VERSION,
                DeviceTimerRuntimeContract.SCHEMA_VERSION
            ),
            channelKey = data.requireTimerText("channelKey"),
            revision = data.requireTimerLong(
                "revision",
                TIMER_NON_NEGATIVE_LONG,
                DeviceTimerRuntimeContract.Limit.UINT32_MAX
            ),
            publishedAtMs = data.requireTimerLong(
                "publishedAtMs",
                TIMER_NON_NEGATIVE_LONG,
                DeviceTimerRuntimeContract.Limit.UINT32_MAX
            ),
            change = parseChange(data.requireTimerObject("change"))
        ).also(::validate)
    }

    private fun parseChange(data: JSONObject): DeviceTimerStatusChange {
        data.requireTimerKeys(CHANGE_KEYS, "timer.status.changed change")
        return DeviceTimerStatusChange(
            sequence = data.requireTimerLong(
                "sequence",
                minimum = 1L,
                maximum = DeviceTimerRuntimeContract.Limit.UINT32_MAX
            ),
            occurredAtMs = data.requireTimerLong(
                "occurredAtMs",
                TIMER_NON_NEGATIVE_LONG,
                DeviceTimerRuntimeContract.Limit.UINT32_MAX
            ),
            operatingState = DeviceTimerOperatingStateParser.parse(
                data.requireTimerText("operatingState")
            ),
            activeSlotId = data.requireNullableTimerInt(
                "activeSlotId",
                DeviceTimerRuntimeContract.Limit.SLOT_ID_MINIMUM,
                DeviceTimerRuntimeContract.Limit.SLOT_ID_MAXIMUM
            ),
            activeSlotName = data.requireNullableTimerText("activeSlotName"),
            nextTransitionType = DeviceTimerNextTransitionTypeParser.parse(
                data.requireTimerText("nextTransitionType")
            ),
            nextTransitionAt = data.requireNullableTimerLong(
                "nextTransitionAt",
                minimum = TIMER_NON_NEGATIVE_LONG
            ),
            runtimeReason = DeviceTimerRuntimeReasonParser.parse(
                data.requireTimerText("runtimeReason")
            ),
            clockReady = data.requireTimerBoolean("clockReady"),
            temporaryOverrideActive = data.requireTimerBoolean("temporaryOverrideActive"),
            temporaryOverrideRemainingMs = data.requireTimerLong(
                "temporaryOverrideRemainingMs",
                TIMER_NON_NEGATIVE_LONG,
                DeviceTimerRuntimeContract.Limit.TEMPORARY_DURATION_MAXIMUM_MS
            )
        ).also { change ->
            require((change.activeSlotId == null) == (change.activeSlotName == null))
            require((change.activeSlotName?.toByteArray(Charsets.UTF_8)?.size ?: 0) <=
                DeviceTimerRuntimeContract.Limit.MAX_SCHEDULE_NAME_BYTES)
            require((change.nextTransitionType == DeviceTimerNextTransitionType.NONE) ==
                (change.nextTransitionAt == null))
            require(change.temporaryOverrideActive ==
                (change.temporaryOverrideRemainingMs > 0L))
        }
    }

    private fun validate(event: DeviceTimerStatusChangedEvent) {
        require(event.schema == DeviceTimerRuntimeContract.SCHEMA)
        require(event.schemaVersion == DeviceTimerRuntimeContract.SCHEMA_VERSION)
        require(event.channelKey == normalizeTimerChannelKey(event.channelKey))
    }
}
