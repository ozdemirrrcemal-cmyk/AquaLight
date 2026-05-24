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
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaDeviceType
import com.aqua.aqualight.data.devices.discovery.DeviceDiscoveryService
import com.aqua.aqualight.data.devices.discovery.DeviceScanReason
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice
import com.aqua.aqualight.data.devices.setup.DeviceSetupWifiConnector
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

    private lateinit var devicesStore: DevicesDataStoreManager
    private lateinit var wifiConnector: DeviceSetupWifiConnector
    private lateinit var setupClient: LegacyDeviceSetupClient

    private var setupConnection: DeviceSetupWifiConnector.SetupConnection? = null

    private var setupSsid: String = ""
    private var displayName: String = "Device"
    private var familyName: String = "Aqua device"
    private var expectedDeviceType: AquaDeviceType = AquaDeviceType.UNKNOWN

    private var selectedHomeSsid: String = ""
    private var isSettingUp: Boolean = false
    private var isScanningNetworks: Boolean = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentDeviceSetupBinding.bind(view)

        devicesStore = DevicesDataStoreManager.create(requireContext())
        wifiConnector = DeviceSetupWifiConnector(requireContext())
        setupClient = LegacyDeviceSetupClient()

        readArgs()
        renderInitialState()
        setupClickListeners()
    }

    private fun readArgs() {
        displayName = requireArguments().getString(
            "displayName",
            "Device"
        )

        familyName = requireArguments().getString(
            "familyName",
            "Aqua device"
        )

        setupSsid = requireArguments().getString(
            "setupSsid",
            ""
        )

        val deviceTypeKey = requireArguments().getString(
            "deviceType",
            ""
        )

        expectedDeviceType = AquaDeviceType.entries.firstOrNull { type ->
            type.storageKey == deviceTypeKey
        } ?: AquaDeviceType.UNKNOWN
    }

    private fun renderInitialState() {
        binding.tvTitle.text = displayName
        binding.tvSubtitle.text = familyName
        binding.tvSetupSsid.text = setupSsid

        binding.ivDeviceImage.setImageResource(
            DeviceIconMapper.iconFor(expectedDeviceType)
        )

        binding.ivDeviceImage.contentDescription = displayName

        binding.tvStatus.text = "Choose your home Wi-Fi network to continue."
        binding.etHomeWifiSsid.setText("")
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
            showError("Device setup network is missing.")
            return
        }

        isScanningNetworks = true

        setBusy(
            busy = true,
            status = "Connecting to $setupSsid to scan Wi-Fi networks..."
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val connection = getOrCreateSetupConnection()

                if (_binding == null) {
                    return@launch
                }

                setBusy(
                    busy = true,
                    status = "Scanning home Wi-Fi networks..."
                )

                val networks = setupClient.scanHomeWifiNetworks(
                    network = connection.network
                )

                if (_binding == null) {
                    return@launch
                }

                if (networks.isEmpty()) {
                    showError("No Wi-Fi networks found near the device.")
                    setBusy(
                        busy = false,
                        status = "No Wi-Fi networks found. Keep the device close to your router and try again."
                    )
                    return@launch
                }

                showWifiNetworksBottomSheet(networks)

                setBusy(
                    busy = false,
                    status = "Select your home Wi-Fi network."
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                if (_binding != null) {
                    showError(
                        exception.message ?: "Could not scan Wi-Fi networks."
                    )

                    setBusy(
                        busy = false,
                        status = "Could not scan networks. Make sure the setup network is active and try again."
                    )
                }
            } finally {
                isScanningNetworks = false

                if (_binding != null && !isSettingUp) {
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

    private fun showWifiNetworksBottomSheet(
        networks: List<LegacyDeviceSetupClient.HomeWifiNetwork>
    ) {
        val dialog = BottomSheetDialog(requireContext())

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                22.dp(),
                12.dp(),
                22.dp(),
                24.dp()
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        val handle = View(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#4A5E75"))

            layoutParams = LinearLayout.LayoutParams(
                42.dp(),
                4.dp()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 18.dp()
            }
        }

        val title = TextView(requireContext()).apply {
            text = "Select home Wi-Fi"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
        }

        val message = TextView(requireContext()).apply {
            text = "Choose the Wi-Fi network your Aqua device should connect to."
            textSize = 14f
            setTextColor(Color.parseColor("#8FA4BE"))
            includeFontPadding = false
            setPadding(
                0,
                10.dp(),
                0,
                18.dp()
            )
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
            val bottomSheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )

            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
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
                binding.tvStatus.text = "Selected ${network.ssid}. Enter the Wi-Fi password to continue."
                dialog.dismiss()
            }
        }

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                16.dp(),
                14.dp(),
                16.dp(),
                14.dp()
            )
        }

        val iconBox = TextView(requireContext()).apply {
            text = "Wi"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_device_add_image_box)
            includeFontPadding = false

            layoutParams = LinearLayout.LayoutParams(
                44.dp(),
                44.dp()
            )
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
            setPadding(
                0,
                6.dp(),
                0,
                0
            )
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

    private fun startSetup() {
        if (isSettingUp) {
            return
        }

        val homeSsid = selectedHomeSsid.ifBlank {
            binding.etHomeWifiSsid.text
                ?.toString()
                ?.trim()
                .orEmpty()
        }

        val homePassword = binding.etHomeWifiPassword.text
            ?.toString()
            ?.orEmpty()
            .orEmpty()

        if (setupSsid.isBlank()) {
            showError("Device setup network is missing.")
            return
        }

        if (homeSsid.isBlank()) {
            binding.inputHomeWifiSsid.error = "Select your home Wi-Fi network."
            return
        } else {
            binding.inputHomeWifiSsid.error = null
        }

        if (homePassword.isBlank()) {
            binding.inputHomeWifiPassword.error = "Enter your home Wi-Fi password."
            return
        } else {
            binding.inputHomeWifiPassword.error = null
        }

        isSettingUp = true

        setBusy(
            busy = true,
            status = "Preparing device setup..."
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val connection = getOrCreateSetupConnection()

                if (_binding == null) {
                    return@launch
                }

                setBusy(
                    busy = true,
                    status = "Sending home Wi-Fi settings..."
                )

                val setupResult = setupClient.sendHomeWifiCredentials(
                    network = connection.network,
                    homeSsid = homeSsid,
                    homePassword = homePassword,
                    disableSetupAccessPoint = true
                )

                closeSetupConnection()

                if (!setupResult.success) {
                    throw IllegalStateException(
                        setupResult.errorMessage
                            ?: "Device did not accept Wi-Fi settings."
                    )
                }

                if (_binding == null) {
                    return@launch
                }

                setBusy(
                    busy = true,
                    status = "Waiting for device on your home network..."
                )

                delay(4_000L)

                val discoveredDevice = waitForDeviceOnHomeNetwork()

                if (discoveredDevice == null) {
                    throw IllegalStateException(
                        "Device setup was sent, but the device was not found on your home network."
                    )
                }

                saveDiscoveredDevice(discoveredDevice)

                if (_binding == null) {
                    return@launch
                }

                setBusy(
                    busy = true,
                    status = "Device added successfully."
                )

                delay(700L)

                openDeviceMenu(discoveredDevice.id)
            } catch (exception: Exception) {
                exception.printStackTrace()

                if (_binding != null) {
                    showError(
                        exception.message ?: "Device setup failed."
                    )

                    setBusy(
                        busy = false,
                        status = "Setup failed. Check your Wi-Fi password and try again."
                    )
                }
            } finally {
                isSettingUp = false

                if (_binding != null) {
                    setBusyControlsEnabled(true)
                }
            }
        }
    }

    private suspend fun waitForDeviceOnHomeNetwork(): DiscoveredAquaDevice? {
        val setupShortId = setupSsid
            .substringAfterLast(
                delimiter = "-",
                missingDelimiterValue = ""
            )
            .trim()

        repeat(10) {
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

            delay(2_000L)
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

    private suspend fun saveDiscoveredDevice(
        device: DiscoveredAquaDevice
    ) {
        val alreadyExists = devicesStore.deviceExists(
            id = device.id
        )

        if (alreadyExists) {
            devicesStore.updateDevicesLastSeen(
                discovered = listOf(
                    device.toLastSeenUpdate()
                )
            )
            return
        }

        val definition = AquaDeviceCatalog.findByType(
            type = device.deviceType
        ) ?: error("Unsupported device")

        val savedAquaName = definition.family.displayName
        val savedName = definition.displayName

        val serial = buildSerial(
            aquaName = savedAquaName,
            name = savedName,
            id = device.id
        )

        devicesStore.addDevice(
            id = device.id,
            aquaName = savedAquaName,
            name = savedName,
            ip = device.ip,
            serial = serial,
            firmwareBuild = device.firmwareBuild,

            deviceType = device.deviceType,

            udpVersion = device.udpVersion,
            tabLight = device.tabLight,
            tabTimer = device.tabTimer,
            tabTemperature = device.tabTemperature,

            productId = device.productId.orEmpty(),
            productFamily = device.productFamily.orEmpty(),
            productModel = device.productModel.orEmpty(),
            hardwareRevision = device.hardwareRevision.orEmpty(),
            firmwareVersion = device.firmwareVersion.orEmpty(),
            apiVersion = device.apiVersion,

            channelCount = device.channelCount,
            sensorCount = device.sensorCount,

            supportedFeatures = device.supportedFeatures,
            supportedScreens = device.supportedScreens
        )

        devicesStore.updateDevicesLastSeen(
            discovered = listOf(
                device.toLastSeenUpdate()
            )
        )
    }

    private fun DiscoveredAquaDevice.toLastSeenUpdate(): DevicesDataStoreManager.DeviceLastSeenUpdate {
        return DevicesDataStoreManager.DeviceLastSeenUpdate(
            id = id,
            ip = ip,
            firmwareBuild = firmwareBuild,

            deviceType = deviceType,

            udpVersion = udpVersion,
            tabLight = tabLight,
            tabTimer = tabTimer,
            tabTemperature = tabTemperature,

            productId = productId,
            productFamily = productFamily,
            productModel = productModel,
            hardwareRevision = hardwareRevision,
            firmwareVersion = firmwareVersion,
            apiVersion = apiVersion,

            channelCount = channelCount,
            sensorCount = sensorCount,

            supportedFeatures = supportedFeatures,
            supportedScreens = supportedScreens
        )
    }

    private fun buildSerial(
        aquaName: String,
        name: String,
        id: Long
    ): String {
        val aquaInitial = aquaName.firstOrNull()
            ?.uppercaseChar()
            ?: 'X'

        val nameInitial = name.firstOrNull()
            ?.uppercaseChar()
            ?: 'X'

        return "$aquaInitial$nameInitial-$id"
    }

    private fun openDeviceMenu(
        deviceId: Long
    ) {
        val args = Bundle().apply {
            putLong("deviceId", deviceId)
        }

        findNavController().navigate(
            R.id.deviceMenuFragment,
            args
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

        binding.btnStartSetup.alpha = if (enabled) {
            1f
        } else {
            0.55f
        }
    }

    private fun closeSetupConnection() {
        setupConnection?.close()
        setupConnection = null
    }

    private fun signalLabel(
        rssi: Int
    ): String {
        return when {
            rssi >= -55 -> "Excellent signal"
            rssi >= -67 -> "Strong signal"
            rssi >= -75 -> "Good signal"
            else -> "Weak signal"
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

    private companion object {
        const val SETUP_AP_PASSWORD = "adminadmin"
    }

    override fun onDestroyView() {
        closeSetupConnection()
        _binding = null

        super.onDestroyView()
    }
}