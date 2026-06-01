package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.sheet

import android.view.LayoutInflater
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetLightPointEditorBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.math.roundToInt

object LightPointEditorBottomSheet {

    private const val MIN_PERCENT = 0
    private const val MAX_PERCENT = 100
    private const val POINT_STEP_MINUTES = 15
    private const val MINUTES_IN_DAY = 24 * 60

    fun show(
        fragment: Fragment,
        model: LightPointEditorSheetModel,
        onSave: (
            pointName: String,
            timeLabel: String,
            intensityPercent: Int
        ) -> Unit,
        onDelete: () -> Unit
    ) {
        val context = fragment.requireContext()
        val dialog = BottomSheetDialog(context)

        val binding = BottomSheetLightPointEditorBinding.inflate(
            LayoutInflater.from(context)
        )

        var selectedMinutes = timeToMinutes(model.timeLabel)
        var selectedIntensity = model.intensityPercent
            ?.coerceIn(MIN_PERCENT, MAX_PERCENT)
            ?: MIN_PERCENT

        binding.tvPointTitle.setText(model.titleRes)
        binding.tvPointLabel.setText(model.descriptionRes)
        binding.btnPointSave.setText(model.saveButtonTextRes)

        binding.inputPointName.setText(model.pointName)
        binding.inputPointName.setSelection(binding.inputPointName.text.length)
        binding.inputPointName.isEnabled = model.canRename
        binding.inputPointName.alpha =
            if (model.canRename) {
                1f
            } else {
                0.65f
            }

        binding.tvPointNameHelper.setText(
            if (model.canRename) {
                R.string.light_point_editor_helper_rename
            } else {
                R.string.light_point_editor_helper_locked
            }
        )

        binding.tvPointTime.text = model.timeLabel

        binding.sliderPointIntensity.valueFrom = MIN_PERCENT.toFloat()
        binding.sliderPointIntensity.valueTo = MAX_PERCENT.toFloat()
        binding.sliderPointIntensity.stepSize = 1f

        if (model.intensityPercent != null) {
            binding.sliderPointIntensity.value = selectedIntensity.toFloat()
            binding.tvPointIntensityValue.text = context.getString(
                R.string.common_percent_value,
                selectedIntensity
            )
        } else {
            binding.tvPointIntensityValue.text = ""
        }

        binding.btnPointDelete.visibility =
            if (model.canDelete) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }

        binding.btnPointTimeMinus.setOnClickListener {
            selectedMinutes = (selectedMinutes - POINT_STEP_MINUTES)
                .coerceAtLeast(0)

            binding.tvPointTime.text = minutesToTime(selectedMinutes)
        }

        binding.btnPointTimePlus.setOnClickListener {
            selectedMinutes = (selectedMinutes + POINT_STEP_MINUTES)
                .coerceAtMost(MINUTES_IN_DAY - POINT_STEP_MINUTES)

            binding.tvPointTime.text = minutesToTime(selectedMinutes)
        }

        binding.sliderPointIntensity.addOnChangeListener { _, value, _ ->
            selectedIntensity = value.roundToInt()
                .coerceIn(MIN_PERCENT, MAX_PERCENT)

            binding.tvPointIntensityValue.text = context.getString(
                R.string.common_percent_value,
                selectedIntensity
            )
        }

        binding.btnPointSave.setOnClickListener {
            val pointName =
                if (model.canRename) {
                    binding.inputPointName.text.toString().trim()
                } else {
                    model.pointName
                }

            if (pointName.isBlank()) {
                binding.inputPointName.error = context.getString(
                    R.string.light_point_editor_error_empty_name
                )
                return@setOnClickListener
            }

            dialog.dismiss()

            onSave(
                pointName,
                minutesToTime(selectedMinutes),
                selectedIntensity
            )
        }

        binding.btnPointDelete.setOnClickListener {
            dialog.dismiss()
            onDelete()
        }

        binding.btnPointCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(binding.root)
        dialog.show()
    }

    private fun timeToMinutes(
        time: String
    ): Int {
        val parts = time.split(":")

        if (parts.size != 2) {
            return 0
        }

        val hour = parts[0].toIntOrNull() ?: 0
        val minute = parts[1].toIntOrNull() ?: 0

        return (hour * 60 + minute).coerceIn(
            0,
            MINUTES_IN_DAY - 1
        )
    }

    private fun minutesToTime(
        minutes: Int
    ): String {
        val safeMinutes = minutes.coerceIn(
            0,
            MINUTES_IN_DAY - 1
        )

        val hour = safeMinutes / 60
        val minute = safeMinutes % 60

        return "%02d:%02d".format(
            hour,
            minute
        )
    }
}