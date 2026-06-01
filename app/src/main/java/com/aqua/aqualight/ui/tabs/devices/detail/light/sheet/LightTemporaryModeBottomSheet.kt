package com.aqua.aqualight.ui.tabs.devices.detail.light.sheet

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.BottomSheetLightTemporaryModeBinding
import com.aqua.aqualight.ui.tabs.devices.detail.light.model.TemporaryLightDurationOption
import com.aqua.aqualight.ui.tabs.devices.detail.light.model.TemporaryLightSceneOption
import com.google.android.material.bottomsheet.BottomSheetDialog

object LightTemporaryModeBottomSheet {

    fun show(
        fragment: Fragment,
        onApply: (
            scene: TemporaryLightSceneOption,
            duration: TemporaryLightDurationOption
        ) -> Unit,
        onRestoreAuto: () -> Unit
    ) {
        val context = fragment.requireContext()

        val dialog = BottomSheetDialog(context)

        val binding = BottomSheetLightTemporaryModeBinding.inflate(
            LayoutInflater.from(context)
        )

        var selectedScene = TemporaryLightSceneOption.PHOTO
        var selectedDuration = TemporaryLightDurationOption.MINUTES_30

        fun updateDurationLabel() {
            binding.tvTempDurationValue.setText(selectedDuration.labelRes)
        }

        fun updateSceneSelection() {
            setSceneSelected(
                view = binding.btnTempModePhoto,
                selected = selectedScene == TemporaryLightSceneOption.PHOTO
            )

            setSceneSelected(
                view = binding.btnTempModeMaintenance,
                selected = selectedScene == TemporaryLightSceneOption.MAINTENANCE
            )

            setSceneSelected(
                view = binding.btnTempModeEvening,
                selected = selectedScene == TemporaryLightSceneOption.EVENING
            )

            setSceneSelected(
                view = binding.btnTempModeMoonlight,
                selected = selectedScene == TemporaryLightSceneOption.MOONLIGHT
            )
        }

        fun updateDurationSelection() {
            setDurationSelected(
                view = binding.chipTempDuration15,
                selected = selectedDuration == TemporaryLightDurationOption.MINUTES_15
            )

            setDurationSelected(
                view = binding.chipTempDuration30,
                selected = selectedDuration == TemporaryLightDurationOption.MINUTES_30
            )

            setDurationSelected(
                view = binding.chipTempDuration60,
                selected = selectedDuration == TemporaryLightDurationOption.MINUTES_60
            )

            setDurationSelected(
                view = binding.chipTempDurationNext,
                selected = selectedDuration == TemporaryLightDurationOption.UNTIL_NEXT_EVENT
            )
        }

        binding.btnTempModePhoto.setOnClickListener {
            selectedScene = TemporaryLightSceneOption.PHOTO
            updateSceneSelection()
        }

        binding.btnTempModeMaintenance.setOnClickListener {
            selectedScene = TemporaryLightSceneOption.MAINTENANCE
            updateSceneSelection()
        }

        binding.btnTempModeEvening.setOnClickListener {
            selectedScene = TemporaryLightSceneOption.EVENING
            updateSceneSelection()
        }

        binding.btnTempModeMoonlight.setOnClickListener {
            selectedScene = TemporaryLightSceneOption.MOONLIGHT
            updateSceneSelection()
        }

        binding.chipTempDuration15.setOnClickListener {
            selectedDuration = TemporaryLightDurationOption.MINUTES_15
            updateDurationLabel()
            updateDurationSelection()
        }

        binding.chipTempDuration30.setOnClickListener {
            selectedDuration = TemporaryLightDurationOption.MINUTES_30
            updateDurationLabel()
            updateDurationSelection()
        }

        binding.chipTempDuration60.setOnClickListener {
            selectedDuration = TemporaryLightDurationOption.MINUTES_60
            updateDurationLabel()
            updateDurationSelection()
        }

        binding.chipTempDurationNext.setOnClickListener {
            selectedDuration = TemporaryLightDurationOption.UNTIL_NEXT_EVENT
            updateDurationLabel()
            updateDurationSelection()
        }

        binding.btnTempModeApply.setOnClickListener {
            dialog.dismiss()
            onApply(
                selectedScene,
                selectedDuration
            )
        }

        binding.btnTempModeRestoreAuto.setOnClickListener {
            dialog.dismiss()
            onRestoreAuto()
        }

        binding.btnTempModeCancel.setOnClickListener {
            dialog.dismiss()
        }

        updateDurationLabel()
        updateSceneSelection()
        updateDurationSelection()

        dialog.setContentView(binding.root)
        dialog.show()
    }

    private fun setSceneSelected(
        view: TextView,
        selected: Boolean
    ) {
        val context = view.context

        val textColor =
            if (selected) {
                R.color.light_accent
            } else {
                R.color.settings_text_secondary
            }

        view.setTextColor(
            ContextCompat.getColor(
                context,
                textColor
            )
        )
    }

    private fun setDurationSelected(
        view: TextView,
        selected: Boolean
    ) {
        val context = view.context

        val backgroundColor =
            if (selected) {
                R.color.light_accent
            } else {
                android.R.color.transparent
            }

        val textColor =
            if (selected) {
                R.color.background_color
            } else {
                R.color.settings_text_secondary
            }

        view.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                context,
                backgroundColor
            )
        )

        view.setTextColor(
            ContextCompat.getColor(
                context,
                textColor
            )
        )
    }
}