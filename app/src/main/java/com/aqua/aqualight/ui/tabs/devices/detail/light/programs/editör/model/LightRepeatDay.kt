package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model

enum class LightRepeatDay {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY;

    companion object {
        fun everyDay(): Set<LightRepeatDay> {
            return entries.toSet()
        }

        fun weekdays(): Set<LightRepeatDay> {
            return setOf(
                MONDAY,
                TUESDAY,
                WEDNESDAY,
                THURSDAY,
                FRIDAY
            )
        }

        fun weekend(): Set<LightRepeatDay> {
            return setOf(
                SATURDAY,
                SUNDAY
            )
        }
    }
}