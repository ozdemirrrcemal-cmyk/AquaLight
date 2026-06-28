package com.aqua.aqualight.ui.tabs.devices.add

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceWifiProvisioningBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader

class DeviceWifiProvisioningFragment : Fragment(R.layout.fragment_device_wifi_provisioning) {

    private val args: DeviceWifiProvisioningFragmentArgs by navArgs()

    private var _binding: FragmentDeviceWifiProvisioningBinding? = null
    private val binding get() = _binding!!

    private var waitingForWifiSettingsReturn = false

    private val wifiSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshWifiNetworkFieldAfterSettings()
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        openWifiSettings()
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
        applyCurrentWifiSsid()
    }

    override fun onResume() {
        super.onResume()
        if (waitingForWifiSettingsReturn) {
            waitingForWifiSettingsReturn = false
            refreshWifiNetworkFieldAfterSettings()
        }
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_wifi_title),
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }

    private fun renderSelectedDevice() {
        binding.tvDeviceName.text = args.deviceTitle.ifBlank {
            getString(R.string.device_wifi_default_device_name)
        }
        binding.tvDeviceSerial.text = getString(
            R.string.device_wifi_serial_format,
            args.deviceSerial.ifBlank { args.candidateId }
        )
        binding.tvDeviceModel.text = args.deviceModel.ifBlank {
            args.bleName.ifBlank { getString(R.string.device_wifi_setup_mode) }
        }
    }

    private fun setupActions() {
        binding.tvUseCurrentWifiAction.setOnClickListener {
            openWifiSettingsWithPermissionCheck()
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

    private fun openWifiSettingsWithPermissionCheck() {
        if (hasWifiSsidPermission()) {
            openWifiSettings()
            return
        }

        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun openWifiSettings() {
        waitingForWifiSettingsReturn = true
        runCatching {
            wifiSettingsLauncher.launch(Intent(Settings.ACTION_WIFI_SETTINGS))
        }.onFailure {
            waitingForWifiSettingsReturn = false
            Toast.makeText(
                requireContext(),
                getString(R.string.device_wifi_settings_open_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun refreshWifiNetworkFieldAfterSettings() {
        binding.root.postDelayed(
            {
                if (_binding != null) {
                    applyCurrentWifiSsid()
                }
            },
            WIFI_REFRESH_DELAY_MS
        )
    }

    private fun applyCurrentWifiSsid() {
        val ssid = currentWifiSsid().orEmpty()
        if (ssid.isNotBlank()) {
            binding.etWifiSsid.setText(ssid)
            binding.etWifiSsid.setSelection(ssid.length)
        }
    }

    @Suppress("DEPRECATION")
    private fun currentWifiSsid(): String? {
        if (!hasWifiSsidPermission()) {
            return null
        }

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

    private fun hasWifiSsidPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun onContinueClicked() {
        val ssid = binding.etWifiSsid.text?.toString()?.trim().orEmpty()
        val networkKey = binding.etWifiPassword.text?.toString().orEmpty()

        when {
            ssid.isBlank() -> {
                binding.etWifiSsid.requestFocus()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.device_wifi_select_first),
                    Toast.LENGTH_SHORT
                ).show()
            }

            ssid.length > MAX_SSID_LENGTH -> {
                binding.etWifiSsid.requestFocus()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.device_wifi_name_too_long),
                    Toast.LENGTH_SHORT
                ).show()
            }

            networkKey.length > MAX_PASSWORD_LENGTH -> {
                binding.etWifiPassword.requestFocus()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.device_wifi_password_too_long),
                    Toast.LENGTH_SHORT
                ).show()
            }

            else -> {
                val draft = DeviceWifiProvisioningDraftFactory.create(
                    args = args,
                    ssid = ssid,
                    networkKey = networkKey
                ).getOrElse { error ->
                    Toast.makeText(
                        requireContext(),
                        error.message ?: getString(R.string.device_wifi_invalid_details),
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }

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
        const val WIFI_REFRESH_DELAY_MS = 700L
        const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
