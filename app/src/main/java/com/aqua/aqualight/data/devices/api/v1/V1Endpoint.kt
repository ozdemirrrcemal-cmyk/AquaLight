package com.aqua.aqualight.data.devices.api.v1

enum class V1Endpoint(
    val path: String
) {
    IDENTITY("/api/v1/identity"),
    STATUS("/api/v1/status"),
    LIGHT_STATUS("/api/v1/light/status"),
    LIGHT_PROGRAMS("/api/v1/light/programs"),
    TIMER_SCHEDULES("/api/v1/timer/schedules"),
    DOSING_SCHEDULES("/api/v1/dosing/schedules"),
    COOLING_STATUS("/api/v1/cooling/status")
}
