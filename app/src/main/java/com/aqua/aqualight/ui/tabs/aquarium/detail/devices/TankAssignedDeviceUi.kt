package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import androidx.annotation.DrawableRes

sealed class TankAssignedDeviceUi {

    abstract val deviceId: Long
    abstract val title: String
    abstract val subtitle: String
    abstract val iconRes: Int
    abstract val isOnline: Boolean

    data class Light(
        override val deviceId: Long,
        override val title: String,
        override val subtitle: String,
        @DrawableRes override val iconRes: Int,
        override val isOnline: Boolean,
        val programName: String,
        val startTimeText: String,
        val endTimeText: String,
        val outputPercent: Int,
        val channels: List<TankLightChannelUi>
    ) : TankAssignedDeviceUi()

    data class Generic(
        override val deviceId: Long,
        override val title: String,
        override val subtitle: String,
        @DrawableRes override val iconRes: Int,
        override val isOnline: Boolean
    ) : TankAssignedDeviceUi()
}

data class TankLightChannelUi(
    val key: TankLightChannelKey,
    val label: String,
    val currentPercent: Int,
    val targetPercent: Int,
    val colorInt: Int
)

enum class TankLightChannelKey {
    WHITE,
    RED,
    GREEN,
    BLUE,
    INTENSITY,
    UV
}