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
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightProgramListBinding.bind(view)

        setupHeader()
        renderPreviewState()
        setupClicks()
    }

    private fun setupHeader() = with(binding.deviceHeader) {
        tvTitle.text = getString(R.string.light_programs_title)

        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        headerActionsContainer.visibility = View.VISIBLE

        btnActionOne.visibility = View.VISIBLE
        btnActionOne.setImageResource(R.drawable.ic_add_24)
        btnActionOne.contentDescription = getString(R.string.light_add_program)
        btnActionOne.setOnClickListener {
            openProgramEditor(
                programName = DEFAULT_NEW_PROGRAM_NAME
            )
        }

        btnActionTwo.visibility = View.GONE
        btnActionThree.visibility = View.GONE
    }

    private fun renderPreviewState() = with(binding) {
        tvActiveProgramTitle.text = "Every Day Program"
        tvActiveProgramChip.text = "ACTIVE"

        tvProgramSummaryPeak.text = "100%"
        tvProgramPhotoperiod.text = "10h 15m"

        viewActiveProgramCurve.setProgramCurve(
            start = "09:00",
            peakStart = "12:00",
            peakEnd = "16:00",
            end = "19:15"
        )

        switchEveryDayProgram.isChecked = true
        switchWeekendProgram.isChecked = false
    }

    private fun setupClicks() = with(binding) {
        cardProgramEveryDay.setOnClickListener {
            openProgramEditor(
                programName = "Every Day Program"
            )
        }

        cardProgramEveryDay.setOnLongClickListener {
            showProgramActions(
                programName = "Every Day Program",
                subtitle = "09:00 → 19:15 · Every day",
                isEnabled = switchEveryDayProgram.isChecked,
                onEdit = {
                    openProgramEditor(
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
                        peak = "100%",
                        photoperiod = "10h 15m",
                        start = "09:00",
                        peakStart = "12:00",
                        peakEnd = "16:00",
                        end = "19:15"
                    )
                }
            )

            true
        }

        cardProgramWeekend.setOnClickListener {
            openProgramEditor(
                programName = "Weekend Soft Light"
            )
        }

        cardProgramWeekend.setOnLongClickListener {
            showProgramActions(
                programName = "Weekend Soft Light",
                subtitle = "10:00 → 18:00 · Sat, Sun",
                isEnabled = switchWeekendProgram.isChecked,
                onEdit = {
                    openProgramEditor(
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
                        peak = "75%",
                        photoperiod = "8h",
                        start = "10:00",
                        peakStart = "12:00",
                        peakEnd = "15:00",
                        end = "18:00"
                    )
                }
            )

            true
        }

        chipProgramsAll.setOnClickListener {
            showMessage("All programs")
        }

        chipProgramsActive.setOnClickListener {
            showMessage("Active programs")
        }

        chipProgramsDisabled.setOnClickListener {
            showMessage("Disabled programs")
        }

        switchEveryDayProgram.setOnCheckedChangeListener { _, isChecked ->
            showMessage(
                if (isChecked) {
                    "Every Day program enabled"
                } else {
                    "Every Day program disabled"
                }
            )
        }

        switchWeekendProgram.setOnCheckedChangeListener { _, isChecked ->
            showMessage(
                if (isChecked) {
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
        val dialog = BottomSheetDialog(requireContext())

        val sheetView = layoutInflater.inflate(
            R.layout.bottom_sheet_light_program_actions,
            null
        )

        sheetView.findViewById<TextView>(
            R.id.tvProgramActionTitle
        ).text = programName

        sheetView.findViewById<TextView>(
            R.id.tvProgramActionSubtitle
        ).text = subtitle

        val toggleButton = sheetView.findViewById<TextView>(
            R.id.btnProgramActionToggle
        )

        toggleButton.text =
            if (isEnabled) {
                "Disable Program"
            } else {
                "Enable Program"
            }

        sheetView.findViewById<TextView>(
            R.id.btnProgramActionEdit
        ).setOnClickListener {
            dialog.dismiss()
            onEdit()
        }

        sheetView.findViewById<TextView>(
            R.id.btnProgramActionPreview
        ).setOnClickListener {
            dialog.dismiss()

            showMessage(
                message = "Preview day for $programName will be added"
            )
        }

        sheetView.findViewById<TextView>(
            R.id.btnProgramActionDuplicate
        ).setOnClickListener {
            dialog.dismiss()

            openProgramEditor(
                programName = "$programName Copy"
            )
        }

        sheetView.findViewById<TextView>(
            R.id.btnProgramActionSetActive
        ).setOnClickListener {
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

        sheetView.findViewById<TextView>(
            R.id.btnProgramActionDelete
        ).setOnClickListener {
            dialog.dismiss()

            showMessage(
                message = "Delete confirmation for $programName will be added"
            )
        }

        sheetView.findViewById<TextView>(
            R.id.btnProgramActionCancel
        ).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun setActiveProgram(
        title: String,
        peak: String,
        photoperiod: String,
        start: String,
        peakStart: String,
        peakEnd: String,
        end: String
    ) = with(binding) {
        tvActiveProgramTitle.text = title
        tvActiveProgramChip.text = "ACTIVE"

        tvProgramSummaryPeak.text = peak
        tvProgramPhotoperiod.text = photoperiod

        viewActiveProgramCurve.setProgramCurve(
            start = start,
            peakStart = peakStart,
            peakEnd = peakEnd,
            end = end
        )
    }

    private fun openProgramEditor(
        programName: String
    ) {
        findNavController().navigate(
            R.id.action_deviceLightProgramListFragment_to_deviceLightProgramEditorFragment,
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
        private const val DEFAULT_NEW_PROGRAM_NAME = "New Program"
    }
}