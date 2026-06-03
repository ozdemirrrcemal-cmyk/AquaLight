package com.aqua.aqualight.ui.tabs.devices.detail.light.sheet

import android.view.LayoutInflater
import com.aqua.aqualight.databinding.BottomSheetLightProgramOptionsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

class LightProgramOptionsSheet(
    private val dialog: BottomSheetDialog,
    private val binding: BottomSheetLightProgramOptionsBinding
) {

    fun show(
        programName: String = "Nature Day",
        subtitle: String = "Every day · 08:00 → 20:00",
        isActive: Boolean = true,
        onActiveChanged: (Boolean) -> Unit = {},
        onDuplicate: () -> Unit = {},
        onRename: () -> Unit = {},
        onDelete: () -> Unit = {}
    ) {
        binding.tvSheetSubtitle.text = "$programName · $subtitle"
        binding.switchProgramActive.isChecked = isActive

        binding.switchProgramActive.setOnCheckedChangeListener { _, checked ->
            onActiveChanged(checked)
        }

        binding.rowDuplicateProgram.setOnClickListener {
            dialog.dismiss()
            onDuplicate()
        }

        binding.rowRenameProgram.setOnClickListener {
            dialog.dismiss()
            onRename()
        }

        binding.rowDeleteProgram.setOnClickListener {
            dialog.dismiss()
            onDelete()
        }

        dialog.show()
    }

    companion object {
        fun create(
            context: android.content.Context
        ): LightProgramOptionsSheet {
            val dialog = BottomSheetDialog(context)
            val binding = BottomSheetLightProgramOptionsBinding.inflate(
                LayoutInflater.from(context)
            )

            dialog.setContentView(binding.root)
            return LightProgramOptionsSheet(dialog, binding)
        }
    }
}