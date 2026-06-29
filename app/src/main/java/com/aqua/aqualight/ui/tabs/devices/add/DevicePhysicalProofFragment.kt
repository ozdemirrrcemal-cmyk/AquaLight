package com.aqua.aqualight.ui.tabs.devices.add

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDevicePhysicalProofBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class DevicePhysicalProofFragment : Fragment(R.layout.fragment_device_physical_proof) {

    private val args: DevicePhysicalProofFragmentArgs by navArgs()

    private var _binding: FragmentDevicePhysicalProofBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDevicePhysicalProofBinding.bind(view)

        setupHeader()
        renderDevice()
        setupActions()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_physical_proof_title),
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }

    private fun renderDevice() {
        binding.tvDeviceName.text = args.deviceTitle.ifBlank {
            getString(R.string.device_wifi_default_device_name)
        }
        binding.tvDeviceSerial.text = args.deviceSerial.ifBlank { args.candidateId }
        binding.tvDeviceModel.text = args.deviceModel.ifBlank {
            getString(R.string.device_physical_proof_manual_ble)
        }
    }

    private fun setupActions() {
        binding.btnContinue.setOnClickListener {
            openWifiProvisioningAfterPhysicalProof()
        }
    }

    private fun openWifiProvisioningAfterPhysicalProof() {
        findNavController().navigate(
            DevicePhysicalProofFragmentDirections.actionDevicePhysicalProofFragmentToDeviceWifiProvisioningFragment(
                candidateId = args.candidateId,
                deviceTitle = args.deviceTitle,
                deviceSerial = args.deviceSerial,
                deviceModel = args.deviceModel,
                bleAddress = args.bleAddress,
                bleName = args.bleName,
                claimCode = "",
                rawQrPayload = ""
            )
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
