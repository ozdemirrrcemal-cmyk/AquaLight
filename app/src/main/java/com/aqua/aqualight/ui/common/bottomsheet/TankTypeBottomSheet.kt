package com.aqua.aqualight.ui.common.bottomsheet

import android.graphics.Typeface
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ContentSheetTankTypeBinding

object TankTypeBottomSheet {

  fun show(
    fragment: Fragment,
    currentType: String,
    onSave: (
      selectedType: String,
      dismiss: () -> Unit
    ) -> Unit
  ) {
    val contentBinding = ContentSheetTankTypeBinding.inflate(
      fragment.layoutInflater
    )

    var selectedType = currentType.ifBlank {
      "Fish"
    }

    val options = listOf(
      contentBinding.optionFish to "Fish",
      contentBinding.optionShrimp to "Shrimp",
      contentBinding.optionPlanted to "Planted",
      contentBinding.optionMarine to "Marine",
      contentBinding.optionSofties to "Softies",
      contentBinding.optionMixedReef to "Mixed Reef",
      contentBinding.optionSps to "SPS",
      contentBinding.optionCoral to "Coral",
      contentBinding.optionOther to "Other"
    )

    fun renderSelection() {
      options.forEach {
        option ->

        val view = option.first
        val value = option.second

        val selected = value.equals(
          selectedType,
          ignoreCase = true
        )

        view.setTypeface(
          null,
          if (selected) {
            Typeface.BOLD
          } else {
            Typeface.NORMAL
          }
        )

        view.isSelected = selected
        view.setBackgroundResource(
          R.drawable.bg_aqua_selection_row_compact
        )
      }
    }

    options.forEach {
      option ->

      val view = option.first
      val value = option.second

      view.setOnClickListener {
        selectedType = value
        renderSelection()
      }
    }

    renderSelection()

    SettingsContentBottomSheet.show(
      fragment = fragment,
      title = "Tank Type",
      contentView = contentBinding.root
    ) {
      dialog ->

      contentBinding.btnCancel.setOnClickListener {
        dialog.dismiss()
      }

      contentBinding.btnSave.setOnClickListener {
        onSave(
          selectedType
        ) {
          dialog.dismiss()
        }
      }
    }
  }
}