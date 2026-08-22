package com.aqua.aqualight.ui.tabs.devices.add

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.application.devices.provisioning.ProvisioningWifiInputError
import com.aqua.aqualight.application.devices.provisioning.ProvisioningWifiInputPolicy
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceWifiProvisioningBinding
import com.aqua.aqualight.platform.permissions.AppCapability
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.permission.CapabilityPermissionCoordinator

class DeviceWifiProvisioningFragment : Fragment(R.layout.fragment_device_wifi_provisioning) {

    private val args: DeviceWifiProvisioningFragmentArgs by navArgs()

    private var _binding: FragmentDeviceWifiProvisioningBinding? = null
    private val binding get() = _binding!!

    private var waitingForWifiSettingsReturn = false

    private val provisioningDraftOperations by lazy {
        requireContext().requireAppContainer().provisioningDraftOperations
    }

    private val permissionCoordinator = CapabilityPermissionCoordinator(this) { action ->
        when (action) {
            ACTION_OPEN_WIFI_SETTINGS -> openWifiSettings()
        }
    }

    private val wifiSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshWifiNetworkFieldAfterSettings()
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
                title = getString(R.string.device_wifi_title),
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
        return if (args.qrSecretReference.isNotBlank()) {
            getString(R.string.device_setup_method_secure_qr)
        } else {
            getString(R.string.device_setup_method_manual_ble)
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
            .ifBlank { getString(R.string.common_not_available_em_dash) }
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
            binding.etWifiPassword.setSelection(
                0,
                binding.etWifiPassword.text?.length ?: 0
            )
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
        permissionCoordinator.runWhenGranted(
            capability = AppCapability.WIFI_SSID,
            actionToken = ACTION_OPEN_WIFI_SETTINGS
        )
    }

    private fun openWifiSettings() {
        waitingForWifiSettingsReturn = true
        runCatching {
            wifiSettingsLauncher.launch(Intent(Settings.ACTION_WIFI_SETTINGS))
        }.onFailure {
            waitingForWifiSettingsReturn = false
            (activity as? BaseActivity)?.showSnackBar(
                message = getString(R.string.device_wifi_settings_open_failed),
                type = BaseActivity.SnackType.ERROR
            )
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
        if (!permissionCoordinator.isGranted(AppCapability.WIFI_SSID)) {
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

    private fun onContinueClicked() {
        clearProvisioningFailureMessage()

        val ssid = binding.etWifiSsid.text?.toString()?.trim().orEmpty()
        val networkKey = binding.etWifiPassword.text?.toString().orEmpty()

        when (ProvisioningWifiInputPolicy.validate(ssid, networkKey)) {
            ProvisioningWifiInputError.EMPTY_SSID -> {
                binding.etWifiSsid.requestFocus()
                (activity as? BaseActivity)?.showSnackBar(
                    message = getString(R.string.device_wifi_select_first),
                    type = BaseActivity.SnackType.WARNING
                )
            }

            ProvisioningWifiInputError.SSID_TOO_LONG -> {
                binding.etWifiSsid.requestFocus()
                (activity as? BaseActivity)?.showSnackBar(
                    message = getString(R.string.device_wifi_name_too_long),
                    type = BaseActivity.SnackType.WARNING
                )
            }

            ProvisioningWifiInputError.PASSWORD_TOO_LONG -> {
                binding.etWifiPassword.requestFocus()
                (activity as? BaseActivity)?.showSnackBar(
                    message = getString(R.string.device_wifi_password_too_long),
                    type = BaseActivity.SnackType.WARNING
                )
            }

            null -> {
                val draft = DeviceWifiProvisioningDraftFactory.create(
                    args = args,
                    ssid = ssid,
                    networkKey = networkKey,
                    operations = provisioningDraftOperations
                ).getOrElse { error ->
                    (activity as? BaseActivity)?.showSnackBar(
                        message = error.message ?: getString(R.string.device_wifi_invalid_details),
                        type = BaseActivity.SnackType.ERROR
                    )
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

    private fun String.isLikelyBleAddress(): Boolean {
        return matches(Regex("(?i)^([0-9a-f]{2}:){5}[0-9a-f]{2}$"))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val ACTION_OPEN_WIFI_SETTINGS = "open_wifi_settings"
        const val WIFI_REFRESH_DELAY_MS = 700L
        const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
