package com.aqua.aqualight.ui.tabs.devices.add

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.provisioning.model.AqlWifiCredentials
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import com.aqua.aqualight.databinding.FragmentDeviceWifiProvisioningBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class DeviceWifiProvisioningFragment : Fragment(R.layout.fragment_device_wifi_provisioning) {

    private val args: DeviceWifiProvisioningFragmentArgs by navArgs()

    private var _binding: FragmentDeviceWifiProvisioningBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceWifiProvisioningBinding.bind(view)

        setupHeader()
        renderSelectedDevice()
        setupActions()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = "Wi-Fi Setup",
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }

    private fun renderSelectedDevice() {
        binding.tvDeviceName.text = args.deviceTitle.ifBlank { "AquaLight Device" }
        binding.tvDeviceSerial.text = "Serial: ${args.deviceSerial.ifBlank { args.candidateId }}"
        binding.tvDeviceModel.text = args.deviceModel.ifBlank {
            args.bleName.ifBlank { "BLE provisioning" }
        }
    }

    private fun setupActions() {
        binding.cbShowPassword.setOnCheckedChangeListener { _, isChecked ->
            val selection = binding.etWifiPassword.selectionStart.coerceAtLeast(0)
            binding.etWifiPassword.inputType = if (isChecked) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            binding.etWifiPassword.setSelection(
                selection.coerceAtMost(binding.etWifiPassword.text?.length ?: 0)
            )
        }

        binding.btnContinue.setOnClickListener {
            onContinueClicked()
        }
    }

    private fun onContinueClicked() {
        val ssid = binding.etWifiSsid.text?.toString()?.trim().orEmpty()
        val password = binding.etWifiPassword.text?.toString().orEmpty()

        when {
            ssid.isBlank() -> {
                binding.etWifiSsid.requestFocus()
                Toast.makeText(
                    requireContext(),
                    "Wi-Fi name is required.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            ssid.length > MAX_SSID_LENGTH -> {
                binding.etWifiSsid.requestFocus()
                Toast.makeText(
                    requireContext(),
                    "Wi-Fi name is too long.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            password.length > MAX_PASSWORD_LENGTH -> {
                binding.etWifiPassword.requestFocus()
                Toast.makeText(
                    requireContext(),
                    "Wi-Fi password is too long.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            else -> {
                val credentials = runCatching {
                    AqlWifiCredentials(
                        ssid = ssid,
                        password = password
                    )
                }.getOrElse { error ->
                    Toast.makeText(
                        requireContext(),
                        error.message ?: "Wi-Fi credentials are invalid.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

                val draft = AqlProvisioningDraftStore.create(
                    candidateId = args.candidateId,
                    bleAddress = args.bleAddress,
                    bleName = args.bleName,
                    claimCode = args.claimCode,
                    rawQrPayload = args.rawQrPayload,
                    deviceTitle = args.deviceTitle,
                    deviceSerial = args.deviceSerial,
                    deviceModel = args.deviceModel,
                    wifiCredentials = credentials
                )

                findNavController().navigate(
                    DeviceWifiProvisioningFragmentDirections
                        .actionDeviceWifiProvisioningFragmentToDeviceProvisioningProgressFragment(
                            sessionId = draft.sessionId
                        )
                )
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val MAX_SSID_LENGTH = 32
        const val MAX_PASSWORD_LENGTH = 64
    }
}
