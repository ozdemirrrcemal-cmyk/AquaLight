package com.aqua.aqualight.ui.common.timeline

import android.graphics.Color
import java.util.Calendar

object TimelineDayResolver {

  fun resolve(
    millis: Long
  ): TimelineDayStatus {
    val targetDayStart = getStartOfDayMillis(millis)
    val todayStart = getStartOfDayMillis(System.currentTimeMillis())

    return when {
      targetDayStart == todayStart -> {
        TimelineDayStatus.TODAY
      }

      targetDayStart > todayStart -> {
        TimelineDayStatus.UPCOMING
      }

      else -> {
        TimelineDayStatus.PAST
      }
    }
  }

  fun getStatusText(
    status: TimelineDayStatus
  ): String {
    return when (status) {
      TimelineDayStatus.TODAY -> "Today"
      TimelineDayStatus.UPCOMING -> "Upcoming"
      TimelineDayStatus.PAST -> ""
    }
  }

  fun getStatusTextColor(
  status: TimelineDayStatus
): Int {
  return when (status) {
    TimelineDayStatus.TODAY -> Color.parseColor("#45CDBD")
    TimelineDayStatus.UPCOMING -> Color.parseColor("#8FA0B5")
    TimelineDayStatus.PAST -> Color.TRANSPARENT
  }
}
  private fun getStartOfDayMillis(
    millis: Long
  ): Long {
    return Calendar.getInstance().apply {
      timeInMillis = millis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis
  }
}