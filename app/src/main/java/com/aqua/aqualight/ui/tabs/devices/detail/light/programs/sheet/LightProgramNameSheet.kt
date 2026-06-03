package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.sheet

import android.content.Context
import android.view.LayoutInflater
import com.aqua.aqualight.databinding.BottomSheetLightProgramNameBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

class LightProgramNameSheet private constructor(
    private val context: Context
) {

    fun show(
        title: String,
        subtitle: String,
        primaryButtonText: String,
        initialName: String = "",
        onSave: (name: String) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val binding = BottomSheetLightProgramNameBinding.inflate(
            LayoutInflater.from(context)
        )

        dialog.setContentView(binding.root)

        binding.tvSheetTitle.text = title
        binding.tvSheetSubtitle.text = subtitle
        binding.btnSave.text = primaryButtonText
        binding.etProgramName.setText(initialName)
        binding.etProgramName.setSelection(initialName.length)

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnSave.setOnClickListener {
            val name = binding.etProgramName.text
                ?.toString()
                ?.trim()
                .orEmpty()

            if (name.isBlank()) {
                binding.programNameInputLayout.error = "Program name is required"
                return@setOnClickListener
            }

            binding.programNameInputLayout.error = null
            onSave(name)
            dialog.dismiss()
        }

        dialog.show()
    }

    companion object {
        fun create(context: Context): LightProgramNameSheet {
            return LightProgramNameSheet(context)
        }
    }
}