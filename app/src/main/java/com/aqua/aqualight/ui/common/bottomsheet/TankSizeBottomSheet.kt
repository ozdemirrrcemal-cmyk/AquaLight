package com.aqua.aqualight.ui.common.bottomsheet

import androidx.fragment.app.Fragment
import com.aqua.aqualight.databinding.ContentSheetTankSizeBinding
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.roundToInt

data class TankSizeBottomSheetResult(
  val widthCm: Int,
  val lengthCm: Int,
  val heightCm: Int,
  val sizeUnit: String
)

object TankSizeBottomSheet {

  private val sizeFormatter = DecimalFormat("#.##")

  fun show(
    fragment: Fragment,
    currentWidthCm: Int,
    currentLengthCm: Int,
    currentHeightCm: Int,
    currentUnit: String,
    title: String = "Size",
    onInvalidInput: () -> Unit,
    onSave: (
      result: TankSizeBottomSheetResult,
      dismiss: () -> Unit
    ) -> Unit
  ) {
    val contentBinding = ContentSheetTankSizeBinding.inflate(
      fragment.layoutInflater
    )

    var selectedUnit = currentUnit.ifBlank {
      "cm"
    }.lowercase(Locale.US)

    if (selectedUnit != "cm" && selectedUnit != "in") {
      selectedUnit = "cm"
    }

    fun getUnitText(): String {
      return if (selectedUnit == "in") {
        "inches"
      } else {
        "centimeters"
      }
    }

    fun formatValueForCurrentUnit(
      cmValue: Int
    ): String {
      val value = if (selectedUnit == "in") {
        cmValue / 2.54
      } else {
        cmValue.toDouble()
      }

      return sizeFormatter.format(value)
    }

    fun renderUnitOnly() {
      contentBinding.tvUnitValue.text = getUnitText()
    }

    fun fillInitialInputs() {
      contentBinding.inputWidth.setText(
        formatValueForCurrentUnit(currentWidthCm)
      )

      contentBinding.inputLength.setText(
        formatValueForCurrentUnit(currentLengthCm)
      )

      contentBinding.inputHeight.setText(
        formatValueForCurrentUnit(currentHeightCm)
      )
    }

    fun readInputValues(): Triple<Double, Double, Double>? {
      val width = contentBinding.inputWidth.text
        .toString()
        .trim()
        .toDoubleOrNull()

      val length = contentBinding.inputLength.text
        .toString()
        .trim()
        .toDoubleOrNull()

      val height = contentBinding.inputHeight.text
        .toString()
        .trim()
        .toDoubleOrNull()

      var hasError = false

      if (width == null || width <= 0.0) {
        contentBinding.inputWidth.error = "Required"
        hasError = true
      }

      if (length == null || length <= 0.0) {
        contentBinding.inputLength.error = "Required"
        hasError = true
      }

      if (height == null || height <= 0.0) {
        contentBinding.inputHeight.error = "Required"
        hasError = true
      }

      if (hasError) {
        onInvalidInput()
        return null
      }

      return Triple(
        width!!,
        length!!,
        height!!
      )
    }

    fun convertInputValueToCm(
      value: Double
    ): Int {
      return if (selectedUnit == "in") {
        (value * 2.54).roundToInt()
      } else {
        value.roundToInt()
      }.coerceAtLeast(1)
    }

    renderUnitOnly()
    fillInitialInputs()

    contentBinding.unitRow.setOnClickListener {
      selectedUnit = if (selectedUnit == "in") {
        "cm"
      } else {
        "in"
      }

      // Önemli:
      // Unit değişince input değerleri değişmeyecek.
      // Sadece unit yazısı değişecek.
      renderUnitOnly()
    }

    SettingsContentBottomSheet.show(
      fragment = fragment,
      title = title,
      contentView = contentBinding.root
    ) {
      dialog ->

      contentBinding.btnCancel.setOnClickListener {
        dialog.dismiss()
      }

      contentBinding.btnSave.setOnClickListener {
        val values = readInputValues() ?: return@setOnClickListener

        val result = TankSizeBottomSheetResult(
          widthCm = convertInputValueToCm(values.first),
          lengthCm = convertInputValueToCm(values.second),
          heightCm = convertInputValueToCm(values.third),
          sizeUnit = selectedUnit
        )

        onSave(
          result
        ) {
          dialog.dismiss()
        }
      }
    }
  }
}