package com.aqua.aqualight.ui.common.bottomsheet

import android.graphics.Typeface
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ContentSheetTankStyleBinding

object TankStyleBottomSheet {

  fun show(
    fragment: Fragment,
    currentStyle: String,
    onSave: (
      selectedStyle: String,
      dismiss: () -> Unit
    ) -> Unit
  ) {
    val contentBinding = ContentSheetTankStyleBinding.inflate(
      fragment.layoutInflater
    )

    val safeCurrentStyle = currentStyle.ifBlank {
      "Nature Aquarium"
    }

    contentBinding.inputStyle.setText(safeCurrentStyle)

    val options = listOf(
      contentBinding.optionNatureAquarium to "Nature Aquarium",
      contentBinding.optionIwagumi to "Iwagumi",
      contentBinding.optionDutch to "Dutch",
      contentBinding.optionJungle to "Jungle",
      contentBinding.optionBiotope to "Biotope",
      contentBinding.optionBlackwater to "Blackwater",
      contentBinding.optionForest to "Forest",
      contentBinding.optionMountain to "Mountain",
      contentBinding.optionIsland to "Island"
    )

    fun renderSelection() {
      val inputValue = contentBinding.inputStyle.text
        .toString()
        .trim()

      options.forEach {
        option ->

        val view = option.first
        val value = option.second

        val selected = value.equals(
          inputValue,
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
        contentBinding.inputStyle.setText(value)

        contentBinding.inputStyle.setSelection(
          contentBinding.inputStyle.text?.length ?: 0
        )

        renderSelection()
      }
    }

    contentBinding.inputStyle.setOnFocusChangeListener {
      _, _ ->
      renderSelection()
    }

    contentBinding.inputStyle.addTextChangedListener(
      object : android.text.TextWatcher {
        override fun beforeTextChanged(
          s: CharSequence?,
          start: Int,
          count: Int,
          after: Int
        ) = Unit

        override fun onTextChanged(
          s: CharSequence?,
          start: Int,
          before: Int,
          count: Int
        ) {
          renderSelection()
        }

        override fun afterTextChanged(
          s: android.text.Editable?
        ) = Unit
      }
    )

    renderSelection()

    SettingsContentBottomSheet.show(
      fragment = fragment,
      title = "Tank Style",
      contentView = contentBinding.root
    ) {
      dialog ->

      contentBinding.btnCancel.setOnClickListener {
        dialog.dismiss()
      }

      contentBinding.btnSave.setOnClickListener {
        val selectedStyle = contentBinding.inputStyle.text
          .toString()
          .trim()

        if (selectedStyle.isBlank()) {
          contentBinding.inputStyle.error = "Required"
          return@setOnClickListener
        }

        onSave(
          selectedStyle
        ) {
          dialog.dismiss()
        }
      }
    }
  }
}