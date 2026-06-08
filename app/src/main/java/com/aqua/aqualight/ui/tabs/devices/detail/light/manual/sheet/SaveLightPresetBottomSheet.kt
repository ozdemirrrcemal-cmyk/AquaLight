package com.aqua.aqualight.ui.tabs.devices.detail.light.manual.sheet

import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import com.aqua.aqualight.databinding.BottomSheetSaveLightPresetBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

object SaveLightPresetBottomSheet {

    fun show(
        fragment: Fragment,
        onSaveClick: (String) -> Unit
    ) {
        val context = fragment.requireContext()
        val dialog = BottomSheetDialog(context)

        val binding = BottomSheetSaveLightPresetBinding.inflate(
            LayoutInflater.from(context)
        )

        binding.btnCancelSavePreset.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnConfirmSavePreset.setOnClickListener {
            val presetName =
                binding.inputPresetName.text
                    ?.toString()
                    .orEmpty()
                    .trim()

            if (presetName.isBlank()) {
                binding.inputPresetName.error = "Preset name is required"
                return@setOnClickListener
            }

            dialog.dismiss()
            onSaveClick(presetName)
        }

        dialog.setContentView(binding.root)
        dialog.show()
    }
}