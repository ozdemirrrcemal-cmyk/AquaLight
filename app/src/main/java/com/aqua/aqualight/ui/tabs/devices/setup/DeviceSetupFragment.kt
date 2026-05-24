package com.aqua.aqualight.ui.tabs.devices.setup

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.DeviceStoreWriter
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceType
import com.aqua.aqualight.data.devices.discovery.DeviceDiscoveryService
import com.aqua.aqualight.data.devices.discovery.DeviceScanReason
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice
import com.aqua.aqualight.data.devices.setup.DeviceSetupWifiConnector
import com.aqua.aqualight.data.devices.setup.HomeWifiConnectionWaiter
import com.aqua.aqualight.data.devices.setup.LegacyDeviceSetupClient
import com.aqua.aqualight.databinding.FragmentDeviceSetupBinding
import com.aqua.aqualight.ui.tabs.devices.model.DeviceIconMapper
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DeviceSetupFragment : Fragment(R.layout.fragment_device_setup) {

    private var _binding: FragmentDeviceSetupBinding? = null
    private val binding get() = _binding!!

    private lateinit var wifiConnector: DeviceSetupWifiConnector
    private lateinit var setupClient: LegacyDeviceSetupClient
    private lateinit var homeWifiWaiter: HomeWifiConnectionWaiter
    private lateinit var deviceStoreWriter: DeviceStoreWriter

    private var setupConnection: DeviceSetupWifiConnector.SetupConnection? = null

    private var setupSsid: String = ""
    private var displayName: String = "Device"
    private var familyName: String = "Aqua device"
    private var expectedDeviceType: AquaDeviceType = AquaDeviceType.UNKNOWN
    private var selectedHomeSsid: String = ""

    private var isSettingUp = false
    private var isScanningNetworks = false

    private data class HomeWifiInput(
        val ssid: String,
        val password: String
    )

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentDeviceSetupBinding.bind(view)

        val devicesStore = DevicesDataStoreManager.create(requireContext())

        wifiConnector = DeviceSetupWifiConnector(requireContext())
        setupClient = LegacyDeviceSetupClient()
        homeWifiWaiter = HomeWifiConnectionWaiter(requireContext())
        deviceStoreWriter = DeviceStoreWriter(devicesStore)

        readArgs()
        renderInitialState()
        setupClickListeners()
    }

    private fun readArgs() {
        displayName = requireArguments().getString("displayName", "Device")
        familyName = requireArguments().getString("familyName", "Aqua device")
        setupSsid = requireArguments().getString("setupSsid", "")

        val deviceTypeKey = requireArguments().getString("deviceType", "")

        expectedDeviceType = AquaDeviceType.entries.firstOrNull { type ->
            type.storageKey == deviceTypeKey
        } ?: AquaDeviceType.UNKNOWN
    }

    private fun renderInitialState() {
        binding.tvTitle.text = displayName
        binding.tvSubtitle.text = familyName
        binding.tvSetupSsid.text = setupSsid
        binding.tvStatus.text = getString(R.string.device_setup_choose_home_wifi)
        binding.etHomeWifiSsid.setText("")

        binding.ivDeviceImage.setImageResource(
            DeviceIconMapper.iconFor(expectedDeviceType)
        )

        binding.ivDeviceImage.contentDescription = displayName
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            if (!isSettingUp && !isScanningNetworks) {
                findNavController().popBackStack()
            }
        }

        binding.inputHomeWifiSsid.setEndIconOnClickListener {
            scanHomeNetworks()
        }

        binding.etHomeWifiSsid.setOnClickListener {
            scanHomeNetworks()
        }

        binding.btnStartSetup.setOnClickListener {
            startSetup()
        }
    }

    private fun scanHomeNetworks() {
        if (isScanningNetworks || isSettingUp) {
            return
        }

        if (setupSsid.isBlank()) {
            showError(getString(R.string.device_setup_missing_setup_network))
            return
        }

        isScanningNetworks = true

        setBusy(
            busy = true,
            status = getString(
                R.string.device_setup_connecting_to_setup,
                setupSsid
            )
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val connection = getOrCreateSetupConnection()

                if (!isViewReady()) {
                    return@launch
                }

                setBusy(
                    busy = true,
                    status = getString(R.string.device_setup_scanning_home_networks)
                )

                val networks = setupClient.scanHomeWifiNetworks(
                    network = connection.network
                )

                if (!isViewReady()) {
                    return@launch
                }

                if (networks.isEmpty()) {
                    showError(getString(R.string.device_setup_no_networks_found))

                    setBusy(
                        busy = false,
                        status = getString(R.string.device_setup_no_networks_status)
                    )

                    return@launch
                }

                showWifiNetworksBottomSheet(networks)

                setBusy(
                    busy = false,
                    status = getString(R.string.device_setup_select_home_wifi_status)
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                if (isViewReady()) {
                    showError(
                        exception.message
                            ?: getString(R.string.device_setup_scan_error)
                    )

                    setBusy(
                        busy = false,
                        status = getString(R.string.device_setup_scan_failed)
                    )
                }
            } finally {
                isScanningNetworks = false

                if (isViewReady() && !isSettingUp) {
                    setBusyControlsEnabled(true)
                }
            }
        }
    }

    private suspend fun getOrCreateSetupConnection(): DeviceSetupWifiConnector.SetupConnection {
        setupConnection?.let { connection ->
            return connection
        }

        val connection = wifiConnector.connectToSetupNetwork(
            ssid = setupSsid,
            password = SETUP_AP_PASSWORD,
            timeoutMs = 30_000L
        )

        setupConnection = connection
        return connection
    }

    private fun startSetup() {
        if (isSettingUp) {
            return
        }

        val input = readAndValidateHomeWifiInput()
            ?: return

        isSettingUp = true

        setBusy(
            busy = true,
            status = getString(R.string.device_setup_preparing)
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                runSetupFlow(input)
            } catch (exception: Exception) {
                exception.printStackTrace()

                if (isViewReady()) {
                    showError(
                        exception.message
                            ?: getString(R.string.device_setup_generic_failed)
                    )

                    setBusy(
                        busy = false,
                        status = getString(R.string.device_setup_failed)
                    )
                }
            } finally {
                isSettingUp = false

                if (isViewReady()) {
                    setBusyControlsEnabled(true)
                }
            }
        }
    }

    private fun readAndValidateHomeWifiInput(): HomeWifiInput? {
        val homeSsid = selectedHomeSsid.ifBlank {
            binding.etHomeWifiSsid.text
                ?.toString()
                ?.trim()
                .orEmpty()
        }

        val homePassword = binding.etHomeWifiPassword.text
            ?.toString()
            .orEmpty()

        if (setupSsid.isBlank()) {
            showError(getString(R.string.device_setup_missing_setup_network))
            return null
        }

        if (homeSsid.isBlank()) {
            binding.inputHomeWifiSsid.error = getString(
                R.string.device_setup_validation_select_wifi
            )
            return null
        }

        binding.inputHomeWifiSsid.error = null

        if (homePassword.isBlank()) {
            binding.inputHomeWifiPassword.error = getString(
                R.string.device_setup_validation_password
            )
            return null
        }

        binding.inputHomeWifiPassword.error = null

        return HomeWifiInput(
            ssid = homeSsid,
            password = homePassword
        )
    }

    private suspend fun runSetupFlow(
        input: HomeWifiInput
    ) {
        val connection = getOrCreateSetupConnection()

        if (!isViewReady()) {
            return
        }

        setBusy(
            busy = true,
            status = getString(R.string.device_setup_sending_credentials)
        )

        val setupResult = setupClient.sendHomeWifiCredentials(
            network = connection.network,
            setupSsid = setupSsid,
            setupPassword = SETUP_AP_PASSWORD,
            homeSsid = input.ssid,
            homePassword = input.password,
            disableSetupAccessPoint = false
        )

        if (!setupResult.success) {
            throw IllegalStateException(
                setupResult.errorMessage
                    ?: getString(R.string.device_setup_not_accepted)
            )
        }

        if (!waitForDeviceClientConnection(connection, setupResult.responseBody)) {
            throw IllegalStateException(
                getString(R.string.device_setup_connection_failed)
            )
        }

        if (!isViewReady()) {
            return
        }

        setBusy(
            busy = true,
            status = getString(R.string.device_setup_closing_setup_network)
        )

        closeSetupAccessPoint(
            connection = connection,
            input = input
        )

        closeSetupConnection()

        if (!isViewReady()) {
            return
        }

        setBusy(
            busy = true,
            status = getString(R.string.device_setup_waiting_phone_home_wifi)
        )

        val phoneReturnedToHomeWifi = homeWifiWaiter.waitUntilHomeWifiReady(
            expectedSsid = input.ssid,
            setupSsid = setupSsid,
            timeoutMs = 75_000L
        )

        if (!phoneReturnedToHomeWifi) {
            throw IllegalStateException(
                getString(
                    R.string.device_setup_phone_not_home_wifi,
                    input.ssid
                )
            )
        }

        if (!isViewReady()) {
            return
        }

        setBusy(
            busy = true,
            status = getString(R.string.device_setup_finding_device)
        )

        delay(7_000L)

        val discoveredDevice = waitForDeviceOnHomeNetwork()
            ?: throw IllegalStateException(
                getString(R.string.device_setup_device_not_found)
            )

        deviceStoreWriter.saveDiscoveredDevice(discoveredDevice)

        if (!isViewReady()) {
            return
        }

        setBusy(
            busy = true,
            status = getString(R.string.device_setup_success)
        )

        delay(700L)

        openDeviceMenu(discoveredDevice.id)
    }

    private suspend fun waitForDeviceClientConnection(
        connection: DeviceSetupWifiConnector.SetupConnection,
        firstResponseBody: String?
    ): Boolean {
        val firstStatus = setupClient.parseDeviceWifiStatus(firstResponseBody)

        if (firstStatus.connected) {
            return true
        }

        if (!isViewReady()) {
            return false
        }

        setBusy(
            busy = true,
            status = getString(R.string.device_setup_checking_connection)
        )

        repeat(15) {
            val status = runCatching {
                setupClient.readDeviceWifiStatus(
                    network = connection.network
                )
            }.getOrNull()

            if (status?.connected == true) {
                return true
            }

            if (isViewReady()) {
                binding.tvStatus.text = getString(
                    R.string.device_setup_joining_home_wifi
                )
            }

            delay(3_000L)
        }

        return false
    }

    private suspend fun closeSetupAccessPoint(
        connection: DeviceSetupWifiConnector.SetupConnection,
        input: HomeWifiInput
    ) {
        val closeApResult = setupClient.sendHomeWifiCredentials(
            network = connection.network,
            setupSsid = setupSsid,
            setupPassword = SETUP_AP_PASSWORD,
            homeSsid = input.ssid,
            homePassword = input.password,
            disableSetupAccessPoint = true
        )

        if (!closeApResult.success) {
            throw IllegalStateException(
                closeApResult.errorMessage
                    ?: getString(R.string.device_setup_close_ap_failed)
            )
        }
    }

    private suspend fun waitForDeviceOnHomeNetwork(): DiscoveredAquaDevice? {
        val setupShortId = setupSsid
            .substringAfterLast(
                delimiter = "-",
                missingDelimiterValue = ""
            )
            .trim()

        repeat(20) {
            val result = DeviceDiscoveryService.scan(
                context = requireContext(),
                timeoutMs = 3_000L,
                reason = DeviceScanReason.MANUAL_SCAN
            )

            val match = result.devices.firstOrNull { device ->
                isExpectedDevice(
                    device = device,
                    setupShortId = setupShortId
                )
            }

            if (match != null) {
                return match
            }

            delay(3_000L)
        }

        return null
    }

    private fun isExpectedDevice(
        device: DiscoveredAquaDevice,
        setupShortId: String
    ): Boolean {
        val typeMatches = expectedDeviceType == AquaDeviceType.UNKNOWN ||
            device.deviceType == expectedDeviceType

        if (!typeMatches) {
            return false
        }

        if (setupShortId.isBlank()) {
            return true
        }

        val normalizedShortId = setupShortId.trimStart('0')
        val deviceIdText = device.id.toString()

        return deviceIdText.endsWith(setupShortId) ||
            (
                normalizedShortId.isNotBlank() &&
                    deviceIdText.endsWith(normalizedShortId)
                )
    }

    private fun showWifiNetworksBottomSheet(
        networks: List<LegacyDeviceSetupClient.HomeWifiNetwork>
    ) {
        val dialog = BottomSheetDialog(requireContext())

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp(), 12.dp(), 22.dp(), 24.dp())
            setBackgroundColor(Color.TRANSPARENT)
        }

        val handle = View(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#4A5E75"))
            layoutParams = LinearLayout.LayoutParams(42.dp(), 4.dp()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 18.dp()
            }
        }

        val title = TextView(requireContext()).apply {
            text = getString(R.string.device_setup_sheet_title)
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
        }

        val message = TextView(requireContext()).apply {
            text = getString(R.string.device_setup_sheet_message)
            textSize = 14f
            setTextColor(Color.parseColor("#8FA4BE"))
            includeFontPadding = false
            setPadding(0, 10.dp(), 0, 18.dp())
        }

        root.addView(handle)
        root.addView(title)
        root.addView(message)

        networks.forEach { network ->
            root.addView(
                createWifiNetworkRow(
                    network = network,
                    dialog = dialog
                )
            )
        }

        dialog.setContentView(root)

        dialog.setOnShowListener {
            dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )?.setBackgroundColor(Color.TRANSPARENT)
        }

        dialog.show()
    }

    private fun createWifiNetworkRow(
        network: LegacyDeviceSetupClient.HomeWifiNetwork,
        dialog: BottomSheetDialog
    ): View {
        val card = MaterialCardView(requireContext()).apply {
            radius = 18.dp().toFloat()
            strokeWidth = 1.dp()
            strokeColor = Color.parseColor("#223A57")
            setCardBackgroundColor(Color.parseColor("#10233A"))
            cardElevation = 0f
            useCompatPadding = false
            isClickable = true
            isFocusable = true

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10.dp()
            }

            setOnClickListener {
                selectedHomeSsid = network.ssid
                binding.etHomeWifiSsid.setText(network.ssid)
                binding.inputHomeWifiSsid.error = null
                binding.tvStatus.text = getString(
                    R.string.device_setup_selected_network,
                    network.ssid
                )
                dialog.dismiss()
            }
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
        }

        val iconBox = TextView(requireContext()).apply {
            text = "Wi"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_device_add_image_box)
            includeFontPadding = false

            layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp())
        }

        val textBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = 14.dp()
            }
        }

        val ssidText = TextView(requireContext()).apply {
            text = network.ssid
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val signalText = TextView(requireContext()).apply {
            text = signalLabel(network.rssi)
            textSize = 12f
            setTextColor(Color.parseColor("#8FA4BE"))
            includeFontPadding = false
            setPadding(0, 6.dp(), 0, 0)
        }

        val chevron = TextView(requireContext()).apply {
            text = "›"
            textSize = 32f
            setTextColor(Color.parseColor("#8FA4BE"))
            gravity = Gravity.CENTER
            includeFontPadding = false
        }

        textBox.addView(ssidText)
        textBox.addView(signalText)

        row.addView(iconBox)
        row.addView(textBox)
        row.addView(chevron)

        card.addView(row)

        return card
    }

    private fun openDeviceMenu(
        deviceId: Long
    ) {
        val args = Bundle().apply {
            putLong("deviceId", deviceId)
        }

        findNavController().navigate(
            R.id.deviceMenuFragment,
            args,
            navOptions {
                popUpTo(R.id.devicesFragment) {
                    inclusive = false
                }

                launchSingleTop = true
            }
        )
    }

    private fun setBusy(
        busy: Boolean,
        status: String
    ) {
        binding.progressSetup.isVisible = busy
        binding.tvStatus.text = status
        setBusyControlsEnabled(!busy)
    }

    private fun setBusyControlsEnabled(
        enabled: Boolean
    ) {
        binding.etHomeWifiSsid.isEnabled = enabled
        binding.etHomeWifiPassword.isEnabled = enabled
        binding.btnStartSetup.isEnabled = enabled
        binding.btnBack.isEnabled = enabled
        binding.btnStartSetup.alpha = if (enabled) 1f else 0.55f
    }

    private fun closeSetupConnection() {
        setupConnection?.close()
        setupConnection = null
    }

    private fun isViewReady(): Boolean {
        return _binding != null
    }

    private fun signalLabel(
        rssi: Int
    ): String {
        return when {
            rssi >= -55 -> getString(R.string.wifi_signal_excellent)
            rssi >= -67 -> getString(R.string.wifi_signal_strong)
            rssi >= -75 -> getString(R.string.wifi_signal_good)
            else -> getString(R.string.wifi_signal_weak)
        }
    }

    private fun showError(
        message: String
    ) {
        (activity as? BaseActivity)?.showSnackBar(
            message = message,
            type = BaseActivity.SnackType.ERROR
        )
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        closeSetupConnection()
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val SETUP_AP_PASSWORD = "adminadmin"
    }
}