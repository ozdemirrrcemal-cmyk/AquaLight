package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.navGraphViewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentTankInfoBinding
import com.aqua.aqualight.ui.common.bottomsheet.SetupDateBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TankSizeBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TankStyleBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TankTypeBottomSheet
import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumDatePolicy
import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumDimensionFormatter
import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumMeasurementPolicy
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel

class TankInfoFragment :
    Fragment(R.layout.fragment_tank_info),
    TankStepFragment {

    private var _binding: FragmentTankInfoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateTankViewModel by navGraphViewModels(R.id.nav_create_tank)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentTankInfoBinding.bind(view)

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
        val draft =
            viewModel.tankDraft

        binding.tvSetupDateValue.text =
            formatSetupDate(
                draft.setupDateMillis
            )

        binding.tvSetupDateValue.setTextColor(
            if (draft.setupDateMillis == null) {
                Color.parseColor("#7F91AA")
            } else {
                Color.WHITE
            }
        )

        binding.tvSizeLabel.text =
            formatSizeTitle()

        binding.tvSizeValue.text =
            formatSize()

        binding.tvVolumeValue.text =
            formatVolume()

        binding.tvTankTypeValue.text =
            draft.tankType.ifBlank {
                getString(R.string.aquarium_common_not_selected)
            }

        binding.tvTankTypeValue.setTextColor(
            if (draft.tankType.isBlank()) {
                Color.parseColor("#7F91AA")
            } else {
                Color.WHITE
            }
        )

        if (draft.tankStyle.isBlank()) {
            binding.tvStyleValue.text =
                getString(R.string.aquarium_common_not_selected)

            binding.tvStyleValue.setTextColor(
                Color.parseColor("#7F91AA")
            )
        } else {
            binding.tvStyleValue.text =
                draft.tankStyle

            binding.tvStyleValue.setTextColor(
                Color.WHITE
            )
        }
    }

    private fun showSetupDateSheet() {
        SetupDateBottomSheet.show(
            fragment = this,
            currentMillis = viewModel.tankDraft.setupDateMillis,
            minYear = AquariumDatePolicy.minSetupYear(),
            maxYear = AquariumDatePolicy.maxSetupYear(),
            monthLocale = AquariumDatePolicy.setupDateLocale,
            onSave = { selectedMillis, dismiss ->

                viewModel.updateSetupDate(
                    selectedMillis
                )

                renderDetails()

                dismiss()
            }
        )
    }

    private fun showSizeSheet() {
        val draft =
            viewModel.tankDraft

        TankSizeBottomSheet.show(
            fragment = this,
            currentWidthCm = draft.widthCm,
            currentLengthCm = draft.lengthCm,
            currentHeightCm = draft.heightCm,
            currentUnit = draft.sizeUnit,
            title = getString(R.string.aquarium_tank_size_title),
            onInvalidInput = {
                showSnackBar(
                    message = getString(R.string.aquarium_validation_invalid_tank_size),
                    type = BaseActivity.SnackType.WARNING
                )
            },
            onSave = { result, dismiss ->

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
        val currentUnit =
            viewModel.tankDraft.volumeUnit

        val newUnit =
            if (currentUnit == "L") {
                "gal"
            } else {
                "L"
            }

        viewModel.updateVolumeUnit(
            newUnit
        )

        renderDetails()
    }

    private fun formatSetupDate(
        millis: Long?
    ): String {
        return AquariumDatePolicy.formatSetupDate(
            millis = millis,
            emptyText = getString(R.string.aquarium_common_not_selected)
        )
    }

    private fun formatSizeTitle(): String {
        return AquariumDimensionFormatter.sizeTitle(
            context = requireContext(),
            sizeUnit = viewModel.tankDraft.sizeUnit
        )
    }

    private fun formatSize(): String {
        val draft = viewModel.tankDraft

        return AquariumDimensionFormatter.sizeText(
            widthCm = draft.widthCm,
            lengthCm = draft.lengthCm,
            heightCm = draft.heightCm,
            sizeUnit = draft.sizeUnit
        )
    }

    private fun formatVolume(): String {
        val draft = viewModel.tankDraft

        return AquariumDimensionFormatter.volumeText(
            widthCm = draft.widthCm,
            lengthCm = draft.lengthCm,
            heightCm = draft.heightCm,
            volumeUnit = draft.volumeUnit
        )
    }

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType = BaseActivity.SnackType.NORMAL
    ) {
        (activity as? BaseActivity)?.showSnackBar(
            message = message,
            type = type
        )
    }

    override fun validateAndSave(): Boolean {
        val draft = viewModel.tankDraft

        val isValidSize = AquariumMeasurementPolicy.areValidDimensions(
            widthCm = draft.widthCm,
            lengthCm = draft.lengthCm,
            heightCm = draft.heightCm
        )

        if (!isValidSize) {
            showSnackBar(
                message = getString(R.string.aquarium_validation_invalid_tank_size),
                type = BaseActivity.SnackType.WARNING
            )
            return false
        }

        if (draft.tankType.isBlank()) {
            showSnackBar(
                message = getString(R.string.aquarium_validation_tank_type_required),
                type = BaseActivity.SnackType.WARNING
            )
            return false
        }

        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding =
            null
    }
}