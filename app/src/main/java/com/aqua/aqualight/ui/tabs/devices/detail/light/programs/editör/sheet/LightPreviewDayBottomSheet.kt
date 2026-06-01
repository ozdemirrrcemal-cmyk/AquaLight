package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet

import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import com.aqua.aqualight.databinding.BottomSheetLightPreviewDayBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

object LightPreviewDayBottomSheet {

    fun show(
        fragment: Fragment
    ) {
        val context = fragment.requireContext()
        val dialog = BottomSheetDialog(context)

        val binding = BottomSheetLightPreviewDayBinding.inflate(
            LayoutInflater.from(context)
        )

        binding.progressPreviewDay.progress = 0

        binding.tvPreviewDayTime.text = ""
        binding.tvPreviewDayMode.text = ""
        binding.tvPreviewMain.text = ""
        binding.tvPreviewRed.text = ""
        binding.tvPreviewGreen.text = ""
        binding.tvPreviewBlue.text = ""
        binding.tvPreviewWhite.text = ""

        binding.btnPreviewDayClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(binding.root)
        dialog.show()
    }
}