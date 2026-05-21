package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ContentSheetSetupDateBinding
import com.aqua.aqualight.databinding.ContentSheetTankSizeBinding
import com.aqua.aqualight.databinding.FragmentTankInfoBinding
import com.aqua.aqualight.ui.common.bottomsheet.SettingsContentBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TankStyleBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TankTypeBottomSheet
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import com.aqua.aqualight.ui.common.bottomsheet.SetupDateBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TankSizeBottomSheet

class TankInfoFragment : Fragment(R.layout.fragment_tank_info), TankStepFragment {

    private var _binding: FragmentTankInfoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateTankViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    private val volumeFormatter = DecimalFormat(
        "#.##",
        DecimalFormatSymbols(Locale.US)
    )

    private val sizeFormatter = DecimalFormat(
        "#0.##",
        DecimalFormatSymbols(Locale.US)
    )

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        _binding = FragmentTankInfoBinding.bind(view)

        setupClickListeners()
        renderDetails()
    }

    private fun setupClickListeners() {
        binding.rowSetupDate.setOnClickListener {
            showSetupDateSheet()
        }

        binding.rowSize.setOnClickListener {
            showSizeSheet()
        }

        binding.rowVolume.setOnClickListener {
            toggleVolumeUnit()
        }

        binding.rowTankType.setOnClickListener {
            showTankTypeSheet()
        }

        binding.rowStyle.setOnClickListener {
            showStyleSheet()
        }
    }

    private fun renderDetails() {
        val draft = viewModel.tankDraft

        binding.tvSetupDateValue.text = formatSetupDate(draft.setupDateMillis)
        binding.tvSetupDateValue.setTextColor(
            if (draft.setupDateMillis == null) {
                Color.parseColor("#7F91AA")
            } else {
                Color.WHITE
            }
        )

        binding.tvSizeLabel.text = formatSizeTitle()
        binding.tvSizeValue.text = formatSize()

        binding.tvVolumeValue.text = formatVolume()
        binding.tvTankTypeValue.text = draft.tankType

        if (draft.tankStyle.isBlank()) {
            binding.tvStyleValue.text = "Not selected"
            binding.tvStyleValue.setTextColor(Color.parseColor("#7F91AA"))
        } else {
            binding.tvStyleValue.text = draft.tankStyle
            binding.tvStyleValue.setTextColor(Color.WHITE)
        }
    }

    private fun showSettingsBottomSheet(
        title: String,
        contentView: View,
        onDialogReady: ((BottomSheetDialog) -> Unit)? = null
    ) {
        SettingsContentBottomSheet.show(
            fragment = this,
            title = title,
            contentView = contentView,
            onDialogReady = onDialogReady
        )
    }

    private fun showSetupDateSheet() {
    val currentYear = Calendar.getInstance().get(
      Calendar.YEAR
    )

     SetupDateBottomSheet.show(
    fragment = this,
    currentMillis = viewModel.tankDraft.setupDateMillis,
    minYear = 2000,
    maxYear = currentYear + 10,
    monthLocale = Locale.ENGLISH,
    onSave = {
      selectedMillis,
      dismiss ->

      viewModel.updateSetupDate(
        selectedMillis
      )

      renderDetails()

      dismiss()
    }
  )
}

private fun showSizeSheet() {
  val draft = viewModel.tankDraft

  TankSizeBottomSheet.show(
    fragment = this,
    currentWidthCm = draft.widthCm,
    currentLengthCm = draft.lengthCm,
    currentHeightCm = draft.heightCm,
    currentUnit = draft.sizeUnit,
    title = "Tank Size",
    onInvalidInput = {
      // Create ekranında snackbar istemiyorsak boş kalabilir.
    },
    onSave = {
      result,
      dismiss ->

      viewModel.updateTankSize(
        widthCm = result.widthCm,
        lengthCm = result.lengthCm,
        heightCm = result.heightCm,
        sizeUnit = result.sizeUnit
      )

      renderDetails()

      dismiss()
    }
  )
}

    private fun showTankTypeSheet() {
        TankTypeBottomSheet.show(
            fragment = this,
            currentType = viewModel.tankDraft.tankType,
            onSave = { selectedType, dismiss ->

                viewModel.updateTankType(
                    selectedType
                )

                renderDetails()

                dismiss()
            }
        )
    }

    private fun showStyleSheet() {
        TankStyleBottomSheet.show(
            fragment = this,
            currentStyle = viewModel.tankDraft.tankStyle,
            onSave = { selectedStyle, dismiss ->

                viewModel.updateTankStyle(
                    selectedStyle
                )

                renderDetails()

                dismiss()
            }
        )
    }

    private fun toggleVolumeUnit() {
        val currentUnit = viewModel.tankDraft.volumeUnit

        val newUnit = if (currentUnit == "L") {
            "gal"
        } else {
            "L"
        }

        viewModel.updateVolumeUnit(newUnit)
        renderDetails()
    }

    private fun formatSetupDate(
        millis: Long?
    ): String {
        if (millis == null) {
            return "Not selected"
        }

        return SimpleDateFormat(
            "dd MMM yyyy",
            Locale.ENGLISH
        ).format(Date(millis))
    }

    private fun formatSizeTitle(): String {
        val draft = viewModel.tankDraft

        return if (draft.sizeUnit.equals("in", ignoreCase = true)) {
            "Size (in)"
        } else {
            "Size (cm)"
        }
    }

    private fun formatSize(): String {
        val draft = viewModel.tankDraft

        return if (draft.sizeUnit.equals("in", ignoreCase = true)) {
            val widthIn = draft.widthCm / 2.54
            val lengthIn = draft.lengthCm / 2.54
            val heightIn = draft.heightCm / 2.54

            "${sizeFormatter.format(widthIn)} W × ${sizeFormatter.format(lengthIn)} L × ${sizeFormatter.format(heightIn)} H"
        } else {
            "${draft.widthCm} W × ${draft.lengthCm} L × ${draft.heightCm} H"
        }
    }

    private fun formatVolume(): String {
        val draft = viewModel.tankDraft

        val liters = (
            draft.widthCm *
                draft.lengthCm *
                draft.heightCm
            ) / 1000.0

        return if (draft.volumeUnit == "gal") {
            "${volumeFormatter.format(liters * 0.264172)} gal"
        } else {
            "${volumeFormatter.format(liters)} L"
        }
    }

    override fun validateAndSave(): Boolean {
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}