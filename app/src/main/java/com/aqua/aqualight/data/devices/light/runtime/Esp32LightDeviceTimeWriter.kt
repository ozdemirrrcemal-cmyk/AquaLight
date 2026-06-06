package com.aqua.aqualight.data.devices.light.runtime

import org.json.JSONObject

class Esp32LightDeviceTimeWriter(
    private val httpClient: Esp32HttpJsonClient = Esp32HttpJsonClient()
) {

    suspend fun writeTime(
        ip: String,
        timeState: LightDeviceTimeState
    ): LightCommandResult {
        val timeJson = buildTimeObject(timeState)

        val json = JSONObject()
            .put(
                "Time",
                JSONObject()
                    .put("SetTime", timeJson)
            )
            .put(
                "TimeL",
                buildTimeObject(timeState)
            )
            .toString()

        return httpClient.postSet(
            ip = ip,
            json = json,
            requestTag = "light_time_sync"
        )
    }

    private fun buildTimeObject(
        timeState: LightDeviceTimeState
    ): JSONObject {
        return JSONObject()
            .put("Y", timeState.year)
            .put("Mn", timeState.month)
            .put("D", timeState.day)
            .put("WD", timeState.weekDay)
            .put("H", timeState.hour)
            .put("M", timeState.minute)
            .put("S", timeState.second)
    }
}