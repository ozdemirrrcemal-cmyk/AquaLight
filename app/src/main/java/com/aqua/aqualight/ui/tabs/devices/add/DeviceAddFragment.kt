package com.aqua.aqualight.ui.tabs.devices.add

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.add.DeviceAddCandidate
import com.aqua.aqualight.data.devices.add.DeviceAddCandidateLoader
import com.aqua.aqualight.data.devices.add.DeviceAddSource
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice
import com.aqua.aqualight.databinding.FragmentDeviceAddBinding
import kotlinx.coroutines.launch

class DeviceAddFragment : Fragment(R.layout.fragment_device_add) {

    private var _binding: FragmentDeviceAddBinding? = null
    private val binding get() = _binding!!

    private lateinit var devicesStore: DevicesDataStoreManager
    private lateinit var adapter: DeviceAddAdapter
    private lateinit var candidateLoader: DeviceAddCandidateLoader

    private var isLoading: Boolean = false
    private var isSaving: Boolean = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        loadCandidates()
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentDeviceAddBinding.bind(view)
        devicesStore = DevicesDataStoreManager.create(requireContext())
        candidateLoader = DeviceAddCandidateLoader(
            context = requireContext(),
            devicesStore = devicesStore
        )

        setupRecycler()
        setupClickListeners()
        requestPermissionsAndLoad()
    }

    private fun setupRecycler() {
        adapter = DeviceAddAdapter { candidate ->
            handleCandidateClick(candidate)
        }

        binding.rvCandidates.layoutManager = LinearLayoutManager(
            requireContext()
        )

        binding.rvCandidates.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnRetry.setOnClickListener {
            requestPermissionsAndLoad()
        }
    }

    private fun requestPermissionsAndLoad() {
    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }.toTypedArray()

    permissionLauncher.launch(permissions)
}

    private fun loadCandidates() {
        if (isLoading) {
            return
        }

        isLoading = true

        viewLifecycleOwner.lifecycleScope.launch {
            showLoadingState()

            val candidates = runCatching {
                candidateLoader.loadCandidates()
            }.getOrDefault(emptyList())

            if (_binding == null) {
                return@launch
            }

            isLoading = false

            if (candidates.isEmpty()) {
                showEmptyState()
            } else {
                showCandidates(
                    candidates = candidates
                )
            }
        }
    }

    private fun showLoadingState() {
    binding.searchingContainer.isVisible = true
    binding.scanAnimation.isVisible = true
    binding.scanAnimation.playAnimation()

    binding.rvCandidates.isVisible = false
    binding.emptyContainer.isVisible = false

    binding.btnRetry.isEnabled = false
    binding.btnRetry.alpha = 0.45f

    binding.tvTitle.text = "Searching..."
    binding.tvSubtitle.text = "Looking for nearby Aqua devices."
}

private fun showCandidates(
    candidates: List<DeviceAddCandidate>
) {
    binding.scanAnimation.cancelAnimation()
    binding.searchingContainer.isVisible = false

    binding.rvCandidates.isVisible = true
    binding.emptyContainer.isVisible = false

    binding.btnRetry.isEnabled = true
    binding.btnRetry.alpha = 1f

    binding.tvTitle.text = "Add Device"
    binding.tvSubtitle.text = "Select your device to continue."

    adapter.submitList(candidates)
}

private fun showEmptyState() {
    binding.scanAnimation.cancelAnimation()
    binding.searchingContainer.isVisible = false

    binding.rvCandidates.isVisible = false
    binding.emptyContainer.isVisible = true

    binding.btnRetry.isEnabled = true
    binding.btnRetry.alpha = 1f

    binding.tvTitle.text = "Add Device"
    binding.tvSubtitle.text = "No Aqua devices found nearby."

    adapter.submitList(emptyList())
}

    private fun handleCandidateClick(
        candidate: DeviceAddCandidate
    ) {
        when (candidate.source) {
            DeviceAddSource.LOCAL_NETWORK -> {
                saveLocalNetworkDevice(candidate)
            }

            DeviceAddSource.SETUP_AP -> {
                openSetupFlow(candidate)
            }
        }
    }

    private fun saveLocalNetworkDevice(
        candidate: DeviceAddCandidate
    ) {
        val device = candidate.localDevice ?: return

        if (isSaving) {
            return
        }

        isSaving = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                saveDiscoveredDevice(device)

                val args = Bundle().apply {
                    putLong("deviceId", device.id)
                }

                findNavController().navigate(
                    R.id.action_deviceAddFragment_to_deviceMenuFragment,
                    args
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                (activity as? BaseActivity)?.showSnackBar(
                    message = "Device could not be added.",
                    type = BaseActivity.SnackType.ERROR
                )
            } finally {
                isSaving = false
            }
        }
    }

    private fun openSetupFlow(
        candidate: DeviceAddCandidate
    ) {
        val args = Bundle().apply {
            putString("setupSsid", candidate.setupSsid.orEmpty())
            putString("displayName", candidate.displayName)
            putString("familyName", candidate.familyName)
            putString("deviceType", candidate.deviceType.storageKey)
        }

        findNavController().navigate(
            R.id.action_deviceAddFragment_to_deviceSetupFragment,
            args
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

        val definition = com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog.findByType(
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

    override fun onDestroyView() {
    binding.scanAnimation.cancelAnimation()
    binding.rvCandidates.adapter = null
    _binding = null

    super.onDestroyView()
}
}