package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.ContentSheetWaterTestPickerBinding
import com.aqua.aqualight.databinding.DialogSettingsBottomSheetBinding
import com.aqua.aqualight.databinding.ItemWaterTestPickerOptionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

internal class WaterTestPickerBottomSheet : BottomSheetDialogFragment() {

    private var _sheetBinding: DialogSettingsBottomSheetBinding? = null
    private val sheetBinding get() = _sheetBinding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _sheetBinding = DialogSettingsBottomSheetBinding.inflate(inflater, container, false)
        return sheetBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sheetBinding.tvSheetTitle.setText(R.string.tank_health_analysis_add_test)

        val contentBinding = ContentSheetWaterTestPickerBinding.inflate(layoutInflater)
        val tankProfile = requireArguments().getString(ARG_TANK_PROFILE).orEmpty()
        val parameterIds = requireArguments()
            .getStringArrayList(ARG_PARAMETER_IDS)
            .orEmpty()
            .mapNotNull { rawId ->
                runCatching { WaterTestParameterId.valueOf(rawId) }.getOrNull()
            }

        parameterIds.forEach { parameterId ->
            val model = WaterTestProfileUiCatalog.model(
                tankProfile = tankProfile,
                id = parameterId,
                importance = WaterTestImportance.ADDITIONAL,
                value = ""
            )
            val option = ItemWaterTestPickerOptionBinding.inflate(
                layoutInflater,
                contentBinding.optionsContainer,
                false
            ).root
            val symbol = model.symbolRes?.let { symbolRes -> getString(symbolRes) }
            option.text = if (symbol.isNullOrBlank()) {
                getString(model.nameRes)
            } else {
                getString(
                    R.string.tank_health_analysis_test_picker_label_format,
                    getString(model.nameRes),
                    symbol
                )
            }
            option.setOnClickListener {
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    bundleOf(RESULT_PARAMETER_ID to parameterId.name)
                )
                dismiss()
            }
            contentBinding.optionsContainer.addView(option)
        }

        contentBinding.btnCancel.setOnClickListener { dismiss() }
        sheetBinding.sheetContentContainer.removeAllViews()
        sheetBinding.sheetContentContainer.addView(contentBinding.root)
    }

    override fun onDestroyView() {
        _sheetBinding = null
        super.onDestroyView()
    }

    companion object {
        const val REQUEST_KEY = "water_test_picker_request"
        const val RESULT_PARAMETER_ID = "water_test_parameter_id"

        private const val ARG_TANK_PROFILE = "tank_profile"
        private const val ARG_PARAMETER_IDS = "parameter_ids"

        fun show(
            fragmentManager: FragmentManager,
            tankProfile: String,
            parameterIds: List<WaterTestParameterId>
        ) {
            if (parameterIds.isEmpty()) return
            WaterTestPickerBottomSheet().apply {
                arguments = bundleOf(
                    ARG_TANK_PROFILE to tankProfile,
                    ARG_PARAMETER_IDS to ArrayList(parameterIds.map { it.name })
                )
            }.show(fragmentManager, TAG)
        }

        private const val TAG = "WaterTestPickerBottomSheet"
    }
}
