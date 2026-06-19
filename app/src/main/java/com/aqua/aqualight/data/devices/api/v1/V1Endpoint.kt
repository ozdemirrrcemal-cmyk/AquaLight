package com.aqua.aqualight.data.devices.api.v1

enum class V1Endpoint(
    val path: String
) {
    API_INDEX("/api/v1"),
    IDENTITY("/api/v1/device/identity"),
    STATUS("/api/v1/device/status"),
    CAPABILITIES("/api/v1/device/capabilities"),
    NETWORK_STATUS("/api/v1/network/status"),
    NETWORK_WIFI("/api/v1/network/wifi"),
    NETWORK_SCAN("/api/v1/network/scan"),
    TIME_STATUS("/api/v1/time/status"),
    TIME_CONFIG("/api/v1/time/config"),
    TIME_SYNC("/api/v1/time/sync"),
    LIGHT_STATUS("/api/v1/light/status"),
    LIGHT_CHANNELS("/api/v1/light/channels"),
    LIGHT_CONFIG("/api/v1/light/config"),
    LIGHT_MANUAL("/api/v1/light/manual"),
    LIGHT_PROGRAMS("/api/v1/light/programs"),
    TIMER_STATUS("/api/v1/timer/status"),
    TIMER_CHANNELS("/api/v1/timer/channels"),
    TIMER_MANUAL("/api/v1/timer/manual"),
    TIMER_SCHEDULES("/api/v1/timer/schedules"),
    DOSING_STATUS("/api/v1/dosing/status"),
    DOSING_CHANNELS("/api/v1/dosing/channels"),
    DOSING_RESERVOIRS("/api/v1/dosing/reservoirs"),
    DOSING_SCHEDULES("/api/v1/dosing/schedules"),
    COOLING_STATUS("/api/v1/cooling/status"),
    COOLING_CONFIG("/api/v1/cooling/config"),
    COOLING_FANS("/api/v1/cooling/fans"),
    COOLING_MANUAL("/api/v1/cooling/manual")
}
