package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.sheet

import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetLightProgramActionsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

object LightProgramActionsBottomSheet {

    private const val ENABLED_ALPHA = 1f
    private const val DISABLED_ALPHA = 0.45f

    fun show(
        fragment: Fragment,
        model: LightProgramActionSheetModel,
        onAction: (LightProgramAction) -> Unit
    ) {
        val context = fragment.requireContext()

        val dialog = BottomSheetDialog(context)

        val binding = BottomSheetLightProgramActionsBinding.inflate(
            LayoutInflater.from(context)
        )

        binding.tvProgramActionTitle.text = model.title
        binding.tvProgramActionSubtitle.text = model.subtitle

        binding.btnProgramActionToggle.setText(
            if (model.isEnabled) {
                R.string.light_program_action_disable
            } else {
                R.string.light_program_action_enable
            }
        )

        binding.btnProgramActionSetActive.setText(
            if (model.isActiveProgram) {
                R.string.light_program_action_active
            } else {
                R.string.light_program_action_set_active
            }
        )

        binding.btnProgramActionSetActive.isEnabled = !model.isActiveProgram
        binding.btnProgramActionSetActive.alpha =
            if (model.isActiveProgram) {
                DISABLED_ALPHA
            } else {
                ENABLED_ALPHA
            }

        binding.btnProgramActionEdit.setOnClickListener {
            dialog.dismiss()
            onAction(LightProgramAction.EDIT)
        }

        binding.btnProgramActionPreview.setOnClickListener {
            dialog.dismiss()
            onAction(LightProgramAction.PREVIEW)
        }

        binding.btnProgramActionDuplicate.setOnClickListener {
            dialog.dismiss()
            onAction(LightProgramAction.DUPLICATE)
        }

        binding.btnProgramActionSetActive.setOnClickListener {
            if (!binding.btnProgramActionSetActive.isEnabled) {
                return@setOnClickListener
            }

            dialog.dismiss()
            onAction(LightProgramAction.SET_ACTIVE)
        }

        binding.btnProgramActionToggle.setOnClickListener {
            dialog.dismiss()
            onAction(LightProgramAction.TOGGLE_ENABLED)
        }

        binding.btnProgramActionDelete.setOnClickListener {
            dialog.dismiss()
            onAction(LightProgramAction.DELETE)
        }

        binding.btnProgramActionCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(binding.root)
        dialog.show()
    }
}