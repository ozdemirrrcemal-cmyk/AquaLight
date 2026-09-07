package com.aqua.aqualight.application.devices.cooling.control

/** Firmware-owned Cooling operating state. Presentation may localize it but never infer it. */
enum class DeviceCoolingOperatingState {
    IDLE,
    COOLING,
    MANUAL,
    PROGRAM,
    FAULT
}

/** Stable application vocabulary for the firmware's control decision reason. */
enum class DeviceCoolingControlReason {
    FAN_HARDWARE_FAULT,
    MANUAL_PERSISTENT_TARGET,
    MANUAL_ZERO_OUTPUT,
    AUTOMATIC_CURVE,
    BELOW_AUTOMATIC_START,
    PROGRAM_EMPTY,
    CLOCK_UNSYNCED,
    NO_ACTIVE_PROGRAM_SLOT,
    PROGRAM_SLOT_COOLING,
    PROGRAM_SLOT_BELOW_THRESHOLD,
    CONFIG_STORAGE_REJECTED,
    WATER_SENSOR_MISSING,
    WATER_SENSOR_TOPOLOGY_INVALID,
    WATER_SENSOR_STALE,
    WATER_SENSOR_INVALID,
    OTA_SAFE_MODE,
    AUTOMATIC_PENDING_SENSOR_EVALUATION,
    PROGRAM_PENDING_TIME_EVALUATION,
    UNKNOWN
}

data class DeviceCoolingProgramRuntimeSnapshot(
    val persistedRevision: Long,
    val evaluatedRevision: Long,
    val slotCount: Int,
    val clockReady: Boolean,
    val currentMinuteOfDay: Int?,
    val activeSlotIndex: Int?
) {
    init {
        require(persistedRevision >= 0L)
        require(evaluatedRevision >= 0L)
        require(slotCount >= 0)
        require(clockReady == (currentMinuteOfDay != null))
        require(currentMinuteOfDay == null || currentMinuteOfDay in 0 until MINUTES_PER_DAY)
        require(activeSlotIndex == null || activeSlotIndex >= 0)
        if (persistedRevision == evaluatedRevision) {
            require(activeSlotIndex == null || activeSlotIndex < slotCount)
        }
    }
}

private const val MINUTES_PER_DAY = 1_440
