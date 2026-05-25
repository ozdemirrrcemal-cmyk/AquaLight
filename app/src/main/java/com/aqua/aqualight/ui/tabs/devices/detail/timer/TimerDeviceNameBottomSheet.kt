package com.aqua.aqualight.ui.tabs.devices.detail.timer

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.fragment.app.Fragment
import com.aqua.aqualight.databinding.BottomSheetTimerDeviceNameBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class TimerDeviceNameBottomSheet(
    private val fragment: Fragment,
    private val currentName: String,
    private val fallbackName: String,
    private val onSave: (
        newName: String,
        sheet: TimerDeviceNameBottomSheet
    ) -> Unit
) {

    private lateinit var dialog: BottomSheetDialog
    private lateinit var binding: BottomSheetTimerDeviceNameBinding

    private var isSaving: Boolean = false

    fun show() {
        val context = fragment.requireContext()

        binding = BottomSheetTimerDeviceNameBinding.inflate(
            fragment.layoutInflater
        )

        dialog = BottomSheetDialog(
            context
        )

        dialog.setContentView(
            binding.root
        )

        bindInitialState()
        bindActions()

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )

            bottomSheet?.background = ColorDrawable(
                Color.TRANSPARENT
            )

            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(
                    sheet
                )

                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = true
            }
        }

        dialog.show()
    }

    private fun bindInitialState() {
        binding.inputDeviceNameLayout.hint = fallbackName.ifBlank {
            "Device name"
        }

        binding.etDeviceName.setText(
            currentName
        )

        binding.etDeviceName.setSelection(
            binding.etDeviceName.text?.length ?: 0
        )

        binding.tvSheetError.visibility = View.GONE
    }

    private fun bindActions() {
        binding.btnCancel.setOnClickListener {
            if (!isSaving) {
                dialog.dismiss()
            }
        }

        binding.btnSave.setOnClickListener {
            save()
        }
    }

    private fun save() {
        if (isSaving) {
            return
        }

        binding.tvSheetError.visibility = View.GONE

        val newName = binding.etDeviceName.text
            ?.toString()
            ?.trim()
            .orEmpty()

        if (newName.isBlank()) {
            showError(
                message = "Device name cannot be empty."
            )
            return
        }

        setSaving(
            saving = true
        )

        onSave(
            newName,
            this
        )
    }

    fun showSaveError(
        message: String
    ) {
        setSaving(
            saving = false
        )

        showError(
            message = message
        )
    }

    fun closeAfterSave() {
        dialog.dismiss()
    }

    private fun showError(
        message: String
    ) {
        binding.tvSheetError.text = message
        binding.tvSheetError.visibility = View.VISIBLE
    }

    private fun setSaving(
        saving: Boolean
    ) {
        isSaving = saving

        dialog.setCancelable(
            !saving
        )

        dialog.setCanceledOnTouchOutside(
            !saving
        )

        binding.etDeviceName.isEnabled = !saving
        binding.inputDeviceNameLayout.isEnabled = !saving
        binding.btnCancel.isEnabled = !saving
        binding.btnSave.isEnabled = !saving

        binding.btnSave.text = if (saving) {
            "Saving..."
        } else {
            "Save"
        }
    }
}