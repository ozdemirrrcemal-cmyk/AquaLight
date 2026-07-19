package com.aqua.aqualight.ui.tabs.aquarium.create.steps

import androidx.core.content.ContextCompat
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.navGraphViewModels
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentTankInfoBinding
import com.aqua.aqualight.ui.common.bottomsheet.TankSettingsEditorBottomSheet
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
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankInfoBinding.bind(view)
        setupEditorResultListener()
        setupClickListeners()
        renderDetails()
    }

    private fun setupEditorResultListener() {
        childFragmentManager.setFragmentResultListener(
            TankSettingsEditorBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(TankSettingsEditorBottomSheet.RESULT_STATUS) !=
                TankSettingsEditorBottomSheet.RESULT_SAVED
            ) return@setFragmentResultListener

            val mode = result.getString(TankSettingsEditorBottomSheet.RESULT_MODE)
                ?.let { value ->
                    runCatching {
                        TankSettingsEditorBottomSheet.Mode.valueOf(value)
                    }.getOrNull()
                }
                ?: return@setFragmentResultListener

            when (mode) {
                TankSettingsEditorBottomSheet.Mode.SETUP_DATE -> {
                    viewModel.updateSetupDate(
                        AquariumDatePolicy.epochDayFromPickerMillis(
                            result.getLong(TankSettingsEditorBottomSheet.RESULT_MILLIS)
                        )
                    )
                }

                TankSettingsEditorBottomSheet.Mode.SIZE -> {
                    val widthCm = result.getInt(TankSettingsEditorBottomSheet.RESULT_WIDTH_CM)
                    val lengthCm = result.getInt(TankSettingsEditorBottomSheet.RESULT_LENGTH_CM)
                    val heightCm = result.getInt(TankSettingsEditorBottomSheet.RESULT_HEIGHT_CM)
                    val sizeUnit = result.getString(
                        TankSettingsEditorBottomSheet.RESULT_UNIT
                    ).orEmpty()

                    if (!AquariumMeasurementPolicy.areValidDimensions(
                            widthCm = widthCm,
                            lengthCm = lengthCm,
                            heightCm = heightCm
                        ) || sizeUnit.isBlank()
                    ) {
                        showSnackBar(
                            message = getString(R.string.aquarium_validation_invalid_tank_size),
                            type = BaseActivity.SnackType.WARNING
                        )
                        return@setFragmentResultListener
                    }

                    viewModel.updateTankSize(
                        widthCm = widthCm,
                        lengthCm = lengthCm,
                        heightCm = heightCm,
                        sizeUnit = sizeUnit
                    )
                }

                TankSettingsEditorBottomSheet.Mode.TYPE -> {
                    result.getString(TankSettingsEditorBottomSheet.RESULT_TEXT)
                        ?.takeIf(String::isNotBlank)
                        ?.let(viewModel::updateTankType)
                }

                TankSettingsEditorBottomSheet.Mode.STYLE -> {
                    result.getString(TankSettingsEditorBottomSheet.RESULT_TEXT)
                        ?.takeIf(String::isNotBlank)
                        ?.let(viewModel::updateTankStyle)
                }

                TankSettingsEditorBottomSheet.Mode.NAME,
                TankSettingsEditorBottomSheet.Mode.IDEA -> Unit
            }

            renderDetails()
        }
    }

    private fun setupClickListeners() {
        binding.rowSetupDate.setOnClickListener { showSetupDateSheet() }
        binding.rowSize.setOnClickListener { showSizeSheet() }
        binding.rowVolume.setOnClickListener { toggleVolumeUnit() }
        binding.rowTankType.setOnClickListener { showTankTypeSheet() }
        binding.rowStyle.setOnClickListener { showStyleSheet() }
    }

    private fun renderDetails() {
        val draft = viewModel.tankDraft

        binding.tvSetupDateValue.text = formatSetupDate(draft.setupDateEpochDay)
        binding.tvSetupDateValue.setTextColor(
            if (draft.setupDateEpochDay == null) {
                ContextCompat.getColor(requireContext(), R.color.aqua_content_placeholder)
            } else {
                ContextCompat.getColor(requireContext(), R.color.aqua_content_on_dark)
            }
        )

        binding.tvSizeLabel.text = formatSizeTitle()
        binding.tvSizeValue.text = formatSize()
        binding.tvVolumeValue.text = formatVolume()

        binding.tvTankTypeValue.text = draft.tankType.ifBlank {
            getString(R.string.aquarium_common_not_selected)
        }
        binding.tvTankTypeValue.setTextColor(
            if (draft.tankType.isBlank()) {
                ContextCompat.getColor(requireContext(), R.color.aqua_content_placeholder)
            } else {
                ContextCompat.getColor(requireContext(), R.color.aqua_content_on_dark)
            }
        )

        if (draft.tankStyle.isBlank()) {
            binding.tvStyleValue.text = getString(R.string.aquarium_common_not_selected)
            binding.tvStyleValue.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.aqua_content_placeholder)
            )
        } else {
            binding.tvStyleValue.text = draft.tankStyle
            binding.tvStyleValue.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.aqua_content_on_dark)
            )
        }
    }

    private fun showSetupDateSheet() {
        TankSettingsEditorBottomSheet.show(
            fragmentManager = childFragmentManager,
            mode = TankSettingsEditorBottomSheet.Mode.SETUP_DATE,
            title = getString(R.string.aquarium_setup_date_title),
            currentMillis = AquariumDatePolicy.pickerMillis(
                viewModel.tankDraft.setupDateEpochDay
            ),
            minYear = AquariumDatePolicy.minSetupYear(),
            maxYear = AquariumDatePolicy.maxSetupYear(),
            locale = AquariumDatePolicy.setupDateLocale(requireContext())
        )
    }

    private fun showSizeSheet() {
        val draft = viewModel.tankDraft
        TankSettingsEditorBottomSheet.show(
            fragmentManager = childFragmentManager,
            mode = TankSettingsEditorBottomSheet.Mode.SIZE,
            title = getString(R.string.aquarium_tank_size_title),
            validationMessage = getString(R.string.aquarium_validation_invalid_tank_size),
            widthCm = draft.widthCm,
            lengthCm = draft.lengthCm,
            heightCm = draft.heightCm,
            currentUnit = draft.sizeUnit
        )
    }

    private fun showTankTypeSheet() {
        TankSettingsEditorBottomSheet.show(
            fragmentManager = childFragmentManager,
            mode = TankSettingsEditorBottomSheet.Mode.TYPE,
            title = getString(R.string.aquarium_tank_type_title),
            currentText = viewModel.tankDraft.tankType
        )
    }

    private fun showStyleSheet() {
        TankSettingsEditorBottomSheet.show(
            fragmentManager = childFragmentManager,
            mode = TankSettingsEditorBottomSheet.Mode.STYLE,
            title = getString(R.string.aquarium_tank_style_title),
            currentText = viewModel.tankDraft.tankStyle,
            validationMessage = getString(R.string.aquarium_error_tank_style_save_failed)
        )
    }

    private fun toggleVolumeUnit() {
        val currentUnit = viewModel.tankDraft.volumeUnit
        val newUnit = if (currentUnit == "L") "gal" else "L"
        viewModel.updateVolumeUnit(newUnit)
        renderDetails()
    }

    private fun formatSetupDate(epochDay: Long?): String {
        return AquariumDatePolicy.formatSetupDate(
            context = requireContext(),
            epochDay = epochDay,
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
            context = requireContext(),
            widthCm = draft.widthCm,
            lengthCm = draft.lengthCm,
            heightCm = draft.heightCm,
            sizeUnit = draft.sizeUnit
        )
    }

    private fun formatVolume(): String {
        val draft = viewModel.tankDraft
        return AquariumDimensionFormatter.volumeText(
            context = requireContext(),
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
        (activity as? BaseActivity)?.showSnackBar(message = message, type = type)
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
        _binding = null
        super.onDestroyView()
    }
}
