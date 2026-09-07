@file:Suppress("MagicNumber")

package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONObject

internal object DeviceTimerRuntimeFixtures {
    private val golden: JSONObject by lazy {
        JSONObject(
            requireNotNull(
                DeviceTimerRuntimeFixtures::class.java.classLoader
                    ?.getResourceAsStream(GOLDEN_FIXTURE)
            ) { "Missing Timer wire golden fixture: $GOLDEN_FIXTURE" }
                .use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
        )
    }

    fun globalStatus(
        uptimeMs: Long = 20_000L,
        revision: Long = 7L
    ): JSONObject = goldenObject("statusGlobal")
        .put("uptimeMs", uptimeMs)
        .put("revision", revision)

    fun channelStatus(
        uptimeMs: Long = 20_000L,
        revision: Long = 7L
    ): JSONObject = goldenObject("statusChannel")
        .put("uptimeMs", uptimeMs)
        .put("revision", revision)

    fun configApply(
        changed: Boolean = true,
        saved: Boolean = true,
        saveRequested: Boolean = true,
        revision: Long = 8L
    ): JSONObject = goldenObject("configApply")
        .put("changed", changed)
        .put("saved", saved)
        .put("saveRequested", saveRequested)
        .put("revision", revision)

    fun channelSet(revision: Long = 8L): JSONObject =
        goldenObject("channelSet").put("revision", revision)

    fun statusChanged(
        sequence: Long = 12L,
        revision: Long = 8L,
        channelKey: String = "channel1"
    ): JSONObject = goldenObject("statusChanged")
        .put("channelKey", channelKey)
        .put("revision", revision)
        .also { event -> event.getJSONObject("change").put("sequence", sequence) }

    fun schedulePayload(
        slotId: Int = 1,
        name: String = "Day Filter",
        startTimeMs: Long = timerScheduleBoundaryMillis(12, 0),
        endTimeMs: Long = timerScheduleBoundaryMillis(18, 0),
        enabled: Boolean = true,
        weekdays: List<Boolean> = WEEKDAYS
    ): DeviceTimerScheduleConfig = DeviceTimerScheduleConfig(
        slotId = slotId,
        enabled = enabled,
        name = name,
        weekdays = weekdays,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs
    )

    private fun goldenObject(key: String): JSONObject =
        JSONObject(golden.getJSONObject(key).toString())

    private const val GOLDEN_FIXTURE = "aql_timer_wire_v1_golden.json"
    private val WEEKDAYS = listOf(true, true, true, true, true, false, false)
}
