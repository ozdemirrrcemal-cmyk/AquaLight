package com.aqua.aqualight.data.devices.api.dosing

data class DosingStatus(
    val channelCount: Int = 0,
    val activeChannel: Int? = null,
    val nextDoseText: String = ""
)

data class DosingChannelStatus(
    val channelIndex: Int,
    val enabled: Boolean = false,
    val remainingVolumeMl: Double? = null,
    val calibrationMlPerSecond: Double? = null
)

data class DosingSchedule(
    val id: String,
    val channelIndex: Int,
    val enabled: Boolean,
    val doseMl: Double,
    val runAtMinutes: List<Int>
)
