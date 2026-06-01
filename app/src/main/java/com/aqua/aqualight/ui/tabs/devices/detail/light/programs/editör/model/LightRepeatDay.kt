package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model

enum class LightRepeatDay(
    val dayValue: Int
) {
    MONDAY(
        dayValue = 1
    ),

    TUESDAY(
        dayValue = 2
    ),

    WEDNESDAY(
        dayValue = 3
    ),

    THURSDAY(
        dayValue = 4
    ),

    FRIDAY(
        dayValue = 5
    ),

    SATURDAY(
        dayValue = 6
    ),

    SUNDAY(
        dayValue = 7
    );

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

        fun fromDayValue(
            value: Int
        ): LightRepeatDay? {
            return entries.firstOrNull { day ->
                day.dayValue == value
            }
        }

        fun fromDayValues(
            values: Set<Int>
        ): Set<LightRepeatDay> {
            return values
                .mapNotNull { value ->
                    fromDayValue(
                        value = value
                    )
                }
                .toSet()
                .ifEmpty {
                    everyDay()
                }
        }
    }
}