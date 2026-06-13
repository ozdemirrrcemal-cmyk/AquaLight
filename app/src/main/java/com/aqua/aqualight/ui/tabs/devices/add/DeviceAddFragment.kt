package com.aqua.aqualight.ui.tabs.devices.add

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.DeviceStoreWriter
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.add.DeviceAddCandidate
import com.aqua.aqualight.data.devices.add.DeviceAddCandidateLoader
import com.aqua.aqualight.data.devices.add.DeviceAddSource
import com.aqua.aqualight.databinding.FragmentDeviceAddBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderFilledIconAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import kotlinx.coroutines.launch

class DeviceAddFragment : Fragment(R.layout.fragment_device_add) {

    private var _binding: FragmentDeviceAddBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DeviceAddAdapter
    private lateinit var candidateLoader: DeviceAddCandidateLoader
    private lateinit var deviceStoreWriter: DeviceStoreWriter

    private var isLoading = false
    private var isSaving = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (hasRequiredPermissionsFromResult(result)) {
            loadCandidates()
        } else {
            showPermissionRequiredState()
        }
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

        val devicesStore = DevicesDataStoreManager.create(
            requireContext()
        )

        candidateLoader = DeviceAddCandidateLoader(
            context = requireContext(),
            devicesStore = devicesStore
        )

        deviceStoreWriter = DeviceStoreWriter(
            devicesStore = devicesStore
        )

        setupHeader()
        setupRecycler()
        requestPermissionsAndLoad()
    }

    private fun setupHeader(
        title: String = getString(R.string.device_add_title),
        retryEnabled: Boolean = true
    ) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = title,
                filledIconAction = AquaHeaderFilledIconAction(
                    iconRes = R.drawable.ic_radar,
                    contentDescription = "Search again",
                    enabled = retryEnabled,
                    onClick = {
                        requestPermissionsAndLoad()
                    }
                )
            )
        )
    }

    private fun setupRecycler() {
        adapter = DeviceAddAdapter { candidate ->
            handleCandidateClick(
                candidate = candidate
            )
        }

        binding.rvCandidates.layoutManager = LinearLayoutManager(
            requireContext()
        )

        binding.rvCandidates.adapter = adapter
    }

    private fun requestPermissionsAndLoad() {
        if (hasRequiredPermissions()) {
            loadCandidates()
            return
        }

        permissionLauncher.launch(
            requiredPermissions()
        )
    }

    private fun requiredPermissions(): Array<String> {
        return buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }.toTypedArray()
    }

    private fun hasRequiredPermissions(): Boolean {
        val locationGranted =
            isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION) ||
                isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)

        val nearbyWifiGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                isPermissionGranted(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                true
            }

        return locationGranted && nearbyWifiGranted
    }

    private fun hasRequiredPermissionsFromResult(
        result: Map<String, Boolean>
    ): Boolean {
        val locationGranted =
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION) ||
                isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)

        val nearbyWifiGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result[Manifest.permission.NEARBY_WIFI_DEVICES] == true ||
                    isPermissionGranted(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                true
            }

        return locationGranted && nearbyWifiGranted
    }

    private fun isPermissionGranted(
        permission: String
    ): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
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
            }.getOrDefault(
                emptyList()
            )

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
        binding.tvSubtitle.isVisible = true

        binding.searchingContainer.isVisible = true
        binding.scanAnimation.isVisible = true
        binding.scanAnimation.playAnimation()

        binding.rvCandidates.isVisible = false
        binding.emptyContainer.isVisible = false

        setupHeader(
            title = getString(R.string.device_add_searching_title),
            retryEnabled = false
        )

        binding.tvSubtitle.text = getString(
            R.string.device_add_searching_subtitle
        )
    }

    private fun showCandidates(
        candidates: List<DeviceAddCandidate>
    ) {
        binding.scanAnimation.cancelAnimation()
        binding.searchingContainer.isVisible = false

        binding.tvSubtitle.isVisible = false

        binding.rvCandidates.isVisible = true
        binding.emptyContainer.isVisible = false

        setupHeader(
            title = getString(R.string.device_add_title),
            retryEnabled = true
        )

        adapter.submitCandidates(
            candidates = candidates
        )
    }

    private fun showEmptyState() {
        binding.tvSubtitle.isVisible = true

        binding.scanAnimation.cancelAnimation()
        binding.searchingContainer.isVisible = false

        binding.rvCandidates.isVisible = false
        binding.emptyContainer.isVisible = true

        setupHeader(
            title = getString(R.string.device_add_title),
            retryEnabled = true
        )

        binding.tvSubtitle.text = getString(
            R.string.device_add_empty_subtitle
        )

        adapter.submitCandidates(
            candidates = emptyList()
        )
    }

    private fun showPermissionRequiredState() {
        isLoading = false

        binding.tvSubtitle.isVisible = true

        binding.scanAnimation.cancelAnimation()
        binding.searchingContainer.isVisible = false

        binding.rvCandidates.isVisible = false
        binding.emptyContainer.isVisible = true

        setupHeader(
            title = getString(R.string.device_add_title),
            retryEnabled = true
        )

        binding.tvSubtitle.text = getString(
            R.string.device_add_permission_required
        )

        adapter.submitCandidates(
            candidates = emptyList()
        )

        (activity as? BaseActivity)?.showSnackBar(
            message = getString(R.string.device_add_permission_message),
            type = BaseActivity.SnackType.WARNING
        )
    }

    private fun handleCandidateClick(
        candidate: DeviceAddCandidate
    ) {
        when (candidate.source) {
            DeviceAddSource.LOCAL_NETWORK -> {
                saveLocalNetworkDevice(
                    candidate = candidate
                )
            }

            DeviceAddSource.SETUP_AP -> {
                openSetupFlow(
                    candidate = candidate
                )
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
                val deviceId = deviceStoreWriter.saveDiscoveredDevice(
                    device = device
                )

                openDeviceMenu(
                    deviceId = deviceId
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                (activity as? BaseActivity)?.showSnackBar(
                    message = getString(R.string.device_add_save_error),
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
        findNavController().navigate(
            DeviceAddFragmentDirections.actionDeviceAddFragmentToDeviceSetupFragment(
                setupSsid = candidate.setupSsid.orEmpty(),
                displayName = candidate.displayName,
                familyName = candidate.familyName,
                productId = candidate.productId,
                productKey = candidate.productKey.storageKey,
                category = candidate.category.storageKey,
                setupCode = candidate.setupCode,
                setupShortId = candidate.setupShortId.orEmpty()
            )
        )
    }

    private fun openDeviceMenu(
        deviceId: Long
    ) {
        findNavController().navigate(
            DeviceAddFragmentDirections.actionDeviceAddFragmentToDeviceRouterFragment(
                deviceId = deviceId,
                deviceIp = ""
            )
        )
    }

    override fun onDestroyView() {
        binding.scanAnimation.cancelAnimation()
        binding.rvCandidates.adapter = null
        _binding = null

        super.onDestroyView()
    }
}