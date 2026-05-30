package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightProgramListBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

class DeviceLightProgramListFragment :
    Fragment(R.layout.fragment_device_light_program_list) {

    private var _binding: FragmentDeviceLightProgramListBinding? = null
    private val binding get() = _binding!!

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentDeviceLightProgramListBinding.bind(view)

        renderPreviewState()
        setupClicks()
    }

    fun onHeaderAddClick() {
        if (_binding == null) {
            return
        }

        navigateToProgramEditor(
            programName = "New Program"
        )
    }

    private fun renderPreviewState() = with(binding) {
        tvActiveProgramTitle.text = "Every Day Program"
        tvActiveProgramSubtitle.text = "09:00 → 19:15 · Ramp 60 min"
        tvActiveProgramChip.text = "ACTIVE"

        tvProgramSummaryPeak.text = "100%"
        tvProgramPhotoperiod.text = "10h 15m"
        tvProgramPeakRange.text = "12:00–16:00"
        tvProgramChannels.text = "R80 G84 B79 W65"

        switchEveryDayProgram.isChecked = true
        switchWeekendProgram.isChecked = false
    }

    private fun setupClicks() = with(binding) {
        cardProgramEveryDay.setOnClickListener {
            navigateToProgramEditor(
                programName = "Every Day Program"
            )
        }

        cardProgramEveryDay.setOnLongClickListener {
            showProgramActions(
                programName = "Every Day Program",
                subtitle = "09:00 → 19:15 · Every day",
                isEnabled = switchEveryDayProgram.isChecked,
                onEdit = {
                    navigateToProgramEditor(
                        programName = "Every Day Program"
                    )
                },
                onToggle = {
                    switchEveryDayProgram.isChecked =
                        !switchEveryDayProgram.isChecked
                },
                onSetActive = {
                    setActiveProgram(
                        title = "Every Day Program",
                        subtitle = "09:00 → 19:15 · Ramp 60 min",
                        peak = "100%",
                        photoperiod = "10h 15m",
                        peakRange = "12:00–16:00",
                        channels = "R80 G84 B79 W65"
                    )
                }
            )

            true
        }

        cardProgramWeekend.setOnClickListener {
            navigateToProgramEditor(
                programName = "Weekend Soft Light"
            )
        }

        cardProgramWeekend.setOnLongClickListener {
            showProgramActions(
                programName = "Weekend Soft Light",
                subtitle = "10:00 → 18:00 · Sat, Sun",
                isEnabled = switchWeekendProgram.isChecked,
                onEdit = {
                    navigateToProgramEditor(
                        programName = "Weekend Soft Light"
                    )
                },
                onToggle = {
                    switchWeekendProgram.isChecked =
                        !switchWeekendProgram.isChecked
                },
                onSetActive = {
                    setActiveProgram(
                        title = "Weekend Soft Light",
                        subtitle = "10:00 → 18:00 · Ramp 90 min",
                        peak = "75%",
                        photoperiod = "8h",
                        peakRange = "12:00–15:00",
                        channels = "R70 G76 B72 W55"
                    )
                }
            )

            true
        }

        chipProgramsAll.setOnClickListener {
            showMessage(
                message = "All programs"
            )
        }

        chipProgramsActive.setOnClickListener {
            showMessage(
                message = "Active programs"
            )
        }

        chipProgramsDisabled.setOnClickListener {
            showMessage(
                message = "Disabled programs"
            )
        }

        switchEveryDayProgram.setOnCheckedChangeListener { _, isChecked ->
            showMessage(
                message = if (isChecked) {
                    "Every Day program enabled"
                } else {
                    "Every Day program disabled"
                }
            )
        }

        switchWeekendProgram.setOnCheckedChangeListener { _, isChecked ->
            showMessage(
                message = if (isChecked) {
                    "Weekend program enabled"
                } else {
                    "Weekend program disabled"
                }
            )
        }
    }

    private fun showProgramActions(
        programName: String,
        subtitle: String,
        isEnabled: Boolean,
        onEdit: () -> Unit,
        onToggle: () -> Unit,
        onSetActive: () -> Unit
    ) {
        val dialog =
            BottomSheetDialog(
                requireContext()
            )

        val sheetView =
            layoutInflater.inflate(
                R.layout.bottom_sheet_light_program_actions,
                null
            )

        sheetView
            .findViewById<TextView>(
                R.id.tvProgramActionTitle
            ).text = programName

        sheetView
            .findViewById<TextView>(
                R.id.tvProgramActionSubtitle
            ).text = subtitle

        val toggleButton =
            sheetView.findViewById<TextView>(
                R.id.btnProgramActionToggle
            )

        toggleButton.text =
            if (isEnabled) {
                "Disable Program"
            } else {
                "Enable Program"
            }

        sheetView
            .findViewById<TextView>(
                R.id.btnProgramActionEdit
            )
            .setOnClickListener {
                dialog.dismiss()
                onEdit()
            }

        sheetView
            .findViewById<TextView>(
                R.id.btnProgramActionPreview
            )
            .setOnClickListener {
                dialog.dismiss()

                showMessage(
                    message = "Preview day for $programName will be added"
                )
            }

        sheetView
            .findViewById<TextView>(
                R.id.btnProgramActionDuplicate
            )
            .setOnClickListener {
                dialog.dismiss()

                navigateToProgramEditor(
                    programName = "$programName Copy"
                )
            }

        sheetView
            .findViewById<TextView>(
                R.id.btnProgramActionSetActive
            )
            .setOnClickListener {
                dialog.dismiss()

                onSetActive()

                showMessage(
                    message = "$programName set as active"
                )
            }

        toggleButton.setOnClickListener {
            dialog.dismiss()
            onToggle()
        }

        sheetView
            .findViewById<TextView>(
                R.id.btnProgramActionDelete
            )
            .setOnClickListener {
                dialog.dismiss()

                showMessage(
                    message = "Delete confirmation for $programName will be added"
                )
            }

        sheetView
            .findViewById<TextView>(
                R.id.btnProgramActionCancel
            )
            .setOnClickListener {
                dialog.dismiss()
            }

        dialog.setContentView(
            sheetView
        )

        dialog.show()
    }

    private fun setActiveProgram(
        title: String,
        subtitle: String,
        peak: String,
        photoperiod: String,
        peakRange: String,
        channels: String
    ) = with(binding) {
        tvActiveProgramTitle.text = title
        tvActiveProgramSubtitle.text = subtitle
        tvActiveProgramChip.text = "ACTIVE"

        tvProgramSummaryPeak.text = peak
        tvProgramPhotoperiod.text = photoperiod
        tvProgramPeakRange.text = peakRange
        tvProgramChannels.text = channels
    }

    private fun navigateToProgramEditor(
        programName: String
    ) {
        findNavController().navigate(
            R.id.deviceLightProgramEditorFragment,
            bundleOf(
                ARG_DEVICE_ID to deviceId,
                ARG_PROGRAM_NAME to programName
            )
        )
    }

    private fun showMessage(
        message: String
    ) {
        Toast.makeText(
            requireContext(),
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        _binding = null

        super.onDestroyView()
    }

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_PROGRAM_NAME = "programName"

        fun newInstance(
            deviceId: Long
        ): DeviceLightProgramListFragment {
            return DeviceLightProgramListFragment().apply {
                arguments = Bundle().apply {
                    putLong(
                        ARG_DEVICE_ID,
                        deviceId
                    )
                }
            }
        }
    }
}