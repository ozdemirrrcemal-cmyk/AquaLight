package com.aqua.aqualight.ui.tabs.devices.add

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.provisioning.model.AqlWifiCredentials
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import com.aqua.aqualight.databinding.FragmentDeviceWifiProvisioningBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import java.util.TimeZone

class DeviceWifiProvisioningFragment : Fragment(R.layout.fragment_device_wifi_provisioning) {

    private val args: DeviceWifiProvisioningFragmentArgs by navArgs()

    private var _binding: FragmentDeviceWifiProvisioningBinding? = null
    private val binding get() = _binding!!

    private val wifiPanelLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        applyCurrentWifiSsid(showMessageIfUnavailable = true)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceWifiProvisioningBinding.bind(view)

        setupHeader()
        renderSelectedDevice()
        setupActions()
        applyCurrentWifiSsid(showMessageIfUnavailable = false)
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
            args.bleName.ifBlank { "Setup mode" }
        }
    }

    private fun setupActions() {
        binding.wifiSelectRow.setOnClickListener {
            openWifiPicker()
        }

        binding.etWifiSsid.doAfterTextChanged { editable ->
            updateSelectedWifiLabel(editable?.toString().orEmpty())
        }

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

    private fun openWifiPicker() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }

        runCatching {
            wifiPanelLauncher.launch(intent)
        }.onFailure {
            Toast.makeText(
                requireContext(),
                "Wi-Fi ayarları açılamadı. Ağ adını manuel girebilirsiniz.",
                Toast.LENGTH_SHORT
            ).show()
            binding.etWifiSsid.requestFocus()
        }
    }

    private fun applyCurrentWifiSsid(showMessageIfUnavailable: Boolean) {
        val ssid = currentWifiSsid().orEmpty()
        if (ssid.isNotBlank()) {
            binding.etWifiSsid.setText(ssid)
            binding.etWifiSsid.setSelection(ssid.length)
            updateSelectedWifiLabel(ssid)
            return
        }

        updateSelectedWifiLabel(binding.etWifiSsid.text?.toString().orEmpty())
        if (showMessageIfUnavailable) {
            Toast.makeText(
                requireContext(),
                "Telefonun bağlı olduğu Wi-Fi okunamadı. Ağ adını manuel girebilirsiniz.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun currentWifiSsid(): String? {
        val wifiManager = requireContext()
            .applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null

        val rawSsid = wifiManager.connectionInfo?.ssid
            ?.trim()
            .orEmpty()

        if (rawSsid.isBlank() || rawSsid == UNKNOWN_SSID) {
            return null
        }

        return rawSsid
            .removePrefix("\"")
            .removeSuffix("\"")
            .takeIf { value -> value.isNotBlank() && value != UNKNOWN_SSID }
    }

    private fun updateSelectedWifiLabel(value: String) {
        val ssid = value.trim()
        binding.tvSelectedWifi.text = ssid.ifBlank { "Wi-Fi seçilmedi" }
    }

    private fun onContinueClicked() {
        val ssid = binding.etWifiSsid.text?.toString()?.trim().orEmpty()
        val password = binding.etWifiPassword.text?.toString().orEmpty()

        when {
            ssid.isBlank() -> {
                binding.etWifiSsid.requestFocus()
                Toast.makeText(
                    requireContext(),
                    "Wi-Fi ağı seçin veya ağ adını girin.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            ssid.length > MAX_SSID_LENGTH -> {
                binding.etWifiSsid.requestFocus()
                Toast.makeText(
                    requireContext(),
                    "Wi-Fi adı çok uzun.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            password.length > MAX_PASSWORD_LENGTH -> {
                binding.etWifiPassword.requestFocus()
                Toast.makeText(
                    requireContext(),
                    "Wi-Fi şifresi çok uzun.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            else -> {
                val credentials = runCatching {
                    val deviceTimeZone = TimeZone.getDefault()
                    val utcOffsetMinutes = deviceTimeZone.getOffset(System.currentTimeMillis()) / MILLIS_PER_MINUTE
                    val timeZoneId = deviceTimeZone.id.orEmpty()
                    AqlWifiCredentials(
                        ssid = ssid,
                        password = password,
                        timezone = "$timeZoneId$TIMEZONE_OFFSET_SEPARATOR$utcOffsetMinutes",
                        utcOffsetMinutes = utcOffsetMinutes
                    )
                }.getOrElse { error ->
                    Toast.makeText(
                        requireContext(),
                        error.message ?: "Wi-Fi bilgileri geçersiz.",
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
        const val MILLIS_PER_MINUTE = 60_000
        const val TIMEZONE_OFFSET_SEPARATOR = "|"
        const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
