package com.aqua.aqualight.ui.tabs.devices.detail.light

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceLightPresetsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

class DeviceLightPresetsFragment : Fragment(R.layout.fragment_device_light_presets) {

    private var _binding: FragmentDeviceLightPresetsBinding? = null
    private val binding get() = _binding!!

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceLightPresetsBinding.bind(view)

        renderDummyState()
        setupClicks()
    }

    private fun renderDummyState() = with(binding) {
        tvPresetsSubtitle.text = "Quick lighting profiles · Device ID: $deviceId"

        tvTemporarySceneTitle.text = "No temporary scene active"
        tvTemporarySceneDesc.text = "Auto program is running normally"
    }

    private fun setupClicks() = with(binding) {
        btnPresetsBack.setOnClickListener {
            findNavController().navigateUp()
        }

        btnCreatePresetTop.setOnClickListener {
            showMessage("Create preset will be added")
        }

        btnEndTemporaryScene.setOnClickListener {
            tvTemporarySceneTitle.text = "No temporary scene active"
            tvTemporarySceneDesc.text = "Auto program is running normally"
            showMessage("Temporary scene ended")
        }

        cardPresetPlantBoost.setOnClickListener {
            showPresetDetail(
                title = "Plant Boost",
                description = "High growth profile",
                master = 90,
                channels = "R85  G92  B76  W70"
            )
        }

        btnApplyPlantBoost.setOnClickListener {
            applyTemporaryScene("Plant Boost")
        }

        cardPresetLowAlgae.setOnClickListener {
            showPresetDetail(
                title = "Low Algae",
                description = "Controlled output for algae-sensitive tanks",
                master = 65,
                channels = "R65  G72  B68  W55"
            )
        }

        btnApplyLowAlgae.setOnClickListener {
            applyTemporaryScene("Low Algae")
        }

        cardPresetFishView.setOnClickListener {
            showPresetDetail(
                title = "Fish View",
                description = "Balanced color for natural display",
                master = 80,
                channels = "R76  G82  B88  W65"
            )
        }

        btnApplyFishView.setOnClickListener {
            applyTemporaryScene("Fish View")
        }

        cardPresetPhotoMode.setOnClickListener {
            showPresetDetail(
                title = "Photo Mode",
                description = "Bright neutral light for photos",
                master = 100,
                channels = "R90  G92  B90  W100"
            )
        }

        btnApplyPhotoMode.setOnClickListener {
            applyTemporaryScene("Photo Mode")
        }

        cardPresetEvening.setOnClickListener {
            showPresetDetail(
                title = "Evening View",
                description = "Warm soft display for night viewing",
                master = 45,
                channels = "R80  G55  B35  W30"
            )
        }

        btnApplyEvening.setOnClickListener {
            applyTemporaryScene("Evening View")
        }

        cardPresetMaintenance.setOnClickListener {
            showPresetDetail(
                title = "Maintenance",
                description = "Soft white light for tank care",
                master = 55,
                channels = "R70  G70  B70  W85"
            )
        }

        btnApplyMaintenance.setOnClickListener {
            applyTemporaryScene("Maintenance")
        }

        rowCustomEvening.setOnClickListener {
            showPresetDetail(
                title = "My Evening View",
                description = "Custom saved preset",
                master = 45,
                channels = "R78  G52  B34  W25"
            )
        }

        btnCreateCustomPreset.setOnClickListener {
            showMessage("Custom preset creation will be added")
        }
    }

    private fun showPresetDetail(
        title: String,
        description: String,
        master: Int,
        channels: String
    ) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(
            R.layout.bottom_sheet_light_preset_detail,
            null
        )

        sheetView.findViewById<TextView>(R.id.tvPresetDetailTitle).text = title
        sheetView.findViewById<TextView>(R.id.tvPresetDetailDesc).text = description
        sheetView.findViewById<TextView>(R.id.tvPresetDetailMaster).text = "Master $master%"
        sheetView.findViewById<TextView>(R.id.tvPresetDetailChannels).text = channels

        sheetView.findViewById<TextView>(R.id.btnPresetApplyTemporary).setOnClickListener {
            dialog.dismiss()
            applyTemporaryScene(title)
        }

        sheetView.findViewById<TextView>(R.id.btnPresetSaveToProgram).setOnClickListener {
            dialog.dismiss()
            showMessage("$title will be assigned to program later")
        }

        sheetView.findViewById<TextView>(R.id.btnPresetEditCopy).setOnClickListener {
            dialog.dismiss()
            showMessage("Edit copy will be added")
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun applyTemporaryScene(
        sceneName: String
    ) = with(binding) {
        tvTemporarySceneTitle.text = "$sceneName active"
        tvTemporarySceneDesc.text = "30 min remaining · Program resumes automatically"
        showMessage("$sceneName applied temporarily")
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

        fun newInstance(
            deviceId: Long
        ): DeviceLightPresetsFragment {
            return DeviceLightPresetsFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_DEVICE_ID, deviceId)
                }
            }
        }
    }
}