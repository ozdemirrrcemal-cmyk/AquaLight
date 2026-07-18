package com.aqua.aqualight.ui.common.timeline

import android.graphics.Color
import androidx.annotation.StringRes
import com.aqua.aqualight.R
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

  @StringRes
  fun getStatusTextRes(
    status: TimelineDayStatus
  ): Int {
    return when (status) {
      TimelineDayStatus.TODAY -> R.string.maintenance_today
      TimelineDayStatus.UPCOMING -> R.string.maintenance_tab_upcoming
      TimelineDayStatus.PAST -> R.string.maintenance_empty_text
    }
  }

  fun getStatusTextColor(
    status: TimelineDayStatus
  ): Int {
    return when (status) {
      TimelineDayStatus.TODAY -> com.aqua.aqualight.designsystem.AquaColorTokens.COLOR_4FD6C8
      TimelineDayStatus.UPCOMING -> com.aqua.aqualight.designsystem.AquaColorTokens.COLOR_8FA0B5
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
