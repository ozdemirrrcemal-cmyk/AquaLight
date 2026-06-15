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
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.add.DeviceAddSetupTarget
import com.aqua.aqualight.databinding.FragmentDeviceAddBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.AquaHeaderFilledIconAction
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.navigation.AppRouteNavigator
import kotlinx.coroutines.launch

class DeviceAddFragment : Fragment(R.layout.fragment_device_add) {

    private val viewModel: DeviceAddViewModel by viewModels()

    private var _binding: FragmentDeviceAddBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DeviceAddAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (hasRequiredPermissionsFromResult(result)) {
            viewModel.loadCandidates()
        } else {
            viewModel.onPermissionDenied()
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

        setupHeader(
            title = getString(R.string.device_add_title),
            retryEnabled = true
        )
        setupRecycler()
        observeViewModel()
        requestPermissionsAndLoad()
    }

    private fun setupHeader(
        title: String,
        retryEnabled: Boolean
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
            viewModel.onCandidateClicked(
                candidate = candidate
            )
        }

        binding.rvCandidates.layoutManager = LinearLayoutManager(
            requireContext()
        )

        binding.rvCandidates.adapter = adapter
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderState(
                            state = state
                        )
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        handleEvent(
                            event = event
                        )
                    }
                }
            }
        }
    }

    private fun requestPermissionsAndLoad() {
        if (hasRequiredPermissions()) {
            viewModel.loadCandidates()
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

    private fun renderState(
        state: DeviceAddUiState
    ) {
        if (_binding == null) {
            return
        }

        setupHeader(
            title = getString(state.headerTitleRes),
            retryEnabled = state.retryEnabled
        )

        binding.tvSubtitle.isVisible = state.subtitleRes != null
        state.subtitleRes?.let { subtitleRes ->
            binding.tvSubtitle.text = getString(
                subtitleRes
            )
        }

        when (state.contentMode) {
            DeviceAddContentMode.IDLE -> {
                binding.scanAnimation.cancelAnimation()
                binding.searchingContainer.isVisible = false
                binding.rvCandidates.isVisible = false
                binding.emptyContainer.isVisible = false
                adapter.submitCandidates(
                    candidates = emptyList()
                )
            }

            DeviceAddContentMode.LOADING -> {
                binding.searchingContainer.isVisible = true
                binding.scanAnimation.isVisible = true
                binding.scanAnimation.playAnimation()

                binding.rvCandidates.isVisible = false
                binding.emptyContainer.isVisible = false

                adapter.submitCandidates(
                    candidates = emptyList()
                )
            }

            DeviceAddContentMode.CANDIDATES -> {
                binding.scanAnimation.cancelAnimation()
                binding.searchingContainer.isVisible = false

                binding.rvCandidates.isVisible = true
                binding.emptyContainer.isVisible = false

                adapter.submitCandidates(
                    candidates = state.candidates
                )
            }

            DeviceAddContentMode.EMPTY,
            DeviceAddContentMode.PERMISSION_REQUIRED -> {
                binding.scanAnimation.cancelAnimation()
                binding.searchingContainer.isVisible = false

                binding.rvCandidates.isVisible = false
                binding.emptyContainer.isVisible = true

                adapter.submitCandidates(
                    candidates = emptyList()
                )
            }
        }
    }

    private fun handleEvent(
        event: DeviceAddEvent
    ) {
        if (!isAdded || _binding == null) {
            return
        }

        when (event) {
            is DeviceAddEvent.ShowMessage -> {
                showSnackBar(
                    message = getString(event.messageRes),
                    level = event.level
                )
            }

            is DeviceAddEvent.OpenDevice -> {
                openDeviceMenu(
                    deviceId = event.deviceId,
                    deviceTitle = event.deviceTitle
                )
            }

            is DeviceAddEvent.OpenSetupFlow -> {
                openSetupFlow(
                    setupTarget = event.setupTarget
                )
            }
        }
    }

    private fun showSnackBar(
        message: String,
        level: DeviceAddMessageLevel
    ) {
        val snackType = when (level) {
            DeviceAddMessageLevel.WARNING -> BaseActivity.SnackType.WARNING
            DeviceAddMessageLevel.ERROR -> BaseActivity.SnackType.ERROR
        }

        (activity as? BaseActivity)?.showSnackBar(
            message = message,
            type = snackType
        )
    }

    private fun openSetupFlow(
        setupTarget: DeviceAddSetupTarget
    ) {
        findNavController().navigate(
            DeviceAddFragmentDirections.actionDeviceAddFragmentToDeviceSetupFragment(
                setupSsid = setupTarget.setupSsid,
                displayName = setupTarget.displayName,
                familyName = setupTarget.familyName,
                productId = setupTarget.productId,
                productKey = setupTarget.productKey,
                category = setupTarget.category,
                setupCode = setupTarget.setupCode,
                setupShortId = setupTarget.setupShortId
            )
        )
    }

    private fun openDeviceMenu(
        deviceId: Long,
        deviceTitle: String = ""
    ) {
        AppRouteNavigator.openDevice(
            navController = findNavController(),
            deviceId = deviceId,
            deviceTitle = deviceTitle,
            clearSetupFlow = true
        )
    }

    override fun onDestroyView() {
        binding.scanAnimation.cancelAnimation()
        binding.rvCandidates.adapter = null
        _binding = null

        super.onDestroyView()
    }
}
