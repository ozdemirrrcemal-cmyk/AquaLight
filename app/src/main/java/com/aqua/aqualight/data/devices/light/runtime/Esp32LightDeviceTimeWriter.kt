package com.aqua.aqualight.data.devices.light.runtime

import org.json.JSONObject

class Esp32LightDeviceTimeWriter(
    private val httpClient: Esp32HttpJsonClient = Esp32HttpJsonClient()
) {

    suspend fun writeTime(
        ip: String,
        timeState: LightDeviceTimeState
    ): LightCommandResult {
        val json = JSONObject()
            .put(
                "TimeL",
                JSONObject()
                    .put("Y", timeState.year)
                    .put("Mn", timeState.month)
                    .put("D", timeState.day)
                    .put("WD", timeState.weekDay)
                    .put("H", timeState.hour)
                    .put("M", timeState.minute)
                    .put("S", timeState.second)
            )
            .toString()

        return httpClient.postSet(
            ip = ip,
            json = json,
            requestTag = "light_time_sync"
        )
    }
}