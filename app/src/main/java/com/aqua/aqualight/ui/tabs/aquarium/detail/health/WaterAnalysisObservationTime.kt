package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import java.util.Calendar

/** Picker results carry a complete instant; only the component selected by the user is applied. */
internal object WaterAnalysisObservationTime {
    fun withDate(current: Calendar, pickedMillis: Long): Calendar =
        Calendar.getInstance(current.timeZone).apply {
            timeInMillis = pickedMillis
        }.let { picked ->
            current.apply {
                set(Calendar.YEAR, picked.get(Calendar.YEAR))
                set(Calendar.MONTH, picked.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, picked.get(Calendar.DAY_OF_MONTH))
            }
        }

    fun withTime(current: Calendar, pickedMillis: Long): Calendar =
        Calendar.getInstance(current.timeZone).apply {
            timeInMillis = pickedMillis
        }.let { picked ->
            current.apply {
                set(Calendar.HOUR_OF_DAY, picked.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, picked.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
}
