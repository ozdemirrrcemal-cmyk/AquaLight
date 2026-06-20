package com.aqua.aqualight.ui.tabs.devices.setup

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.setup.DeviceSetupEntryArgs
import com.aqua.aqualight.databinding.FragmentDeviceSetupBinding
import com.aqua.aqualight.ui.common.bottomsheet.HomeWifiNetworksBottomSheetFragment
import com.aqua.aqualight.ui.common.devicecard.DeviceCardIconMapper
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardBinder
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardUi
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.navigation.AppRouteNavigator
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class DeviceSetupFragment : Fragment(R.layout.fragment_device_setup) {

    private val args: DeviceSetupFragmentArgs by navArgs()

    private val viewModel: DeviceSetupViewModel by viewModels()

    private var _binding: FragmentDeviceSetupBinding? = null
    private val binding get() = _binding!!

    private var currentUiState =
        DeviceSetupUiState()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentDeviceSetupBinding.bind(view)

        setupHeader(
            state = currentUiState
        )
        setupClickListeners()
        setupHomeWifiBottomSheetResultListener()
        observeViewModel()

        viewModel.initialize(
            args = DeviceSetupEntryArgs(
                setupSsid = args.setupSsid,
                displayName = args.displayName,
                familyName = args.familyName,
                productId = args.productId,
                productKey = args.productKey,
                category = args.category,
                setupCode = args.setupCode,
                setupShortId = args.setupShortId
            )
        )
    }

    private fun setupHeader(
        state: DeviceSetupUiState
    ) {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = state.displayName,
                onBackClick = {
                    if (state.backEnabled) {
                        findNavController().popBackStack()
                    }
                }
            )
        )

        setHeaderBackEnabled(
            enabled = state.backEnabled
        )
    }

    private fun setupClickListeners() {
        binding.inputHomeWifiSsid.setEndIconOnClickListener {
            viewModel.scanHomeNetworks()
        }


        binding.btnStartSetup.setOnClickListener {
            viewModel.startSetup(
                enteredHomeSsid = binding.etHomeWifiSsid.text
                    ?.toString()
                    ?.trim()
                    .orEmpty(),
                enteredHomePassword = binding.etHomeWifiPassword.text
                    ?.toString()
                    .orEmpty()
            )
        }
    }

    private fun setupHomeWifiBottomSheetResultListener() {
        parentFragmentManager.setFragmentResultListener(
            HomeWifiNetworksBottomSheetFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            val ssid = result.getString(
                HomeWifiNetworksBottomSheetFragment.RESULT_SSID
            ).orEmpty()

            viewModel.onHomeWifiSelected(
                ssid = ssid
            )
        }
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

    private fun renderState(
        state: DeviceSetupUiState
    ) {
        if (_binding == null) {
            return
        }

        currentUiState = state

        setupHeader(
            state = state
        )

        DeviceCompactCardBinder.bind(
            binding = binding.deviceHeroCompactCard,
            item = DeviceCompactCardUi(
                deviceId = 0L,
                displayName = state.displayName,
                serialText = state.serialText,
                supportingText = getString(
                    R.string.device_setup_ready_chip
                ),
                showSupportingText = true,
                iconRes = DeviceCardIconMapper.iconFor(
                    state.expectedCategory
                ),
                isOnline = false,
                showConnectionStatus = false
            )
        )

        binding.deviceHeroCompactCard.root.isClickable = false
        binding.deviceHeroCompactCard.root.isFocusable = false

        binding.tvSetupSsid.text = state.setupSsid
        binding.tvSetupNetworkMeta.text = getString(
            R.string.device_setup_network_meta,
            state.expectedSetupCode.ifBlank { "—" },
            state.setupShortId.ifBlank { "—" }
        )

        if (
            state.homeWifiSsidText.isNotBlank() &&
            binding.etHomeWifiSsid.text?.toString() != state.homeWifiSsidText
        ) {
            binding.etHomeWifiSsid.setText(
                state.homeWifiSsidText
            )
        }

        binding.inputHomeWifiSsid.error = state.homeWifiSsidError
        binding.inputHomeWifiPassword.error = state.homeWifiPasswordError

        binding.progressSetup.isVisible = state.isBusy
        binding.tvStatus.text = state.statusText

        setSetupInputControlsEnabled(
            enabled = state.setupInputEnabled
        )

        setHeaderBackEnabled(
            enabled = state.backEnabled
        )

        renderSetupProgress(
            activeStep = state.activeStep
        )
    }

    private fun handleEvent(
        event: DeviceSetupEvent
    ) {
        if (!isAdded || _binding == null) {
            return
        }

        when (event) {
            is DeviceSetupEvent.ShowError -> {
                showError(
                    message = event.message
                )
            }

            is DeviceSetupEvent.ShowHomeWifiNetworks -> {
                showWifiNetworksBottomSheet(
                    networks = event.networks
                )
            }

            is DeviceSetupEvent.OpenDevice -> {
                openDeviceMenu(
                    deviceId = event.deviceId,
                    deviceTitle = event.deviceTitle
                )
            }
        }
    }

    private fun showWifiNetworksBottomSheet(
        networks: List<HomeWifiNetworkUi>
    ) {
        HomeWifiNetworksBottomSheetFragment.show(
            fragmentManager = parentFragmentManager,
            networks = networks.map { network ->
                HomeWifiNetworksBottomSheetFragment.HomeWifiNetworkItem(
                    ssid = network.ssid,
                    rssi = network.rssi
                )
            }
        )
    }

    private fun renderSetupProgress(
        activeStep: DeviceSetupStep
    ) {
        styleStep(
            card = binding.cardStepDevice,
            number = binding.tvStepDeviceNumber,
            label = binding.tvStepDeviceLabel,
            completed = true,
            active = false
        )

        styleStep(
            card = binding.cardStepWifi,
            number = binding.tvStepWifiNumber,
            label = binding.tvStepWifiLabel,
            completed = activeStep == DeviceSetupStep.CONNECT ||
                activeStep == DeviceSetupStep.DONE,
            active = activeStep == DeviceSetupStep.WIFI
        )

        styleStep(
            card = binding.cardStepConnect,
            number = binding.tvStepConnectNumber,
            label = binding.tvStepConnectLabel,
            completed = activeStep == DeviceSetupStep.DONE,
            active = activeStep == DeviceSetupStep.CONNECT
        )

        styleStep(
            card = binding.cardStepDone,
            number = binding.tvStepDoneNumber,
            label = binding.tvStepDoneLabel,
            completed = activeStep == DeviceSetupStep.DONE,
            active = activeStep == DeviceSetupStep.DONE
        )

        if (activeStep == DeviceSetupStep.DONE) {
            binding.statusDot.setBackgroundResource(R.drawable.bg_status_dot_green)
        } else {
            binding.statusDot.setBackgroundResource(R.drawable.bg_setup_dot_blue)
        }
    }

    private fun styleStep(
        card: MaterialCardView,
        number: TextView,
        label: TextView,
        completed: Boolean,
        active: Boolean
    ) {
        val backgroundColor: Int
        val strokeColor: Int
        val textColor: Int

        when {
            completed -> {
                backgroundColor = Color.parseColor("#123526")
                strokeColor = Color.parseColor("#31D07E")
                textColor = Color.parseColor("#31D07E")
            }

            active -> {
                backgroundColor = Color.parseColor("#143A5F")
                strokeColor = Color.parseColor("#2B95F6")
                textColor = Color.parseColor("#6CB7FF")
            }

            else -> {
                backgroundColor = Color.parseColor("#10233A")
                strokeColor = Color.parseColor("#243D5C")
                textColor = Color.parseColor("#8FA4BE")
            }
        }

        card.setCardBackgroundColor(backgroundColor)
        card.strokeColor = strokeColor
        number.setTextColor(textColor)
        label.setTextColor(textColor)
    }

    private fun openDeviceMenu(
        deviceId: Long,
        deviceTitle: String
    ) {
        AppRouteNavigator.openDevice(
            navController = findNavController(),
            deviceId = deviceId,
            deviceTitle = deviceTitle,
            clearSetupFlow = true
        )
    }

    private fun setHeaderBackEnabled(
        enabled: Boolean
    ) {
        binding.appHeader.btnBack.isEnabled = enabled
        binding.appHeader.btnBack.alpha = if (enabled) {
            1f
        } else {
            0.45f
        }
    }

    private fun setSetupInputControlsEnabled(
        enabled: Boolean
    ) {
        binding.etHomeWifiSsid.isEnabled = enabled
        binding.etHomeWifiPassword.isEnabled = enabled
        binding.btnStartSetup.isEnabled = enabled

        binding.btnStartSetup.alpha = if (enabled) {
            1f
        } else {
            0.55f
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

    override fun onDestroyView() {
        _binding = null

        super.onDestroyView()
    }
}
