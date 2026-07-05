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
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
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
        observeProvisioningFailureResult()
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
            safeSerialText()
        )
        binding.tvDeviceModel.text = setupMethodLabel()
    }

    private fun setupMethodLabel(): String {
        return if (args.claimCode.isNotBlank()) {
            "Secure QR Setup"
        } else {
            "Manual BLE Setup"
        }
    }

    private fun safeSerialText(): String {
        return args.deviceSerial
            .trim()
            .ifBlank {
                args.candidateId
                    .trim()
                    .takeUnless { value -> value.isLikelyBleAddress() }
                    .orEmpty()
            }
            .ifBlank { "—" }
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

        binding.etWifiSsid.doAfterTextChanged {
            clearProvisioningFailureMessage()
        }

        binding.etWifiPassword.doAfterTextChanged {
            clearProvisioningFailureMessage()
        }

        binding.btnContinue.setOnClickListener {
            onContinueClicked()
        }
    }

    private fun observeProvisioningFailureResult() {
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        savedStateHandle.getLiveData<String>(DeviceWifiProvisioningResult.KEY_FAILURE_MESSAGE)
            .observe(viewLifecycleOwner) { message ->
                val field = savedStateHandle.remove<String>(
                    DeviceWifiProvisioningResult.KEY_FAILURE_FIELD
                ).orEmpty()
                savedStateHandle.remove<String>(DeviceWifiProvisioningResult.KEY_FAILURE_MESSAGE)
                showProvisioningFailure(
                    message = message,
                    field = field
                )
            }
    }

    private fun showProvisioningFailure(
        message: String,
        field: String
    ) {
        val errorMessage = message.ifBlank {
            getString(R.string.device_wifi_provisioning_failed_error)
        }

        binding.tvFormHint.text = errorMessage

        if (field == DeviceWifiProvisioningResult.FIELD_SSID) {
            binding.etWifiSsid.error = errorMessage
            focusInput(binding.etWifiSsid)
        } else {
            binding.etWifiPassword.error = errorMessage
            binding.etWifiPassword.selectAll()
            focusInput(binding.etWifiPassword)
        }
    }

    private fun clearProvisioningFailureMessage() {
        binding.tvFormHint.text = getString(R.string.device_wifi_privacy_hint)
        binding.etWifiSsid.error = null
        binding.etWifiPassword.error = null
    }

    private fun focusInput(view: View) {
        binding.contentScrollView.post {
            if (_binding == null) return@post
            view.requestFocus()
            val inputMethodManager = requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            inputMethodManager?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
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
        clearProvisioningFailureMessage()

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

            ssid.utf8ByteSize() > AqlBleProvisioningContract.WIFI_SSID_MAX_LENGTH -> {
                binding.etWifiSsid.requestFocus()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.device_wifi_name_too_long),
                    Toast.LENGTH_SHORT
                ).show()
            }

            networkKey.utf8ByteSize() > AqlBleProvisioningContract.WIFI_PASSWORD_MAX_LENGTH -> {
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

    private fun String.utf8ByteSize(): Int = toByteArray(Charsets.UTF_8).size

    private fun String.isLikelyBleAddress(): Boolean {
        return matches(Regex("(?i)^([0-9a-f]{2}:){5}[0-9a-f]{2}$"))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val WIFI_REFRESH_DELAY_MS = 700L
        const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
