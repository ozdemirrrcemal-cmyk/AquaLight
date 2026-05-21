package com.aqua.aqualight.ui.common.bottomsheet

import androidx.fragment.app.Fragment
import com.aqua.aqualight.databinding.ContentSheetSetupDateBinding
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

object SetupDateBottomSheet {

  fun show(
    fragment: Fragment,
    currentMillis: Long?,
    minYear: Int,
    maxYear: Int,
    monthLocale: Locale = Locale.getDefault(),
    onSave: (
      selectedMillis: Long,
      dismiss: () -> Unit
    ) -> Unit
  ) {
    val contentBinding = ContentSheetSetupDateBinding.inflate(
      fragment.layoutInflater
    )

    val calendar = Calendar.getInstance().apply {
      currentMillis?.let {
        timeInMillis = it
      }
    }

    val monthNames = Array(12) {
      index ->
      DateFormatSymbols(monthLocale)
        .months[index]
        .replaceFirstChar {
          char ->
          if (char.isLowerCase()) {
            char.titlecase(monthLocale)
          } else {
            char.toString()
          }
        }
    }

    contentBinding.dayPicker.apply {
      wrapSelectorWheel = false
      minValue = 1
      maxValue = 31
      value = calendar.get(Calendar.DAY_OF_MONTH)
    }

    contentBinding.monthPicker.apply {
      wrapSelectorWheel = false
      minValue = 0
      maxValue = 11
      displayedValues = monthNames
      value = calendar.get(Calendar.MONTH)
    }

    contentBinding.yearPicker.apply {
      wrapSelectorWheel = false
      this.minValue = minYear
      this.maxValue = maxYear
      value = calendar.get(Calendar.YEAR).coerceIn(
        minYear,
        maxYear
      )
    }

    fun updateDayMax() {
      val tempCalendar = Calendar.getInstance().apply {
        set(
          Calendar.YEAR,
          contentBinding.yearPicker.value
        )

        set(
          Calendar.MONTH,
          contentBinding.monthPicker.value
        )

        set(
          Calendar.DAY_OF_MONTH,
          1
        )
      }

      val maxDay = tempCalendar.getActualMaximum(
        Calendar.DAY_OF_MONTH
      )

      contentBinding.dayPicker.maxValue = maxDay

      if (contentBinding.dayPicker.value > maxDay) {
        contentBinding.dayPicker.value = maxDay
      }
    }

    contentBinding.monthPicker.setOnValueChangedListener {
      _, _, _ ->
      updateDayMax()
    }

    contentBinding.yearPicker.setOnValueChangedListener {
      _, _, _ ->
      updateDayMax()
    }

    updateDayMax()

    SettingsContentBottomSheet.show(
      fragment = fragment,
      title = "Setup Date",
      contentView = contentBinding.root
    ) {
      dialog ->

      contentBinding.btnCancel.setOnClickListener {
        dialog.dismiss()
      }

      contentBinding.btnSave.setOnClickListener {
        val selectedCalendar = Calendar.getInstance().apply {
          set(
            Calendar.YEAR,
            contentBinding.yearPicker.value
          )

          set(
            Calendar.MONTH,
            contentBinding.monthPicker.value
          )

          set(
            Calendar.DAY_OF_MONTH,
            contentBinding.dayPicker.value
          )

          set(
            Calendar.HOUR_OF_DAY,
            0
          )

          set(
            Calendar.MINUTE,
            0
          )

          set(
            Calendar.SECOND,
            0
          )

          set(
            Calendar.MILLISECOND,
            0
          )
        }

        onSave(
          selectedCalendar.timeInMillis
        ) {
          dialog.dismiss()
        }
      }
    }
  }
}