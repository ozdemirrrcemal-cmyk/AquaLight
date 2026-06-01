package com.aqua.aqualight.ui.tabs.devices.detail.light.quicksetup.model

object LightQuickSetupDays {
    const val MONDAY = 1
    const val TUESDAY = 2
    const val WEDNESDAY = 3
    const val THURSDAY = 4
    const val FRIDAY = 5
    const val SATURDAY = 6
    const val SUNDAY = 7

    val all: Set<Int> =
        setOf(
            MONDAY,
            TUESDAY,
            WEDNESDAY,
            THURSDAY,
            FRIDAY,
            SATURDAY,
            SUNDAY
        )

    val weekdays: Set<Int> =
        setOf(
            MONDAY,
            TUESDAY,
            WEDNESDAY,
            THURSDAY,
            FRIDAY
        )

    val weekend: Set<Int> =
        setOf(
            SATURDAY,
            SUNDAY
        )
}